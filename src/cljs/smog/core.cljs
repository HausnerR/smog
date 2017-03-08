(ns smog.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]

              [smog.main_view :refer [main-view]]))

;; Routes

#_(secretary/defroute "/" []
                    (session/put! :current-page #'main-view))

;; Initialize app

(defn current-page []
  [(or (session/get :current-page) #'main-view)])

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))
