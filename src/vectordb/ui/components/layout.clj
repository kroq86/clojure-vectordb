(ns vectordb.ui.components.layout
  (:require [hiccup.page :refer [html5 include-css include-js]]))

(defn base-layout [title & content]
  (html5
    [:head
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:title title]
     [:link {:rel "stylesheet" :href "https://cdn.jsdelivr.net/npm/bulma@0.9.4/css/bulma.min.css"}]
     [:script {:src "https://code.jquery.com/jquery-3.6.0.min.js"}]
     [:script "document.addEventListener('DOMContentLoaded', () => {
       // Auto-hide notifications after 3 seconds
       const notifications = document.querySelectorAll('.notification');
       notifications.forEach(notification => {
         setTimeout(() => {
           notification.style.display = 'none';
         }, 3000);
       });
       
       // Handle delete buttons
       const deleteButtons = document.querySelectorAll('.notification .delete');
       deleteButtons.forEach(button => {
         button.addEventListener('click', () => {
           button.parentElement.style.display = 'none';
         });
       });
     });"]
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

(defn message-page [title message back-url]
  (base-layout title
    [:div
     [:h1.title title]
     [:p.subtitle message]
     [:div.block
      [:a.button.is-info {:href back-url} "Back"]]])) 