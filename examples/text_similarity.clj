(ns text-similarity
  (:require [vectordb.model :as vdb]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.math.numeric-tower :as math]))

(def RELEVANCE-THRESHOLD 0.1)  ; Lowered threshold further

(def texts ["Clojure is a functional programming language"
           "Python is great for data science"
           "JavaScript is widely used for web development"
           "Rust is a systems programming language"])

(defn tokenize [text]
  (-> text
      str/lower-case
      (str/split #"\s+")
      (->> (filter #(> (count %) 2)))))

(defn term-frequency [tokens]
  (let [freqs (reduce (fn [acc token]
                        (update acc token (fnil inc 0)))
                      {}
                      tokens)
        total (reduce + (vals freqs))]
    (reduce (fn [acc [term freq]]
              (assoc acc term (/ freq total)))
            {}
            freqs)))

(defn document-frequency [documents]
  (let [token-sets (map #(set (keys (term-frequency %))) documents)]
    (reduce (fn [acc token-set]
              (reduce (fn [acc token]
                       (update acc token (fnil inc 0)))
                     acc
                     token-set))
            {}
            token-sets)))

(defn get-all-terms [documents]
  (->> documents
       (mapcat #(keys (term-frequency %)))
       set
       vec
       sort))

(defn tf-idf [documents doc-freq n-docs all-terms]
  (map (fn [tokens]
         (let [tf (term-frequency tokens)]
           (reduce (fn [acc term]
                    (assoc acc term
                           (* (get tf term 0.0)
                              (Math/log (/ n-docs
                                         (get doc-freq term 1))))))
                  {}
                  all-terms)))
       documents))

(defn normalize-vector [vec]
  (let [magnitude (Math/sqrt (reduce + (map #(* % %) vec)))]
    (if (zero? magnitude)
      vec
      (map #(/ % magnitude) vec))))

(defn filter-by-threshold [scores threshold]
  (filter #(>= (second %) threshold) scores))

(defn example-with-tfidf []
  (def db (vdb/new-vector-database "vectors_tfidf.db"))
  
  (println "Adding texts using TF-IDF...")
  (let [start-time (System/currentTimeMillis)
        tokenized-texts (map tokenize texts)
        all-terms (get-all-terms tokenized-texts)
        doc-freq (document-frequency tokenized-texts)
        n-docs (count texts)
        tfidf-vectors (tf-idf tokenized-texts doc-freq n-docs all-terms)
        _ (doseq [[text vector] (map vector texts tfidf-vectors)]
            (let [normalized-vec (normalize-vector (mapv #(get vector % 0.0) all-terms))]
              (vdb/insert db text normalized-vec)))
        end-time (System/currentTimeMillis)]
    (println "Total insert time:" (- end-time start-time) "ms"))
  
  (let [query "What programming language is good for data analysis?"
        start-time (System/currentTimeMillis)
        tokenized-texts (map tokenize texts)
        all-terms (get-all-terms tokenized-texts)
        doc-freq (document-frequency tokenized-texts)
        n-docs (count texts)
        query-vector (first (tf-idf [(tokenize query)] doc-freq n-docs all-terms))
        normalized-query (normalize-vector (mapv #(get query-vector % 0.0) all-terms))
        results (vdb/search db normalized-query 2 "hnsw")
        filtered-results (filter-by-threshold (:results results) RELEVANCE-THRESHOLD)
        end-time (System/currentTimeMillis)]
    (println "\nQuery:" query)
    (println "Search time:" (- end-time start-time) "ms")
    (println "All results before filtering:")
    (doseq [[key similarity] (:results results)]
      (println "-" key "with similarity:" similarity))
    (println "\nFiltered results (threshold:" RELEVANCE-THRESHOLD "):")
    (doseq [[key similarity] filtered-results]
      (println "-" key "with similarity:" similarity))))

(defn -main [& args]
  (example-with-tfidf))

(comment
  (example-with-tfidf)) 