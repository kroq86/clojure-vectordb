(ns vectordb.ui.components.documents
  (:require [vectordb.ui.components.layout :refer [base-layout]]
            [vectordb.api :as api]))

(defn document-item [doc]
  [:div.result-item
   [:p [:strong "Document ID: "] (:id doc)]
   [:p [:strong "Content: "] (:content doc)]])

(defn document-list [db]
  (let [documents (api/list-documents db)]
    [:div.card
     [:div.card-header
      [:p.card-header-title "All Documents"]]
     [:div.card-content
      (if (empty? documents)
        [:p "No documents found."]
        [:div.documents-container {:style "max-height: 400px; overflow-y: auto;"}
         (map document-item documents)])]]))

(defn document-actions []
  [:div
   [:div.card
    [:div.card-header
     [:p.card-header-title "Add Document"]]
    [:div.card-content
     [:form {:action "/ui/add-document" :method "post"}
      [:div.field
       [:label.label "Document ID"]
       [:div.control
        [:input.input {:type "text" :name "doc_id" :placeholder "e.g., doc123" :required true}]]]
      
      [:div.field
       [:label.label "Document Text"]
       [:div.control
        [:textarea.textarea {:name "document_text" :rows 3 :placeholder "Enter document content" :required true}]]]
      
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
        [:input.input {:type "text" :name "doc_id" :placeholder "e.g., doc123" :required true}]]]
      
      [:div.field
       [:div.control
        [:button.button.is-danger {:type "submit"} "Delete Document"]]]]]]])

(defn document-list-page [db]
  (base-layout "Document List"
    [:div
     [:h1.title "All Documents"]
     
     [:div.block
      [:a.button.is-info {:href "/"} "Back to Home"]]
     
     [:div.content
      (let [documents (api/list-documents db)]
        (if (empty? documents)
          [:p "No documents found."]
          [:div
           (map document-item documents)]))]])) 