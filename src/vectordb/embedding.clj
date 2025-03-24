(ns vectordb.embedding
  (:require [clojure.string :as str]))

(defn- normalize-vector [vec]
  (let [norm (Math/sqrt (reduce + (map #(* % %) vec)))]
    (if (zero? norm)
      vec
      (mapv #(/ % norm) vec))))

(defn simple-embedding
  "Creates a simple embedding vector from text.
   Uses random values seeded by text hash, with character frequencies influencing values."
  ([text] (simple-embedding text 10))
  ([text dim]
   (let [seed (mod (.hashCode text) Integer/MAX_VALUE)
         r (java.util.Random. seed)
         
         ;; Initialize with random normal values
         base-vec (vec (repeatedly dim #(- (.nextGaussian r) 0.5)))
         
         ;; Count character frequencies
         char-counts (frequencies (str/lower-case text))
         
         ;; Influence vector with character counts for first 10 letters
         influenced-vec 
         (reduce (fn [v [idx letter]]
                   (if (< idx dim)
                     (let [count (get char-counts letter 0)]
                       (update v idx #(+ % (* count 0.1))))
                     v))
                 base-vec
                 (map-indexed vector "abcdefghij"))]
     
     ;; Normalize the vector
     (normalize-vector influenced-vec)))) 