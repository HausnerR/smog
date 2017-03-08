(ns smog.handler
  (:require [clojure.core.async :as async]
            [clj-http.client :as http-client]
            [clojure.data.json :as json]
            [clj-time.core :as time]
            [clj-time.coerce :as time-c]
            [clj-time.format :as time-f]
            [compojure.core :refer [GET defroutes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [smog.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]))

(defn loading-page []
  (html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:content "IE=edge" :http-equiv "X-UA-Compatible"}]
     [:meta {:content "width=device-width, initial-scale=1" :name "viewport"}]
     [:title "SMOG data"]
     [:meta {:content "" :name "description"}]
     [:meta {:content "Jakub Pachciarek" :name "author"}]

     [:link {:rel "stylesheet" :href "http://fonts.googleapis.com/icon?family=Material+Icons"}]
     [:link {:rel "stylesheet" :href "https://cdnjs.cloudflare.com/ajax/libs/materialize/0.98.0/css/materialize.min.css" :type "text/css"}]

     [:meta {:content "width=device-width, initial-scale=1.0" :name "viewport"}]

     (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))]

    [:body.blue-grey.lighten-5
     [:nav.top-nav
      [:div.nav-wrapper.blue-grey.darken-2
       [:a.brand-logo.center {:href "#"} "SMOG data"]]]

     [:main#app
      [:div.container
       [:div.loading
        [:div.preloader-wrapper.big.active
         [:div.spinner-layer
          [:div.circle-clipper.left [:div.circle]]
          [:div.gap-patch [:div.circle]]
          [:div.circle-clipper.right [:div.circle]]]]]]]

     [:footer.page-footer.blue-grey
      [:div.container
       [:div.row
        [:div.col.s12
         #_[:h5.white-text "Footer Content"]

         [:p.grey-text.text-lighten-4
          "Dane prezentowane na stronie pochodzą z "
          [:a.grey-text.text-lighten-1 {:href "http://www.gios.gov.pl/pl/"} "http://www.gios.gov.pl/pl/"]
          "."]]]]

      [:div.footer-copyright
       [:div.container
        "© 2017 Copyright Jakub Pachciarek"

        #_[:a.grey-text.text-lighten-4.right {:href "#!"} "More apps"]]]]

     [:script {:src "https://code.jquery.com/jquery-2.1.1.min.js" :type "text/javascript"}]
     [:script {:src "https://cdnjs.cloudflare.com/ajax/libs/materialize/0.98.0/js/materialize.min.js" :type "text/javascript"}]
     (include-js "/js/app.js")]))


(def GIOS-FETCH-INTERVAL 180)
(def gios-data (atom nil))

(defn get-gios-data []
  (println (str (time-f/unparse (time-f/formatter "YYYY-MM-dd HH:mm:ss") (time/now))
                " Getting GIOS data..."))
  (if-let [json (try
                    (->
                      (http-client/get "http://powietrze.gios.gov.pl/pjp/current/getAQIDetailsList?param=PM10"
                                       {:accept :json
                                        :socket-timeout 60000
                                        :conn-timeout 60000})
                      :body
                      json/read-str)
                    (catch Exception e
                      (println "GIOS request/parse error: " (.getMessage e))
                      nil))]
    (do
      (println (str (time-f/unparse (time-f/formatter "YYYY-MM-dd HH:mm:ss") (time/now))
                    " Got GIOS data. "
                    (count json)
                    " stations"))
      (reset! gios-data (json/write-str {"data"    json
                                         "updated" (time-c/to-long (time/now))}))
      true)
    false))

(async/go-loop []
  (get-gios-data)
  (async/<! (async/timeout (* GIOS-FETCH-INTERVAL 1000)))
  (recur))


(defroutes routes
  (GET "/" [] (loading-page))
  (GET "/data" [] @gios-data)

  (resources "/")
  (not-found "Not Found"))

(def app (wrap-middleware #'routes))
