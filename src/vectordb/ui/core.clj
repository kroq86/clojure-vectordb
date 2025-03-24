(ns vectordb.ui.core
  (:require [clojure.string :as str]
            [ring.util.response :refer [response content-type]]
            [vectordb.api :as api]
            [vectordb.ui.components.layout :refer [base-layout message-page]]
            [vectordb.ui.components.search :refer [search-form search-results-page]]
            [vectordb.ui.components.documents :refer [document-list document-actions document-list-page]]
            [vectordb.ui.components.status :refer [db-status]]))

(defn index-page [db]
  (base-layout "Vector Database"
    [:div.columns
     
     [:div.column
      ;; Search first, on top
      (search-form)
      
      ;; Then document list with scrollable area
      (document-list db)]
     
     [:div.column
      ;; Status component
      (db-status db)
      
      ;; Document actions
      (document-actions)]]))

(defn ui-routes [db]
  (fn [request]
    (let [uri (:uri request)
          method (:request-method request)]
      (cond
        (and (= uri "/") (= method :get))
        (-> (index-page db)
            (response)
            (content-type "text/html"))
        
        (and (= uri "/ui/search") (= method :get))
        (let [params (:query-params request)
              query (get params "query")
              num-results (get params "num_results" "3")
              search-method (get params "method" "hnsw")]
          (if (str/blank? query)
            (-> (message-page "Search Error" "Please enter a search query" "/")
                (response)
                (content-type "text/html"))
            (-> (search-results-page db query num-results search-method)
                (response)
                (content-type "text/html"))))
        
        (and (= uri "/ui/documents") (= method :get))
        (-> (document-list-page db)
            (response)
            (content-type "text/html"))
        
        (and (= uri "/ui/add-document") (= method :post))
        (let [params (:form-params request)
              doc-id (get params "doc_id")
              doc-text (get params "document_text")
              result (api/add-document db doc-id doc-text)]
          (-> (message-page "Document Added" (:message result) "/")
              (response)
              (content-type "text/html")))
        
        (and (= uri "/ui/delete-document") (= method :post))
        (let [params (:form-params request)
              doc-id (get params "doc_id")
              result (api/delete-document db doc-id)]
          (-> (message-page "Document Deleted" (:message result) "/")
              (response)
              (content-type "text/html")))
        
        :else
        {:status 404
         :headers {"Content-Type" "text/html"}
         :body (base-layout "Not Found"
                 [:div
                  [:h1.title "Page Not Found"]
                  [:p "The requested page does not exist."]
                  [:a.button.is-info {:href "/"} "Back to Home"]])})))) 