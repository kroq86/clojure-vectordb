(ns vectordb.agent
  (:require [vectordb.model :as model]
            [vectordb.embedding :as embedding]
            [clojure.string :as str]))

(defrecord AgentContext [db max-results default-method])

(defn new-agent-context
  "Create a new agent context with configuration"
  [db & {:keys [max-results default-method]
         :or {max-results 5 default-method "hnsw"}}]
  (->AgentContext db max-results default-method))

(defn search-knowledge
  "Search the knowledge base with natural language query"
  [context query & {:keys [k method] :or {k nil method nil}}]
  (let [{:keys [db max-results default-method]} context
        k (or k max-results)
        method (or method default-method)
        query-vector (embedding/simple-embedding query)
        {:keys [results]} (model/search db query-vector k :method method)]
    {:query query
     :method method
     :results (map (fn [[key similarity]]
                    {:key key
                     :similarity (double similarity)})
                  results)}))

(defn store-knowledge
  "Store new knowledge in the database"
  [context key content]
  (let [{:keys [db]} context
        vector (embedding/simple-embedding content)]
    (model/insert db key vector)
    {:status "success"
     :key key
     :vector-length (count vector)}))

(defn get-knowledge
  "Retrieve specific knowledge by key"
  [context key]
  (let [{:keys [db]} context
        vector (model/retrieve db key)]
    (if vector
      {:status "success"
       :key key
       :vector-length (count vector)}
      {:status "not-found"
       :key key})))

(defn delete-knowledge
  "Remove knowledge from the database"
  [context key]
  (let [{:keys [db]} context]
    (if (model/delete db key)
      {:status "success"
       :key key}
      {:status "not-found"
       :key key})))

(defn batch-store-knowledge
  "Store multiple pieces of knowledge at once"
  [context knowledge-map]
  (let [{:keys [db]} context
        results (atom [])]
    (doseq [[key content] knowledge-map]
      (let [result (store-knowledge context key content)]
        (swap! results conj result)))
    {:status "success"
     :results @results}))

(defn get-database-stats
  "Get database statistics and metrics"
  [context]
  (let [{:keys [db]} context
        metrics (model/get-metrics db)]
    {:cache-hits (.get (:cache-hits metrics))
     :cache-misses (.get (:cache-misses metrics))
     :memory-usage-mb (:memory-usage metrics)
     :total-vectors (:total-vectors metrics)
     :cache-size-bytes (:cache-size metrics)})) 