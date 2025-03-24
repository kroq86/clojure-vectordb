(ns vectordb.embedding
  (:require [clojure.string :as str]))

(defn- normalize-vector [vec]
  (let [norm (Math/sqrt (reduce + (map #(* % %) vec)))]
    (if (zero? norm)
      vec
      (mapv #(/ % norm) vec))))

(defn- tokenize [text]
  (-> text
      str/lower-case
      (str/replace #"[^a-z0-9\s]" " ")
      (str/split #"\s+")
      (->> (filter #(not (empty? %))))))

(defn simple-embedding
  "Creates a simple embedding vector from text.
   Uses word tokens to create a more meaningful embedding."
  ([text]
   (simple-embedding text 10))
  
  ([text dim]
   (let [seed (mod (.hashCode text) Integer/MAX_VALUE)
         r (java.util.Random. seed)
         tokens (tokenize text)
         token-hashes (mapv #(.hashCode %) tokens)
         
         ;; Create base vector with small random values
         base-vec (vec (repeatedly dim #(* 0.01 (- (.nextGaussian r) 0.5))))
         
         ;; Influence vector based on token hashes
         influenced-vec 
         (if (empty? tokens)
           base-vec
           (reduce (fn [v token-hash]
                     (let [pos (mod (Math/abs token-hash) dim)
                           influence (* 0.1 (/ token-hash (max 1 (count tokens))))]
                       (update v pos #(+ % influence))))
                   base-vec
                   token-hashes))
         
         ;; Keywords to specifically boost
         keywords ["python" "java" "clojure" "database" "vector" "machine" "learning" "ai" 
                   "artificial" "intelligence" "cloud" "data"]
         
         ;; Map keywords to vector positions
         keyword-indices (map-indexed (fn [idx kw] [kw (mod idx dim)]) keywords)
         keyword-map (into {} keyword-indices)
         
         ;; Boost dimensions for matching keywords
         with-keywords 
         (reduce (fn [v token]
                   (if-let [idx (get keyword-map token)]
                     (update v idx #(+ % 0.5))
                     v))
                 influenced-vec
                 tokens)
         
         ;; Final normalized vector
         final-vec (normalize-vector with-keywords)]
     
     final-vec)))