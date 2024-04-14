(ns nomis-electric-demo.main
  {:clj-kondo/config
   '{:linters {:unresolved-namespace {:exclude #?(:clj [js]
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
                               (dom/div
                                 (dom/props {:role "group"
                                             :style {:display "contents"
                                                     :grid-row (inc i)}})
                                 (e/for [k columns]
                                   (dom/div
                                     (dom/props
                                      {:role  "gridcell"
                                       :style {:position "sticky"
                                               :top      (str (* row-height (inc i)) "px")
                                               :height   (str row-height "px")}})
                                     (e/server (case m
                                                 ::empty nil
                                                 (Format. m k))))))))]
                   (e/server
                     (println "**** start-row =" start-row)
                     (case :v2
                       :v1
                       (let [xs (vec (->> rows (drop start-row) (take page-size)))]
                         (e/for [i (range page-size)]
                           (let [m (get xs i ::empty)]
                             (e/client (Row. i m)))))
                       :v2
                       (let [xs (->> rows (drop start-row) (take page-size))
                             kf (case :v2a
                                  :v2a identity
                                  :v2b (fn [x]
                                         (let [res (id-key x)]
                                           (println "**** e/for-by kf value ="
                                                    res)
                                           res)))]
                         (e/for-by kf [m (take page-size
                                               (concat xs
                                                       (repeat [0 ::empty])))]
                           (let [i (dec (- (get m id-key)
                                           start-row))]
                             (e/client (Row. i m))))))))
                 (dom/div
                   (dom/props
                    {:style {:padding-bottom (str padding-bottom "px")}})))) ; scrollbar
      )))

(defn ->k [x] (-> x str keyword))

(e/defn Main [_ring-request]
  (e/client
    (binding [dom/node js/document.body]
      (let [max-x 10
            max-y 100
            first-column-key (-> 1 str keyword)]
        (e/server
          (GridSheet. (e/for [j (map inc (range max-y))]
                        (into {}
                              (e/for [i (map inc (range max-x))]
                                [(->k i) (* i j)])))
                      {:id-key    first-column-key
                       :columns   (map (comp ->k inc) (range max-x))
                       :page-size 5}))))))
