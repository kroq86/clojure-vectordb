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

(defn- get-token-weight [token]
  (cond
    (re-find #"python|java|clojure|scala|kotlin" token) 2.0
    (re-find #"database|vector|index|query" token) 1.8
    (re-find #"machine|learning|ai|artificial|intelligence" token) 1.6
    (re-find #"cloud|data|big|analytics" token) 1.4
    :else 1.0))

(defn- hash-to-float [hash]
  (let [max-hash (double Integer/MAX_VALUE)]
    (/ (double hash) max-hash)))

(defn- generate-token-vector [token dim]
  (let [weight (get-token-weight token)
        hash-val (hash-to-float (.hashCode token))
        ;; Create semantic components
        semantic-category (cond
                          (re-find #"python|java|clojure|scala|kotlin" token) 0
                          (re-find #"database|vector|index|query" token) 1
                          (re-find #"machine|learning|ai|artificial|intelligence" token) 2
                          (re-find #"cloud|data|big|analytics" token) 3
                          :else 4)
        ;; Create vector with semantic components
        semantic-indices (take 20 (shuffle (range dim)))]
    (vec (for [i (range dim)]
           (let [;; Base component with semantic separation
                 base-val (if (contains? (set semantic-indices) i)
                           (* weight (if (= (mod i 5) semantic-category) 2.0 1.0))
                           0.0)
                 ;; Add moderate noise
                 noise (* 0.5 (Math/random))]
             (+ base-val noise))))))

(defn simple-embedding
  "Creates a simple embedding vector from text.
   Uses word tokens to create a more meaningful embedding."
  ([text]
   (simple-embedding text 100))
  
  ([text dim]
   (let [tokens (tokenize text)
         ;; Create initial vector with zeros
         base-vec (vec (repeat dim 0.0))
         
         ;; Process each token
         token-vecs (mapv #(generate-token-vector % dim) tokens)
         
         ;; Combine token vectors with weighted sum
         combined-vec (if (empty? token-vecs)
                       base-vec
                       (let [weights (mapv get-token-weight tokens)
                             total-weight (reduce + weights)]
                         (reduce (fn [acc [vec weight]]
                                  (mapv #(+ %1 (* %2 (/ weight total-weight))) acc vec))
                                base-vec
                                (map vector token-vecs weights))))
         
         ;; Normalize the final vector
         normalized-vec (normalize-vector combined-vec)]
     
     normalized-vec)))