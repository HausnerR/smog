(ns smog.main_view
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent-forms.core :refer [bind-fields]]
              [clojure.string :as str]
              [ajax.core :refer [GET]]
              [cljs-time.core :as time]
              [cljs-time.format :as time-f]
              [cljs-time.coerce :as time-c]))


(def user-stations-list (atom nil))
(def stations-data (atom nil))
(def last-updated (atom nil))



;Save and restore state from url
(defn restore-user-stations-list-from-url []
  (reset! user-stations-list
          (->> (-> js/document.location.hash (subs 1) (str/split ","))
               (keep #(when-let [digits (re-find #"\d+" %)] (js/parseInt digits)))
               set)))

(js/window.addEventListener "hashchange" restore-user-stations-list-from-url)
(restore-user-stations-list-from-url)


(defn save-user-stations-list-to-url []
  (set! js/document.location.hash (str "#" (str/join "," @user-stations-list))))

(add-watch user-stations-list :url-watcher save-user-stations-list-to-url)



;Get data from backend loop
(defn update-stations-data []
  (GET "/data"
       {:response-format :json
        :keywords?       true
        :handler         #(do
                            (reset! stations-data (:data %))
                            (reset! last-updated (:updated %)))
        :error-handler   #(do)}))

(js/setInterval update-stations-data (* 30 1000))
(update-stations-data)



(defn add-station-component []
  (let [popup-opened (atom false)
        input-data (atom nil)

        add-station (fn [station]
                      (swap! user-stations-list conj (-> station :stationId))
                      (reset! popup-opened false))

        filter-stations (fn [stations filter-text]
                          (keep (fn [station]
                                  (when (re-find (re-pattern (str "(?i)" filter-text)) (-> station :stationName))
                                    station))
                                stations))]

    (reagent/create-class
      {:component-did-update
       (fn []
         (-> (js/document.getElementById "add-station-filter-text") .focus))

       :reagent-render
       (fn []
         [:div.add-station-component.left-align
          [:button.waves-effect.waves-light.btn {:on-click #(reset! popup-opened true)} "Dodaj stację"]

          [:div.overlay {:class (when-not @popup-opened "hide") :on-click #(reset! popup-opened false)}]
          [:div.collection.with-header.scale-transition.scale-out {:class (when @popup-opened "scale-in")}
           [:div.collection-header
            [:div.input-field
             [bind-fields [:input {:field :text :id :add-station-filter-text :placeholder "Search"}] input-data]]]

           [:div.overflow
            (for [station (filter-stations @stations-data (-> @input-data :add-station-filter-text))]
              ^{:key (-> station :stationId)}
              [:a.collection-item {:href "" :on-click #(do (.preventDefault %) (add-station station))}
               (-> station :stationName)])]]])})))


(defn station-row-component [station]
  (let [green "green darken-2"
        yellow "amber lighten-1"
        orange "orange darken-2"
        red "red darken-3"
        purple "deep-purple darken-4"
        black "grey darken-4"
        grey "blue-grey"

        aqIndexMap {0 "bardzo dobre"
                    1 "dobre"
                    2 "umiarkowane"
                    3 "dostateczne"
                    4 "złe"
                    5 "bardzo złe"}

        aqIndexColorMap {0 green
                         1 green
                         2 orange
                         3 red
                         4 purple
                         5 black}

        pm25ColorMap {12 green
                      35 yellow
                      55 orange
                      150 red
                      250 purple
                      1000 black}

        pm10ColorMap {54 green
                      154 yellow
                      254 orange
                      354 red
                      424 purple
                      1000 black}

        o3ColorMap {125 green
                    164 orange
                    204 red
                    404 purple
                    1000 black}

        no2ColorMap {53 green
                     100 yellow
                     360 orange
                     649 red
                     1249 purple
                     10000 black}

        coColorMap {4400 green
                    9400 yellow
                    12400 orange
                    15400 red
                    30400 purple
                    100000 black}

        so2ColorMap {35 green
                     75 yellow
                     185 orange
                     304 red
                     604 purple
                     1000 black}

        color-for-norm (fn [value norm-color-map]
                         (if value
                           (some (fn [[norm color]] (when (<= value norm) color))
                                 norm-color-map)
                           grey))

        station-section-card (fn [title color value]
                               [:div.card {:class color}
                                [:div.card-content.white-text
                                 [:span.card-title title]

                                 (when (some #{color} [orange red purple black])
                                   [:i.material-icons.warning-icon "warning"])

                                 (if value
                                   [:p.right-align.truncate.value value [:span.unit " µg/m" [:sup 3]]]
                                   [:p.right-align.truncate.no-data "brak danych"])]])

        remove-station (fn [station]
                         (swap! user-stations-list disj (-> station :stationId)))]
    (fn []
      [:div
       [:div.section.station-row
        [:div.row
         [:div.col.m7.s12
          [:h5 (-> station :stationName)]]

         [:div.col.m5.s12
          [:button.waves-effect.waves-light.btn.red.darken-4.remove-button {:on-click #(remove-station station)}
           [:i.material-icons "delete"]]

          [:span.new.badge {:data-badge-caption ""
                            :class (get aqIndexColorMap (-> station :aqIndex))}
           (str "ogólna jakość powietrza: " (get aqIndexMap (-> station :aqIndex)))]]]

        [:div.row
         [:div.col.m4.s6
          [station-section-card "PM2.5" (color-for-norm (-> station :values :PM2.5) pm25ColorMap) (-> station :values :PM2.5)]]

         [:div.col.m4.s6
          [station-section-card "PM10" (color-for-norm (-> station :values :PM10) pm10ColorMap) (-> station :values :PM10)]]

         [:div.col.m4.s6
          [station-section-card "CO" (color-for-norm (-> station :values :CO) coColorMap) (-> station :values :CO)]]

         [:div.col.m3.s6
          [station-section-card "SO2" (color-for-norm (-> station :values :SO2) so2ColorMap) (-> station :values :SO2)]]

         [:div.col.m3.s6
          [station-section-card "NO2" (color-for-norm (-> station :values :NO2) no2ColorMap) (-> station :values :NO2)]]

         [:div.col.m3.s6
          [station-section-card "O3" (color-for-norm (-> station :values :O3) o3ColorMap) (-> station :values :O3)]]

         [:div.col.m3.s6
          [station-section-card "C6H6" grey (-> station :values :C6H6)]]]]])))


(defn main-view []
  (let [get-user-stations (fn []
                            (let [usl @user-stations-list]
                              (keep #(when (get usl (:stationId %)) %) @stations-data)))]
    (fn []
      [:div.container
       [:div.buttons-bar
        [:span.last-update
         "Ostatnia aktualizacja danych: " (time-f/unparse (time-f/formatter "dd.MM.yyyy HH:mm") (time/to-default-time-zone (time-c/from-long @last-updated)))]

        [add-station-component]]

       (when (empty? @user-stations-list)
         [:div
          [:div.divider]
          [:div.row
           [:div.col.m8.offset-m2.s12
            [:div.card.blue-grey.darken-2.welcome-info-box
             [:div.card-content.white-text
              [:span.card-title "Jak to działa?"]
              [:p "Dodając obserwowane stacje pomiaru zmienia się adres w przeglądarce." [:br] "Jeżeli chcesz mieć szybki dostęp do swojej listy stacji, zapisz sobie link, np. w ulubionych."]]

             [:div.card-content.white-text
              [:span.card-title "Po co?"]
              [:p "Aplikacja ta została stworzona aby ułatwić dostęp do danych z GIOS. Ostatnimi czasy trzeba mieć baaardzo dużo cierpliwości aby móc na ich stronie sprawdzić aktualny stan zapylenia."]
              [:p "Dlatego też aplikacja ta pobiera co kilka minut informacje ze strony GIOS, zapisuje je i następnie serwuje wszystkim odwiedzającym."]]

             [:div.card-content.white-text
              [:span.card-title "Kolorki!"]
              [:p "Do oznaczenia kolorem wartości zapylenia stosowana jest norma amerykańska US EPA, gdyż Polskie czy Europejskie normy mają mniej progów."]]]

            [:div.card.blue-grey.darken-2.welcome-info-box
             [:div.card-content.white-text
              [:span.card-title "Kontakt"]
              [:p "Jeżeli masz jakieś uwagi co do działania tej mini-aplikacji, bądź chcesz pomóc w jej ulepszeniu, śmiało kontaktuj się pod " [:strong "kuba [at] pachciarek.pl"]]]]]]])

       (for [station (get-user-stations)]
         ^{:key (-> station :stationId)}
         [:div
          [:div.divider]
          [station-row-component station]])])))
