(ns workshop.core
  (:require [helix.core :as hx :refer [$ <> defnc]]
            [helix.dom :as d]
            [helix.hooks :as hooks]
            ["react" :as r]
            ["react-dom/server" :as rds]
            [devcards.core :as dc :include-macros true]))


(defnc props-test
  [props]
  (d/div
   (d/div "props test")
   (for [[k v] props]
     (d/div
      {:key k}
      (d/div
       {:style {:color "red"}}
       "key: " (str k))
      (d/div "val: " (pr-str v))))))


(dc/defcard props ($ props-test {:class :class
                                 :style :style
                                 :for :for
                                 :kebab-case :kebab-case
                                 :camelCase :camelCase}))


(defnc subcomponent
  [{:keys [name] :as props}]
  (d/div name))


(defnc state-test
  []
  (let [[{:keys [name]} set-state] (hooks/use-state {:name "asdf"})]
    (d/div
     (d/input {:value name
               :on-change #(set-state assoc :name (.. % -target -value))})
     (subcomponent {:name name}))))


(dc/defcard use-state
  ($ state-test))


(defnc display-range
  [{:keys [end color] :as props}]
  (for [n (range end)]
    (d/div {:key n
            :style {:width "10px" :height "10px" :display "inline-block"
                    :background (or color "green") :margin "auto 2px"}})))


(defnc effect-test
  []
  (let [[count set-count] (hooks/use-state 0)
        renders (hooks/use-ref 1)
        [fx-state set-fx-state] (hooks/use-state {:every 0
                                                  :every/auto 0
                                                  :every-third 0
                                                  :every-third/auto 0})
        threes (quot count 3)]
    (hooks/use-effect
     (set! (.-current renders) (inc (.-current renders))))
    (hooks/use-effect
     [count]
     (set-fx-state assoc :every count))
    (hooks/use-effect
     [threes]
     (set-fx-state assoc :every-third threes))
    (hooks/use-effect
     :auto-deps
     (set-fx-state assoc :every/auto count))
    (hooks/use-effect
     :auto-deps
     (set-fx-state assoc :every-third/auto threes))

    (d/div
     (d/div "Count: " count)
     (d/button {:on-click #(set-count inc)} "inc")
     (d/div
      (d/div "renders:")
      ($ display-range {:end (.-current renders) :color "red"}))
     (for [[k v] fx-state]
       (d/div {:key (str k)}
        (d/div (str k))
        ($ display-range {:end v}))))))


(dc/defcard use-effect
  ($ effect-test))


(defnc lazy-test
  [{:keys [begin end]
    :or {begin 0}}]
  (<>
   (d/div (str "numbers " (or begin 0) "-" (dec end) ":"))
   (d/ul
    (for [n (range begin end)]
      (d/li {:key n} n)))
   (d/div "ur welcome")))


(dc/defcard lazy
  ($ lazy-test {:end 6}))


(defnc dynamic-test
  []
  (let [div "div"
        props {:style {:color "blue"}}
        children '("foo" "bar")]
    ($ div props children "baz")))

(dc/defcard dynamic
  ($ dynamic-test))


(defnc children-test
  [{:keys [children] :as props}]
  (d/div {:style {:display "flex"
                  :justify-content "space-between"}}
   children))

(dc/defcard children
  ($ children-test (d/div "foo") (d/div "bar")))


(defnc use-memo-component
  [{:keys [qworp]}]
  (let [bar "bar"
        foobar (macroexpand '(hooks/use-memo :auto-deps (fn [] (str qworp bar goog/DEBUG))))]
    (pr-str foobar)))


(dc/defcard use-memo
  ($ use-memo-component {:qworp "foo"}))


;;
;; -- Benchmarking
;;

(defnc helix-children-benchmark
  [{:keys [children] :as props}]
  (d/div {:style {:display "flex"
                   :justify-content "space-between"}}
          children))

(defnc helix-children-interpret-props-benchmark
  [{:keys [children] :as props}]
  (let [props {:style {:display "flex"
                       :justify-content "space-between"}}]
  (d/div props
         children)))


(defn react-children-benchmark
  [props]
  (r/createElement
   "div"
   #js {:style
        #js {:display "flex"
             :justifyContent "space-between"}}
   (.-children ^js props)))


(defnc simple-benchmark-component []
  (let [[re-render set-state] (hooks/use-state 0)
        force-render #(set-state inc)
        [iterations set-iterations] (hooks/use-state 10000)
        helix-time (hooks/use-memo [re-render]
                                   (with-out-str
                                     (simple-benchmark
                                      []
                                      (rds/renderToString
                                       ($ helix-children-benchmark
                                        {:foo "bar"}
                                        (d/div {:style {:background-color "green"}} "foo")
                                        (d/div "bar")))
                                      iterations)))

        helix-interpret-props-time (hooks/use-memo [re-render]
                                                   (with-out-str
                                                     (simple-benchmark
                                                      []
                                                      (rds/renderToString
                                                       ($ helix-children-interpret-props-benchmark
                                                        {:foo "bar"}
                                                        (d/div {:style {:background-color "green"}} "foo")
                                                        (d/div "bar")))
                                                      iterations)))

        react-time (hooks/use-memo [re-render]
                                   (with-out-str
                                     (simple-benchmark
                                      []
                                      (rds/renderToString
                                       (r/createElement
                                        react-children-benchmark
                                        #js {:foo "bar"}
                                        (r/createElement "div" #js {:style #js {:backgroundColor "green"}} "foo")
                                        (r/createElement "div" nil "bar")))
                                      iterations)))]
    (<>
     (d/div
      (d/input {:value iterations
                :on-change #(set-iterations
                             (-> %
                                 .-target
                                 .-value
                                 (js/parseInt 10)))
                :type "number"})
      (d/button {:on-click force-render} "Re-run"))
     (d/div
      {:style {:padding "5px"}}
      (d/code
       helix-time))
     (d/div
      {:style {:padding "5px"}}
      (d/code
       helix-interpret-props-time))
     (d/div
      {:style {:padding "5px"}}
      (d/code
       react-time)))))

#_(dc/defcard simple-benchmark
  ($ simple-benchmark-component))
