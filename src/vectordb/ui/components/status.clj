(ns vectordb.ui.components.status
  (:require [vectordb.api :as api]))

(defn db-status [db]
  [:div.card
   [:div.card-header
    [:p.card-header-title "Database Status"]]
   [:div.card-content
    (let [stats (api/get-database-stats db)]
      [:div
       [:p (str "Total Vectors: " (:total-vectors stats))]
       [:p (str "Memory Usage: " (:memory-usage stats))]
       [:p (str "Cache Hit Ratio: " (:cache-hit-ratio stats))]
       [:p (str "Cache Size: " (:cache-size stats))]])]]) 