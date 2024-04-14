(ns nomis-electric-demo.main
  {:clj-kondo/config
   '{:linters {:unresolved-symbol {:exclude #?(:clj []
                                               :cljs [])}
               :unresolved-namespace {:exclude #?(:clj [js]
                                                  :cljs [])}}}}
  (:require
   [clojure.math :as math]
   [contrib.assert :refer [check]]
   [contrib.data :refer [auto-props round-floor]]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-ui4 :as ui]))

(e/defn GridSheet [xs props]
  ;; Copied from Elecric Clojure's `contrib.gridsheet` and hacked.
  (let [props (auto-props props
                          {::row-height 24
                           ::page-size  20})
        {:keys [::id-key
                ::Format
                ::columns
                ::row-height ; px, same unit as scrollTop
                ::page-size #_ "tight"]} props
        Format (or Format (e/fn [m k] (e/client (dom/text (pr-str (get m k))))))
        client-height (* (inc (check number? page-size)) (check number? row-height))
        rows (seq xs)
        row-count (count rows)]
    (when (nil? columns)
      (throw (ex-info "gridsheet: ::columns prop is required" {})))
    (when (nil? id-key)
      (throw (ex-info "gridsheet: ::id-key prop is required" {})))
    (e/client
      (dom/div (dom/props {:role  "grid"
                           :style {:height (str client-height "px")
                                   :display "grid", :overflowY "auto"
                                   ;;
                                   :grid-template-columns
                                   (->> (repeat (e/server (count columns)) "1fr")
                                        (interpose " ") (apply str))}})
               (let [[scroll-top _scroll-height _client-height'] (new (ui/scroll-state< dom/node))
                     _ (e/server (println "**** scroll-top =" scroll-top))
                     max-height (* row-count row-height)
                     padding-bottom (js/Math.max (- max-height client-height) 0)
                     ;; don't scroll past the end
                     clamped-scroll-top (js/Math.min scroll-top padding-bottom)
                     start-row (math/ceil (/ clamped-scroll-top row-height))
                     _start-row-page-aligned (round-floor start-row page-size)]
                 (e/for [k columns]
                   (dom/div (dom/props
                             {:role  "columnheader"
                              :style {:position         "sticky"
                                      :top              (str 0 "px")
                                      :background-color "rgb(248 250 252)"
                                      :box-shadow       "0 1px gray"
                                      :width            "max-content"}})
                            (dom/text (name k))))
                 (let [Row (e/fn [i m]
                             (e/client
                               (when (or (< i 0) (>= i page-size))
                                 (println "**** Unexpected i" i))
                               (dom/div
                                 (dom/props {:role "group"
                                             :style {:display "contents"
                                                     :grid-row (inc i)}})
                                 (e/for [k columns]
                                   (dom/div
                                     (dom/props
                                      {:role  "gridcell"
                                       :style {:position "sticky"
                                               :top      (str (* row-height
                                                                 (inc i))
                                                              "px")
                                               :height   (str row-height "px")}})
                                     (e/server (case m
                                                 ::empty nil
                                                 (Format. m k))))))))]
                   (e/server
                     (println "                      **** start-row ="
                              start-row)
                     (let [ms (->> (concat rows (repeat ::empty))
                                   (drop start-row)
                                   (take page-size))]

                       (case :v2 ; change this to try different cases

                         ;; `:v1` is copied more-or-less from
                         ;; `contrib.gridsheet`. It works, but the key function
                         ;; doesn't return the right value. Instead of returning
                         ;; a key that depends on the row, it returns the values
                         ;; 0 up to `page-size` -- so when the user scrolls the
                         ;; same row gets different keys.
                         :v1
                         (let [ms (vec ms)]
                           (e/for [i (range page-size)]
                             (let [m (get ms i)]
                               (e/client (Row. i m)))))


                         ;; `:v2` is my fix for the above issue. It returns
                         ;; a unique key for the row in the correct manner.
                         ;; But `scroll-top` keeps changing and the UI forever
                         ;; jumps about showing different rows at the top.
                         :v2
                         (e/for-by id-key [m ms]
                           (let [i (dec (- (get m id-key)
                                           start-row))]
                             (e/client (Row. i m))))))))
                 (dom/div
                   (dom/props
                    {:style {:padding-bottom (str padding-bottom "px")}})))) ; scrollbar
      )))

(defn ->k [x] (-> x str keyword))

(def max-x 10)
(def max-y 100)

(def data
  (for [j (map inc (range max-y))]
    (into {}
          (for [i (map inc (range max-x))]
            [(->k i) (* i j)]))))

(e/defn Main [_ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (let [first-column-key (-> 1 str keyword)]
        (e/server
          (GridSheet. data
                      {:id-key    first-column-key
                       :columns   (map (comp ->k inc) (range max-x))
                       :page-size 12}))))))
