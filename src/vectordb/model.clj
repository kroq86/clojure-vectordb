(ns vectordb.model
  (:require [clojure.math.numeric-tower :as math]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util.concurrent ConcurrentHashMap]
           [java.util LinkedHashMap Collections]
           [java.nio ByteBuffer]
           [java.util.concurrent.atomic AtomicInteger]
           [java.util Base64]))

;; Performance metrics record
(defrecord PerformanceMetrics [cache-hits cache-misses memory-usage total-vectors total-dimensions cache-size])

;; Search metrics record
(defrecord SearchMetrics [exact-time approx-time lsh-time recall-at-k precision-at-k memory-used cache-hit-ratio])

;; Create a new performance metrics instance
(defn new-performance-metrics []
  (->PerformanceMetrics 
    (AtomicInteger. 0)
    (AtomicInteger. 0)
    0.0
    0
    0
    0))

;; LSH Index 
(defrecord LSHIndex [num-hash-functions num-bands hash-ranges dimensions bucket-dict vector-count])

(defn new-lsh-index 
  ([] (new-lsh-index 20 10))
  ([num-hash-functions num-bands]
   (->LSHIndex 
     num-hash-functions
     num-bands
     nil
     nil
     (ConcurrentHashMap.)
     (AtomicInteger. 0))))

(defn- initialize-hash-ranges [lsh-index dimensions]
  (let [hash-functions (get lsh-index :num-hash-functions)
        random-values (vec (repeatedly (* hash-functions dimensions) #(- (Math/random) 0.5)))
        hash-ranges (->> random-values
                      (partition dimensions)
                      (mapv vec))]
    ;; Normalize rows
    (assoc lsh-index 
           :hash-ranges (mapv (fn [row]
                                (let [norm (Math/sqrt (reduce + (map #(* % %) row)))]
                                  (mapv #(/ % norm) row)))
                              hash-ranges)
           :dimensions dimensions)))

(defn- hash-vector [{:keys [hash-ranges dimensions] :as lsh-index} vector]
  (let [lsh-index (if (or (nil? hash-ranges) (not= dimensions (count vector)))
                    (initialize-hash-ranges lsh-index (count vector))
                    lsh-index)
        hash-ranges (:hash-ranges lsh-index)
        ;; Normalize vector
        norm (Math/sqrt (reduce + (map #(* % %) vector)))
        normalized-vector (mapv #(/ % norm) vector)
        ;; Calculate projections
        projections (mapv (fn [hash-row]
                           (reduce + (map * hash-row normalized-vector)))
                         hash-ranges)]
    [(mapv #(if (pos? %) 1 0) projections) lsh-index]))

(defn lsh-insert [{:keys [num-bands bucket-dict] :as lsh-index} key vector]
  (let [[hash-signature updated-lsh-index] (hash-vector lsh-index vector)
        functions-per-band (int (/ (:num-hash-functions updated-lsh-index) num-bands))]
    
    ;; Update vector count
    (.incrementAndGet ^AtomicInteger (:vector-count updated-lsh-index))
    
    ;; For each band, create a signature and add to bucket
    (doseq [band (range num-bands)]
      (let [start-idx (* band functions-per-band)
            end-idx (+ start-idx functions-per-band)
            band-signature (vec (subvec hash-signature start-idx end-idx))
            band-hash (hash [band band-signature])]
        
        ;; Add to bucket dictionary
        (if-let [existing-bucket (.get bucket-dict band-hash)]
          (.add existing-bucket key)
          (let [new-bucket (Collections/synchronizedList (java.util.ArrayList.))]
            (.add new-bucket key)
            (.put bucket-dict band-hash new-bucket)))))
    updated-lsh-index))

(defn lsh-query [{:keys [num-bands bucket-dict] :as lsh-index} vector min-candidates]
  (let [[hash-signature updated-lsh-index] (hash-vector lsh-index vector)
        functions-per-band (int (/ (:num-hash-functions updated-lsh-index) num-bands))
        candidate-keys (java.util.HashSet.)]
    
    ;; For each band, find matching bucket and add to candidates
    (doseq [band (range num-bands)]
      (let [start-idx (* band functions-per-band)
            end-idx (+ start-idx functions-per-band)
            band-signature (vec (subvec hash-signature start-idx end-idx))
            band-hash (hash [band band-signature])]
        
        (when-let [bucket (.get bucket-dict band-hash)]
          (doseq [key bucket]
            (.add candidate-keys key)))))
    
    ;; If not enough candidates, return all keys
    (if (< (.size candidate-keys) min-candidates)
      (let [all-keys (java.util.HashSet.)]
        (doseq [bucket (enumeration-seq (.elements bucket-dict))
                key bucket]
          (.add all-keys key))
        (vec all-keys))
      (vec candidate-keys))))

;; Vector database
(defrecord VectorDatabase [conn chunk-size max-cache-size similarity-metric
                          cache cache-lock current-cache-size 
                          metrics lsh-index vector-dimension])

(defn serialize-vector [vector]
  (let [vector-str (str/join "," (map str vector))]
    vector-str))

(defn deserialize-vector [vector-str dimensions]
  (let [values (str/split vector-str #",")
        parsed (mapv #(Double/parseDouble %) values)]
    parsed))

(defn- initialize-db [db]
  (let [conn (:conn db)]
    (jdbc/execute! conn ["DROP TABLE IF EXISTS vectors"])
    (jdbc/execute! conn ["CREATE TABLE vectors (
                          key VARCHAR PRIMARY KEY,
                          vector TEXT,
                          dimensions INTEGER,
                          partition_id INTEGER,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )"])
    (jdbc/execute! conn ["CREATE INDEX IF NOT EXISTS idx_vectors_key ON vectors(key)"])
    (jdbc/execute! conn ["CREATE INDEX IF NOT EXISTS idx_vectors_partition ON vectors(partition_id)"])
    db))

(defn new-vector-database 
  ([] (new-vector-database ":memory:"))
  ([db-path] (new-vector-database db-path 1000 (* 1024 1024 1024) "cosine"))
  ([db-path chunk-size max-cache-size similarity-metric]
   (let [conn (jdbc/get-connection {:dbtype "duckdb" :dbname db-path})
         cache (LinkedHashMap. 16 0.75 true) ; access-order=true for LRU behavior
         cache-lock (Object.)
         metrics (new-performance-metrics)
         lsh-index (new-lsh-index)
         db (->VectorDatabase conn chunk-size max-cache-size similarity-metric
                           cache cache-lock 0 metrics lsh-index nil)]
     (initialize-db db))))

(defn- get-cached-vector [db key]
  (locking (:cache-lock db)
    (let [vector (.get ^LinkedHashMap (:cache db) key)]
      (if vector
        (do 
          (.incrementAndGet ^AtomicInteger (-> db :metrics :cache-hits))
          vector)
        (do
          (.incrementAndGet ^AtomicInteger (-> db :metrics :cache-misses))
          nil)))))

(defn- monitor-cache-size [vector]
  (+ (* (count vector) 8) 64)) ; 8 bytes per double + overhead

(defn- evict-cache [db]
  (locking (:cache-lock db)
    (let [target-size (* (:max-cache-size db) 0.8)
          cache ^LinkedHashMap (:cache db)]
      (loop [current-db db]
        (if (and (> (:current-cache-size current-db) target-size)
                (pos? (.size cache)))
          (let [iter (.iterator (.entrySet cache))]
            (if (and (.hasNext iter) 
                    ;; Skip first entry (most recently used)
                    (do (.next iter) (.hasNext iter)))
              (let [entry (.next iter)]
                (.remove iter)
                (let [vector (.getValue entry)
                      vector-size (monitor-cache-size vector)
                      updated-db (assoc current-db :current-cache-size 
                                       (- (:current-cache-size current-db) vector-size))]
                  (recur updated-db)))
              current-db))
          current-db)))))

(defn- set-cached-vector [db key vector]
  (locking (:cache-lock db)
    (let [cache ^LinkedHashMap (:cache db)
          vector-size (monitor-cache-size vector)
          
          ;; Remove old vector if exists
          db-after-remove 
          (if-let [old-vector (.get cache key)]
            (let [old-size (monitor-cache-size old-vector)]
              (assoc db :current-cache-size (- (:current-cache-size db) old-size)))
            db)]
      
      ;; Add new vector
      (.put cache key vector)
      (let [db-after-add (assoc db-after-remove :current-cache-size 
                               (+ (:current-cache-size db-after-remove) vector-size))]
      
        ;; Evict if necessary
        (if (> (:current-cache-size db-after-add) (:max-cache-size db-after-add))
          (evict-cache db-after-add)
          db-after-add)))))

(defn get-metrics [db]
  (let [runtime (Runtime/getRuntime)
        memory-usage (/ (- (.totalMemory runtime) (.freeMemory runtime)) (* 1024.0 1024.0))]
    (assoc (:metrics db)
           :memory-usage memory-usage
           :cache-size (:current-cache-size db)
           :total-vectors (-> (jdbc/execute-one! (:conn db) ["SELECT COUNT(*) as count FROM vectors"])
                              :count))))

(defn- calculate-similarity [db v1 v2]
  (let [metric (:similarity-metric db)]
    (cond
      (= metric "cosine")
      (let [dot-product (reduce + (map * v1 v2))
            norm-v1 (Math/sqrt (reduce + (map #(* % %) v1)))
            norm-v2 (Math/sqrt (reduce + (map #(* % %) v2)))]
        (if (or (zero? norm-v1) (zero? norm-v2))
          0.0
          (/ dot-product (* norm-v1 norm-v2))))
      
      (= metric "euclidean")
      (let [diff (map - v1 v2)]
        (- (Math/sqrt (reduce + (map #(* % %) diff)))))
      
      :else ; dot-product
      (reduce + (map * v1 v2)))))

(defn insert [db key vector & {:keys [partition-id]}]
  (let [partition-id (or partition-id (mod (hash key) (:chunk-size db)))
        vector-str (serialize-vector vector)
        dimensions (count vector)]
    
    (jdbc/execute! (:conn db)
      ["INSERT OR REPLACE INTO vectors (key, vector, dimensions, partition_id) VALUES (?, ?, ?, ?)"
       key vector-str dimensions partition-id])
    
    (set-cached-vector db key vector)
    (let [updated-lsh-index (lsh-insert (:lsh-index db) key vector)]
      (assoc db :lsh-index updated-lsh-index))))

(defn retrieve [db key]
  (if-let [cached (get-cached-vector db key)]
    cached
    (when-let [result (jdbc/execute-one! (:conn db)
                         ["SELECT vector, dimensions FROM vectors WHERE key = ?" key])]
      (let [vector-str (:vector result)
            dimensions (:dimensions result)
            vector (deserialize-vector vector-str dimensions)]
        (set-cached-vector db key vector)
        vector))))

(defn- process-partition [db partition-vectors query-vector]
  (for [[key vector-str dimensions] partition-vectors]
    (let [vector (or (get-cached-vector db key)
                    (let [v (deserialize-vector vector-str dimensions)]
                      (set-cached-vector db key v)
                      v))
          similarity (calculate-similarity db query-vector vector)]
      [key similarity])))

(defn exact-search [db query-vector k]
  (let [conn (:conn db)
        start-time (System/nanoTime)
        all-vectors (jdbc/execute! conn
                      ["SELECT key, vector, dimensions FROM vectors"])
        
        ;; Process all vectors and calculate similarity
        similarities (process-partition db 
                      (map (juxt :key :vector :dimensions) all-vectors)
                      query-vector)
        
        ;; Sort by similarity (descending) and take top k
        top-results (take k (sort-by second > similarities))
        
        end-time (System/nanoTime)
        elapsed-time (/ (- end-time start-time) 1e9)]
    
    {:results top-results
     :elapsed-time elapsed-time}))

(defn lsh-search [db query-vector k]
  (let [start-time (System/nanoTime)
        candidate-keys (lsh-query (:lsh-index db) query-vector 100)
        
        ;; Fetch candidates
        candidates (filter some?
                    (for [key candidate-keys]
                      (when-let [vector (retrieve db key)]
                        [key vector])))
        
        ;; Calculate similarities
        similarities (for [[key vector] candidates]
                      [key (calculate-similarity db query-vector vector)])
        
        ;; Sort and take top k
        top-results (take k (sort-by second > similarities))
        
        end-time (System/nanoTime)
        elapsed-time (/ (- end-time start-time) 1e9)]
    
    {:results top-results
     :elapsed-time elapsed-time}))

(defn approximate-search [db query-vector k sample-size]
  (let [conn (:conn db)
        chunk-size (:chunk-size db)
        
        ;; Get total count
        total-count (-> (jdbc/execute-one! conn ["SELECT COUNT(*) as count FROM vectors"])
                        :count)
        
        ;; Adjust sample size if needed
        effective-sample-size (min sample-size total-count)
        
        ;; Random sampling
        sample-vectors (jdbc/execute! conn
                        ["SELECT key, vector, dimensions FROM vectors 
                          ORDER BY RANDOM() LIMIT ?" effective-sample-size])
        
        ;; Process sampled vectors
        similarities (process-partition db 
                      (map (juxt :key :vector :dimensions) sample-vectors)
                      query-vector)
        
        ;; Sort by similarity (descending) and take top k
        top-results (take k (sort-by second > similarities))]
    
    {:results top-results}))

(defn search [db query-vector k & {:keys [method] :or {method "exact"}}]
  (cond
    (= method "lsh") (lsh-search db query-vector k)
    (= method "approximate") (approximate-search db query-vector k (/ (:chunk-size db) 10))
    :else (exact-search db query-vector k)))

(defn delete [db key]
  (let [conn (:conn db)
        result (jdbc/execute-one! conn
                 ["DELETE FROM vectors WHERE key = ? RETURNING 1 as deleted" key])]
    
    ;; Remove from cache if present
    (locking (:cache-lock db)
      (.remove ^LinkedHashMap (:cache db) key))
    
    ;; Check if deletion was successful
    (boolean (:deleted result))))

(defn get-all-keys [db]
  (let [conn (:conn db)
        results (jdbc/execute! conn ["SELECT key FROM vectors"])]
    (mapv :key results)))

(defn clear-cache [db]
  (locking (:cache-lock db)
    (.clear ^LinkedHashMap (:cache db))
    (assoc db :current-cache-size 0)))

(defn close [db]
  (.close (:conn db))) 