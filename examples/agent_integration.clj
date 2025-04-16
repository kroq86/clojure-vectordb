(ns agent-integration
  (:require [vectordb.model :as vdb]
            [vectordb.agent :as agent]
            [clojure.pprint :refer [pprint]]))

(defn print-section [title]
  (println "\n" (str "=== " title " ===")))

(defn print-result [label result]
  (println "\n" label)
  (pprint result))

(defn example-agent-integration []
  ;; Create a new vector database
  (let [db (vdb/new-vector-database "agent-db.db")
        
        ;; Create agent context with custom configuration
        agent-context (agent/new-agent-context 
                      db 
                      :max-results 3 
                      :default-method "hnsw")
        
        ;; Sample knowledge to store
        knowledge-map {"python" "Python is a high-level programming language known for its readability and versatility."
                      "ml" "Machine learning models require training data to learn patterns and make predictions."
                      "db" "Database indexing improves query performance by creating data structures that speed up data retrieval."}]
    
    (print-section "Storing Knowledge")
    (print-result "Batch storage results:" 
                 (agent/batch-store-knowledge agent-context knowledge-map))
    
    (print-section "Searching Knowledge")
    (print-result "Search results for 'programming language':" 
                 (agent/search-knowledge agent-context "programming language"))
    
    (print-section "Retrieving Knowledge")
    (print-result "Retrieval results for 'python':" 
                 (agent/get-knowledge agent-context "python"))
    
    (print-section "Database Statistics")
    (print-result "Current database stats:" 
                 (agent/get-database-stats agent-context))
    
    (print-section "Deleting Knowledge")
    (print-result "Deletion results for 'db':" 
                 (agent/delete-knowledge agent-context "db"))
    
    (print-section "Verification Search")
    (print-result "Search results after deletion:" 
                 (agent/search-knowledge agent-context "database"))
    
    ;; Close the database
    (vdb/close db)))

(defn -main
  "Run the agent integration example"
  [& args]
  (try
    (example-agent-integration)
    (catch Exception e
      (println "Error running agent integration example:" (.getMessage e))
      (System/exit 1)))) 