(ns vectordb.ui.components.search
  (:require [vectordb.ui.components.layout :refer [base-layout]]
            [vectordb.api :as api]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

(defn search-form []
  [:div.card
   [:div.card-header
    [:p.card-header-title "Search"]]
   [:div.card-content
    [:form#search-form {:action "/ui/search" :method "get"}
     [:div.field
      [:div.control
       [:input.input {:type "text" 
                     :name "query" 
                     :placeholder "Enter search query" 
                     :required true 
                     :autofocus true}]]]
     [:div.field
      [:div.control.is-flex.is-justify-content-space-between
       [:button.button.is-primary {:type "submit"} "Search"]
       [:div.select.is-small.ml-2
        [:select {:name "num_results"}
         [:option {:value "3"} "3 results"]
         [:option {:value "5"} "5 results"]
         [:option {:value "10"} "10 results"]]]]]
     [:input {:type "hidden" :name "method" :value "hnsw"}]]]])

(defn search-result-item [result]
  [:div.box.mb-4
   [:p [:strong "Document ID: "] (:key result)]
   [:p [:strong "Similarity: "] (format "%.4f" (:similarity result))]
   [:p [:strong "Content: "] (str/trim (:document result))]])

(defn search-results-page [db query num-results method]
  (let [num-results-int (Integer/parseInt (or num-results "3"))
        method-str (or method "hnsw")
        search-results (api/search-text db query num-results-int method-str)
        results (get-in search-results [:body :results])]
    
    ;; Debug info
    (println "Search request:")
    (println "  Query:" query)
    (println "  Num results:" num-results-int)
    (println "  Method:" method-str)
    (println "Raw search results:")
    (pprint search-results)
    (println "Processed results:")
    (pprint results)
    
    (base-layout "Search Results"
      [:div
       [:h1.title "Search Results"]
       [:p.subtitle (str "Query: \"" query "\"")]
       
       ;; Include search form at the top for easy refinement
       (search-form)
       
       [:div.block.mt-4
        [:a.button.is-info {:href "/"} "Back to Home"]]
       
       [:div.content.mt-4
        (if (or (nil? results) (empty? results))
          [:p "No results found."]
          [:div
           (for [result results]
             (search-result-item result))])]]))) 