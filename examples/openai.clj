(ns openai
  (:require [vectordb.model :as vdb]
            [clojure.string :as str]
            [clj-http.client :as client]
            [cheshire.core :as json]))


;; export OPENAI_API_KEY="your-api-key-here"
(def openai-api-key (System/getenv "OPENAI_API_KEY"))
(def openai-api-url "https://api.openai.com/v1/embeddings")


;; Debug print
(println "API Key:" (if openai-api-key "Present" "Missing"))


(defn get-openai-embedding [text]
  (let [start-time (System/currentTimeMillis)
        response (client/post openai-api-url
                             {:headers {"Authorization" (str "Bearer " openai-api-key)
                                      "Content-Type" "application/json"}
                              :body (json/generate-string
                                    {:model "text-embedding-ada-002"
                                     :input text})})
        body (json/parse-string (:body response) true)
        end-time (System/currentTimeMillis)]
    (println "API call took:" (- end-time start-time) "ms")
    (get-in body [:data 0 :embedding])))


(defn get-openai-embeddings-batch [texts]
  (let [start-time (System/currentTimeMillis)
        response (client/post openai-api-url
                             {:headers {"Authorization" (str "Bearer " openai-api-key)
                                      "Content-Type" "application/json"}
                              :body (json/generate-string
                                    {:model "text-embedding-ada-002"
                                     :input texts})})
        body (json/parse-string (:body response) true)
        end-time (System/currentTimeMillis)]
    (println "Batch API call took:" (- end-time start-time) "ms")
    (map #(get-in % [:embedding]) (:data body))))


(defn example-with-openai []
  (def db (vdb/new-vector-database "vectors.db"))
  
  (let [texts ["Clojure is a functional programming language"
               "Python is great for data science"
               "JavaScript is widely used for web development"
               "Rust is a systems programming language"]]
    (println "Adding texts in batch...")
    (let [start-time (System/currentTimeMillis)
          embeddings (get-openai-embeddings-batch texts)
          _ (doseq [[text embedding] (map vector texts embeddings)]
              (vdb/insert db text embedding))
          end-time (System/currentTimeMillis)]
      (println "Total batch insert time:" (- end-time start-time) "ms")))
  
  (let [query "What programming language is good for data analysis?"
        start-time (System/currentTimeMillis)
        query-embedding (get-openai-embedding query)
        results (vdb/search db query-embedding 2 "hnsw")
        end-time (System/currentTimeMillis)]
    (println "\nQuery:" query)
    (println "Search time:" (- end-time start-time) "ms")
    (println "Similar texts:")
    (doseq [[key similarity] (:results results)]
      (println "-" key "with similarity:" similarity))))


(defn -main [& args]
  (example-with-openai))


(comment
  (example-with-openai)) 