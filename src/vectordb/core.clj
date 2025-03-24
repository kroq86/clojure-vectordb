(ns vectordb.core
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults site-defaults]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [compojure.core :refer [defroutes routes context GET POST ANY]]
            [compojure.route :as route]
            [vectordb.model :as model]
            [vectordb.api :as api]
            [vectordb.ui :as ui]
            [clojure.tools.logging :as log])
  (:gen-class))

(def db-path (or (System/getenv "VECTOR_DB_PATH") ":memory:"))
(def vector-db (atom nil))
(def enable-csrf (= (System/getenv "ENABLE_CSRF") "true"))

(defn create-combined-handler [db]
  (let [api-handler (api/create-api-handler db)
        ui-handler (ui/ui-routes db)
        
        ;; Site defaults with optional CSRF
        site-config (if enable-csrf
                      site-defaults
                      (assoc-in site-defaults [:security :anti-forgery] false))]
    
    (routes
      (context "/api" []
        (-> api-handler
            (wrap-json-body {:keywords? true})
            (wrap-json-response)
            (wrap-defaults api-defaults)))
      
      (ANY "*" []
        (-> ui-handler
            (wrap-multipart-params)
            (wrap-params)
            (wrap-keyword-params)
            (wrap-defaults site-config))))))

(defn start-server [port]
  (let [db (model/new-vector-database db-path)
        handler (create-combined-handler db)]
    (reset! vector-db db)
    (log/info "Initializing sample data...")
    (api/init-sample-data db)
    (log/info "Starting server on port" port)
    (log/info "CSRF protection:" (if enable-csrf "enabled" "disabled"))
    (jetty/run-jetty handler {:port port :join? false})))

(defn -main
  "Start the vector database server"
  [& args]
  (let [port (Integer/parseInt (or (System/getenv "PORT") "3000"))]
    (start-server port)
    (log/info "Server running on port" port)))

(defn stop-server [server]
  (.stop server)
  (when-let [db @vector-db]
    (model/close db)
    (reset! vector-db nil))
  (log/info "Server stopped."))

(comment
  ;; Development helpers
  (def server (start-server 3000))
  (stop-server server)
  
  ;; Initialize fresh sample data 
  (api/init-sample-data @vector-db)
  
  ;; Check existing data
  (model/get-all-keys @vector-db)
  
  ;; Get database stats
  (api/get-database-stats @vector-db)
  
  ;; Search example
  (let [query "machine learning"
        query-vector (vectordb.embedding/simple-embedding query)]
    (model/search @vector-db query-vector 3))
) 