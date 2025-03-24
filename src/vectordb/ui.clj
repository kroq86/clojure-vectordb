(ns vectordb.ui
  (:require [clojure.string :as str]
            [hiccup.page :refer [html5 include-css include-js]]
            [ring.util.response :refer [response content-type]]
            [vectordb.api :as api]
            [vectordb.embedding :as embedding]))

(defn base-layout [title & content]
  (html5
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:title title]
     [:link {:rel "stylesheet" :href "https://cdn.jsdelivr.net/npm/bulma@0.9.4/css/bulma.min.css"}]
     [:script {:src "https://code.jquery.com/jquery-3.6.0.min.js"}]
     [:style "
       .main-container { padding: 2rem; }
       .card { margin-bottom: 1.5rem; }
       .result-item { margin-bottom: 1rem; padding: 1rem; border: 1px solid #eee; border-radius: 4px; }
     "]]
    [:body
     [:nav.navbar.is-info
      [:div.navbar-brand
       [:a.navbar-item {:href "/"}
        [:span.is-size-4 "Vector Database"]]]]
     
     [:div.main-container
      content]
     
     [:footer.footer
      [:div.content.has-text-centered
       [:p "Vector Database in Clojure"]]]]))

(defn index-page [db]
  (base-layout "Vector Database"
    [:div.columns
     
     [:div.column.is-one-third
      [:div.card
       [:div.card-header
        [:p.card-header-title "Search Documents"]]
       [:div.card-content
        [:form#search-form {:action "/ui/search" :method "get"}
         [:div.field
          [:label.label "Query Text"]
          [:div.control
           [:textarea.textarea {:name "query" :rows 3 :placeholder "Enter your search query"}]]]
         
         [:div.field
          [:label.label "Number of Results"]
          [:div.control
           [:input.input {:type "number" :name "num_results" :value 3 :min 1 :max 10}]]]
         
         [:div.field
          [:label.label "Search Method"]
          [:div.control
           [:div.select
            [:select {:name "method"}
             [:option {:value "exact"} "Exact Search"]
             [:option {:value "approximate"} "Approximate Search"]
             [:option {:value "lsh"} "LSH Search"]
             [:option {:value "hnsw"} "HNSW (default)"]]]]]
         
         [:div.field
          [:div.control
           [:button.button.is-primary {:type "submit"} "Search"]]]]]]]
     
     [:div.column
      [:div.card
       [:div.card-header
        [:p.card-header-title "Database Status"]]
       [:div.card-content
        (let [stats (api/get-database-stats db)]
          [:div
           [:p (str "Total Vectors: " (:total-vectors stats))]
           [:p (str "Memory Usage: " (:memory-usage stats))]
           [:p (str "Cache Hit Ratio: " (:cache-hit-ratio stats))]
           [:p (str "Cache Size: " (:cache-size stats))]])]]
      
      [:div.card
       [:div.card-header
        [:p.card-header-title "Add Document"]]
       [:div.card-content
        [:form {:action "/ui/add-document" :method "post"}
         [:div.field
          [:label.label "Document ID"]
          [:div.control
           [:input.input {:type "text" :name "doc_id" :placeholder "e.g., doc123"}]]]
         
         [:div.field
          [:label.label "Document Text"]
          [:div.control
           [:textarea.textarea {:name "document_text" :rows 3 :placeholder "Enter document content"}]]]
         
         [:div.field
          [:div.control
           [:button.button.is-success {:type "submit"} "Add Document"]]]]]]
      
      [:div.card
       [:div.card-header
        [:p.card-header-title "Delete Document"]]
       [:div.card-content
        [:form {:action "/ui/delete-document" :method "post"}
         [:div.field
          [:label.label "Document ID"]
          [:div.control
           [:input.input {:type "text" :name "doc_id" :placeholder "e.g., doc123"}]]]
         
         [:div.field
          [:div.control
           [:button.button.is-danger {:type "submit"} "Delete Document"]]]]]]]]))

(defn search-results-page [db query num-results method]
  (let [search-results (api/search-text db query (Integer/parseInt num-results) method)
        results (:results search-results)]
    (base-layout "Search Results"
      [:div
       [:h1.title "Search Results"]
       [:p.subtitle (str "Query: \"" query "\"")]
       
       [:div.block
        [:a.button.is-info {:href "/"} "Back to Home"]]
       
       [:div.content
        (if (empty? results)
          [:p "No results found."]
          [:div
           (for [result results]
             [:div.result-item
              [:p [:strong "Document ID: "] (:key result)]
              [:p [:strong "Similarity: "] (format "%.4f" (:similarity result))]
              [:p [:strong "Content: "] (:document result)]])])]])))

(defn document-list-page [db]
  (let [documents (api/list-documents db)]
    (base-layout "Document List"
      [:div
       [:h1.title "All Documents"]
       
       [:div.block
        [:a.button.is-info {:href "/"} "Back to Home"]]
       
       [:div.content
        (if (empty? documents)
          [:p "No documents found."]
          [:div
           (for [doc documents]
             [:div.result-item
              [:p [:strong "Document ID: "] (:id doc)]
              [:p [:strong "Content: "] (:content doc)]])])]])))

(defn message-page [title message back-url]
  (base-layout title
    [:div
     [:h1.title title]
     [:p.subtitle message]
     [:div.block
      [:a.button.is-info {:href back-url} "Back"]]]))

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
              method (get params "method" "hnsw")]
          (-> (search-results-page db query num-results method)
              (response)
              (content-type "text/html")))
        
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
                  [:a.button.is-info {:href "/"} "Back to Home"]])}))))) 