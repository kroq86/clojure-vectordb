(ns knowledge-base
  (:require [vectordb.model :as vdb]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.set :as set]))

(def RELEVANCE-THRESHOLD 0.08)  ; Lowered threshold for more results

(defn read-file [path]
  (with-open [reader (io/reader path)]
    (str/join "\n" (line-seq reader))))

(defn split-into-sections [content]
  (->> (str/split content #"\n## ")
       (map str/trim)
       (filter #(not (str/blank? %)))))

(defn create-section-id [title]
  (-> title
      str/lower-case
      (str/replace #"[^a-z0-9]+" "-")))

(defn process-section [section]
  (let [lines (str/split-lines section)
        title (first lines)
        content (str/join "\n" (rest lines))]
    {:id (create-section-id (or title "untitled"))
     :title (or title "Untitled Section")
     :content (str/trim content)}))

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

(defn deduplicate-results [results]
  (let [seen (atom #{})]
    (filter (fn [[text _]]
              (let [id (create-section-id (first (str/split-lines text)))]
                (when-not (@seen id)
                  (swap! seen conj id)
                  true)))
            results)))

(defn format-section [text]
  (let [[title & content] (str/split-lines text)
        formatted-content (->> content
                             (map #(if (str/starts-with? % "```")
                                   (str "\n" % "\n")  ; Add spacing around code blocks
                                   %))
                             (str/join "\n"))]
    (str "\n" title "\n" "=" (count title) "\n"  ; Underline title
         formatted-content)))

(defn example-with-knowledge-base []
  (def db (vdb/new-vector-database "knowledge_base.db"))
  
  ;; Read and process documentation files
  (let [fasm-handbook (read-file "FASM_handbook.md")
        ai-fasm (read-file "AI_FASM.md")
        all-content (str fasm-handbook "\n" ai-fasm)
        sections (split-into-sections all-content)
        processed-sections (map process-section sections)
        section-texts (map #(str (:title %) "\n" (:content %)) processed-sections)]
    
    (println "Adding sections to knowledge base...")
    (let [start-time (System/currentTimeMillis)
          tokenized-texts (map tokenize section-texts)
          all-terms (get-all-terms tokenized-texts)
          doc-freq (document-frequency tokenized-texts)
          n-docs (count section-texts)
          tfidf-vectors (tf-idf tokenized-texts doc-freq n-docs all-terms)
          _ (doseq [[text vector] (map vector section-texts tfidf-vectors)]
              (let [normalized-vec (normalize-vector (mapv #(get vector % 0.0) all-terms))]
                (vdb/insert db text normalized-vec)))
          end-time (System/currentTimeMillis)]
      (println "Total insert time:" (- end-time start-time) "ms"))
    
    ;; Example queries
    (let [queries ["How to handle array operations in FASM?"]]
      (doseq [query queries]
        (println "\n" "=" 50)
        (println "Query:" query)
        (let [start-time (System/currentTimeMillis)
              tokenized-texts (map tokenize section-texts)
              all-terms (get-all-terms tokenized-texts)
              doc-freq (document-frequency tokenized-texts)
              n-docs (count section-texts)
              query-vector (first (tf-idf [(tokenize query)] doc-freq n-docs all-terms))
              normalized-query (normalize-vector (mapv #(get query-vector % 0.0) all-terms))
              results (vdb/search db normalized-query 5 "hnsw")  ; Increased to 5 results
              filtered-results (filter-by-threshold (:results results) RELEVANCE-THRESHOLD)
              deduped-results (deduplicate-results filtered-results)
              end-time (System/currentTimeMillis)]
          (println "Search time:" (- end-time start-time) "ms")
          (println "\nRelevant sections:")
          (doseq [[text similarity] deduped-results]
            (println "\n" "-" 50)
            (println "Similarity:" (format "%.3f" similarity))
            (println (format-section text))))))))

(defn -main [& args]
  (example-with-knowledge-base))

(comment
  (example-with-knowledge-base)) 