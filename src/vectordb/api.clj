(ns vectordb.api
  (:require [compojure.core :refer [defroutes GET POST DELETE context]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.util.response :refer [response status content-type]]
            [clojure.data.json :as json]
            [vectordb.model :as model]
            [vectordb.embedding :as embedding]))

;; In-memory store for document content
(def document-store (atom {"doc1" "Python is a high-level programming language known for its readability and versatility."
                           "doc2" "Machine learning models require training data to learn patterns and make predictions."
                           "doc3" "Database indexing improves query performance by creating data structures that speed up data retrieval."
                           "doc4" "Artificial intelligence systems aim to simulate human intelligence in machines."
                           "doc5" "Cloud computing provides on-demand delivery of computing resources over the internet."}))

;; Initialize sample data
(defn init-sample-data [db]
  (doseq [[doc-id doc-text] @document-store]
    (let [vector (embedding/simple-embedding doc-text)]
      (model/insert db doc-id vector)
      (println (str "Inserted " doc-id " with vector length " (count vector))))))

;; API routes
(defn read-root []
  (response {:message "MCP Vector Database API" :version "1.0.0"}))

(defn get-all-vectors [db]
  (let [keys (model/get-all-keys db)
        result (into {} 
                 (for [key keys]
                   [key (get @document-store key "Vector without associated document")]))]
    (response result)))

(defn search-text [db query k method]
  (try
    (let [query-vector (embedding/simple-embedding query)
          {:keys [results]} (model/search db query-vector k :method method)
          formatted-results (for [[key similarity] results]
                              {:key key
                               :similarity (double similarity)
                               :document (get @document-store key "No document text available")})]
      (response {:query query, :results formatted-results}))
    (catch Exception e
      (-> {:error (str "Search error: " (.getMessage e))}
          (response)
          (status 400)))))

(defn insert-vector [db key vector]
  (try
    (model/insert db key vector)
    (response {:status "success" :message (str "Vector inserted with key: " key)})
    (catch Exception e
      (-> {:error (.getMessage e)}
          (response)
          (status 400)))))

(defn delete-vector [db key]
  (if (model/delete db key)
    (do
      (swap! document-store dissoc key)
      (response {:status "success" :message (str "Vector with key " key " deleted")}))
    (-> {:error (str "Vector with key " key " not found")}
        (response)
        (status 404))))

(defn search-vectors [db query-vector k method]
  (try
    (let [{:keys [results]} (model/search db query-vector k :method method)
          formatted-results (for [[key similarity] results]
                              {:key key
                               :similarity (double similarity)
                               :document (get @document-store key "No document text available")})]
      (response {:results formatted-results}))
    (catch Exception e
      (-> {:error (.getMessage e)}
          (response)
          (status 400)))))

(defn get-metrics [db]
  (try
    (let [metrics (model/get-metrics db)]
      (response {:cache_hits (.get (:cache-hits metrics))
                 :cache_misses (.get (:cache-misses metrics))
                 :memory_usage_mb (:memory-usage metrics)
                 :total_vectors (:total-vectors metrics)
                 :cache_size_bytes (:cache-size metrics)}))
    (catch Exception e
      (-> {:error (.getMessage e)}
          (response)
          (status 500)))))

(defn api-routes [db]
  (routes
    (GET "/" [] (read-root))
    
    (GET "/vectors" [] 
      (get-all-vectors db))
    
    (GET "/search_text" [query k method]
      (let [k (if k (Integer/parseInt k) 3)
            method (or method "hnsw")]
        (search-text db query k method)))
    
    (POST "/vectors/:key" [key :as req]
      (let [vector (get-in req [:body "vector"])]
        (insert-vector db key vector)))
    
    (DELETE "/vectors/:key" [key]
      (delete-vector db key))
    
    (POST "/search" req
      (let [body (:body req)
            query-vector (get body "query_vector")
            k (or (get body "k") 3)
            method (or (get body "method") "hnsw")]
        (search-vectors db query-vector k method)))
    
    (GET "/metrics" []
      (get-metrics db))
    
    (route/not-found 
      (-> {:error "Not Found"} 
          (response) 
          (status 404)))))

(defn create-api-handler [db]
  (-> (api-routes db)
      (wrap-json-body {:keywords? true})
      (wrap-json-response)
      (wrap-keyword-params)
      (wrap-params)))

;; Helper functions for document management
(defn add-document [db doc-id document-text]
  (try
    (let [vector (embedding/simple-embedding document-text)]
      (model/insert db doc-id vector)
      (swap! document-store assoc doc-id document-text)
      {:success true :message (str "Document '" doc-id "' added successfully!")})
    (catch Exception e
      {:success false :message (str "Error adding document: " (.getMessage e))})))

(defn list-documents [db]
  (let [keys (model/get-all-keys db)
        result (for [key keys]
                 {:id key
                  :content (get @document-store key "Vector without associated document")})]
    result))

(defn delete-document [db doc-id]
  (if (model/delete db doc-id)
    (do
      (swap! document-store dissoc doc-id)
      {:success true :message (str "Document '" doc-id "' deleted successfully!")})
    {:success false :message (str "Document '" doc-id "' not found in the database.")}))

(defn get-database-stats [db]
  (let [metrics (model/get-metrics db)
        cache-hits (.get (:cache-hits metrics))
        cache-misses (.get (:cache-misses metrics))
        hit-ratio (if (zero? (+ cache-hits cache-misses)) 
                    0 
                    (/ cache-hits (+ cache-hits cache-misses)))]
    {:total-vectors (:total-vectors metrics)
     :memory-usage (format "%.2f MB" (:memory-usage metrics))
     :cache-hit-ratio (format "%.2f" hit-ratio)
     :cache-size (format "%.2f MB" (/ (:cache-size metrics) (* 1024.0 1024.0)))}))) 