(ns wkok.buy2let.report.views
  (:require [re-frame.core :as rf]
            [wkok.buy2let.report.events :as re]
            [wkok.buy2let.crud.subs :as cs]
            [wkok.buy2let.report.subs :as rs]
            [wkok.buy2let.site.subs :as ss]
            [wkok.buy2let.site.events :as se]
            [wkok.buy2let.shared :as shared]
            [tick.alpha.api :as t]
            [clojure.string :as s]
            [reagent-material-ui.icons.cloud-download :refer [cloud-download]]
            [reagent-material-ui.icons.edit :refer [edit]]
            [reagent-material-ui.core.paper :refer [paper]]
            [reagent-material-ui.core.grid :refer [grid]]
            [reagent-material-ui.core.icon-button :refer [icon-button]]
            [reagent-material-ui.core.tooltip :refer [tooltip]]
            [reagent-material-ui.core.table :refer [table]]
            [reagent-material-ui.core.table-container :refer [table-container]]
            [reagent-material-ui.core.table-head :refer [table-head]]
            [reagent-material-ui.core.table-body :refer [table-body]]
            [reagent-material-ui.core.table-row :refer [table-row]]
            [reagent-material-ui.core.table-cell :refer [table-cell]]
            [reagent-material-ui.pickers.date-picker :refer [date-picker]]))


(defn format-amount [ledger path]
  (->> (get-in ledger path) shared/format-money))

(defn report-header [months props]
  (let [class-table-header (get-in props [:classes :table-header])]
    [table-row
     [table-cell {:class class-table-header} "Charge"]
     (for [m months]
       ^{:key m}
       [table-cell {:class class-table-header
                    :align :right} (str (shared/format-month (:date m)) " " (t/year (:date m)))])
     [table-cell {:class class-table-header
                  :align :right} "Total"]]))

(defn report-charge-col 
  [m charge {:keys [ledger report property]}]
  [table-cell {:align :right}
   [:div.report-charge-col
    [:div
     (format-amount ledger [(:year m) (:month m) :breakdown (:id charge) :amount])]
    (when (= true (:show-invoices report))
      [:div.report-charge-col-icons
       [:div.report-charge-col-icon
        (let [note (get-in ledger [(:year m) (:month m) :breakdown (:id charge) :note])]
          (if (not (s/blank? note))
            [:i.far.fa-sticky-note {:on-click #(rf/dispatch [::se/dialog {:heading "Note" :message note}])}]
            [:label]))]
       [:div.report-charge-col-icon
        (if (get-in ledger [(:year m) (:month m) :breakdown (:id charge) :invoiced])
          [:i.fas.fa-paperclip {:on-click #(rf/dispatch [::shared/view-invoice
                                                         (:id property)
                                                         (:year m)
                                                         (:month m)
                                                         charge])}]
          [:label])]])]])

(defn report-profit-col
  [m {:keys [ledger props]}]
  (let [profit (shared/calc-profit-property ledger m)
        class-table-header (get-in props [:classes :table-header])]
    (if (neg? profit)
      [table-cell {:align :right
                   :class class-table-header} (shared/format-money profit)]
      (if (pos? profit)
        [table-cell {:align :right
                     :class class-table-header}
         (shared/format-money profit)]
        [table-cell {:align :right
                     :class class-table-header}
         (shared/format-money profit)]))))

(defn report-owed-col 
  [m {:keys [ledger props]}]
  (let [owed (-> (get-in ledger [(:year m) (:month m) :totals :agent-current]) shared/to-money)
        class-table-header (get-in props [:classes :table-header])]
    (if (neg? owed)
      [table-cell {:align :right
                   :class class-table-header}
       (shared/format-money owed)]
      (if (pos? owed)
        [table-cell {:align :right
                     :class class-table-header}
         (shared/format-money owed)]
        [table-cell {:align :right
                     :class class-table-header}
         (shared/format-money owed)]))))


(defn report-cash-col 
  [m {:keys [ledger props]}]
  (let [cash (-> (get-in ledger [(:year m) (:month m) :totals :owner]) shared/to-money)
        class-table-header (get-in props [:classes :table-header])]
    (if (neg? cash)
      [table-cell {:align :right
                   :class class-table-header}
       (shared/format-money cash)]
      (if (pos? cash)
        [table-cell {:align :right
                     :class class-table-header}
         (shared/format-money cash)]
        [table-cell {:align :right
                     :class class-table-header}
         (shared/format-money cash)]))))

(defn report-edit-col 
  [m {:keys [property]}]
  [table-cell {:align :right}
   [tooltip {:title "Edit"}
    [icon-button {:on-click #(js/window.location.assign (str "#/reconcile/" (-> property :id name)
                                                                      "/" (-> (:month m) name)
                                                                      "/" (-> (:year m) name)
                                                                      "/edit"))}
    [edit {:color :primary}]]]])

(defn report-charge-row 
  [charge months {:keys [report props] :as options}]
  [table-row
   [table-cell (:name charge)]
   (for [m months]
     ^{:key m}
     [report-charge-col m charge options])
   [table-cell {:align :right
                :class (get-in props [:classes :table-header])}
    (-> (get-in report [:result :totals :breakdown (:id charge) :amount])
        shared/format-money)]])

(defn report-profit-row-total 
  [{:keys [report props]}]
  (let [profit (+ (get-in report [:result :totals :accounting :owner])
                  (get-in report [:result :totals :accounting :agent-current]))]
    (if (neg? profit)
      [table-cell {:align :right
                   :class (get-in props [:classes :table-header])} 
       (shared/format-money profit)]
      [table-cell {:align :right
                   :class (get-in props [:classes :table-header])} 
       (shared/format-money profit)])))

(defn report-profit-row 
  [months {:keys [props] :as options}]
  [table-row
   [table-cell {:class (get-in props [:classes :table-header])} 
    [:strong [:label.report-view-amount-pos "Profit"] " / " [:label.report-view-amount-neg "(Loss)"]]]
   (for [m months]
     ^{:key m}
     [report-profit-col m options])
   [report-profit-row-total options]])

(defn report-owed-row 
  [months options]
  [table-row
   [table-cell
    [:strong [:label.report-view-amount-owe "Owed"] " / " [:label.report-view-amount-neg "(Owing)"]]]
   (for [m months]
     ^{:key m}
     [report-owed-col m options])
   [table-cell {:align :right} "-"]])

(defn report-cash-row 
  [months options]
  [table-row
   [table-cell [:strong "Cash Flow"]]
   (for [m months]
     ^{:key m}
     [report-cash-col m options])
   [table-cell {:align :right} "-"]])

(defn report-edit-row 
  [months options]
  [table-row
   [table-cell]
   (for [m months]
     ^{:key m}
     [report-edit-col m options])
   [table-cell]])

(defn zip-invoices-confirm [property-charges]
  (rf/dispatch [::se/dialog {:heading "Download?"
                             :message "This will download all invoices for the selected period & might take a while"
                             :buttons {:left  {:text     "Yes"
                                               :on-click #(rf/dispatch [::re/zip-invoices property-charges])}
                                       :right {:text     "Cancel"}}}]))

(defn view-report 
  [{:keys [property-charges report props] :as options}]
  (rf/dispatch [:set-fab-actions {:left-1 {:fn   #(zip-invoices-confirm property-charges)
                                           :icon [cloud-download]
                                           :title "Download"}}])
  (let [months (-> (get-in report [:result :months]) reverse)]
    [paper
     [grid {:container true
            :direction :column}
      [grid {:item true :xs 12}
       [table-container
        [table {:size :small}
         [table-head
          [report-header months props]]
         [table-body
          (for [charge property-charges]
            ^{:key (:id charge)}
            [report-charge-row charge months options])
          [report-profit-row months options]
          [report-owed-row months options]
          [report-cash-row months options]
          [report-edit-row months options]]]]]
      [grid {:container true
             :item true
             :class (get-in props [:classes :paper])
             :justify :flex-end}
       (if (= true (:show-invoices report))
         (shared/anchor #(rf/dispatch [::re/report-set-show-invoices false])
                        "Hide invoices / notes")
         (shared/anchor #(rf/dispatch [::re/report-set-show-invoices true])
                        "Show invoices / notes"))]]]))

(defn criteria
  [{:keys [properties props]}]
  [paper {:class (get-in props [:classes :paper])}
   [grid {:container true
          :direction :column
          :spacing 2}
    [grid {:item true}
     (shared/select-property properties
                             #(rf/dispatch [::re/report-set-property (.. % -target -value)])
                             @(rf/subscribe [::ss/active-property])
                             "--select--" "Property")]
    [grid {:item true}
     [grid {:container true
            :direction :row
            :spacing 3}
      [grid {:item true
             :xs 6}
       [date-picker {:variant :inline
                     :open-to :year
                     :views [:year :month]
                     :label "From"
                     :value (.parse shared/date-utils (str (name @(rf/subscribe [::rs/report-year :from])) "/"
                                                           (name @(rf/subscribe [::rs/report-month :from]))) "yyyy/MM")
                     :on-change #(do (rf/dispatch [::re/report-set-month :from (->> % (.getMonth shared/date-utils) inc str keyword)])
                                     (rf/dispatch [::re/report-set-year :from (->> % (.getYear shared/date-utils) str keyword)]))
                     :auto-ok true}]]
      [grid {:item true
             :xs 6}
       [date-picker {:variant :inline
                     :open-to :year
                     :views [:year :month]
                     :label "To"
                     :value (.parse shared/date-utils (str (name @(rf/subscribe [::rs/report-year :to])) "/"
                                                           (name @(rf/subscribe [::rs/report-month :to]))) "yyyy/MM")
                     :on-change #(do (rf/dispatch [::re/report-set-month :to (->> % (.getMonth shared/date-utils) inc str keyword)])
                                     (rf/dispatch [::re/report-set-year :to (->> % (.getYear shared/date-utils) str keyword)]))
                     :auto-ok true}]]]]]])

(defn view-panel 
  [{:keys [property] :as options}]
  [grid {:container true
         :direction :column
         :spacing 2}
   [grid {:item true}
    [criteria options]]
   [grid {:item true :xs 12}
    (if (not (nil? property))
      [view-report options]
      (rf/dispatch [:set-fab-actions nil]))]])


(defn report [props]
  (let [properties @(rf/subscribe [::cs/properties])
        charges @(rf/subscribe [::cs/charges])
        report @(rf/subscribe [::rs/report])
        property-id @(rf/subscribe [::ss/active-property])
        property (shared/by-id property-id properties)
        property-charges (->> (map #(shared/by-id % charges) (keys (:charges property)))
                              (filter #(not (:hidden %)))
                              (sort-by :name))
        ledger @(rf/subscribe [:ledger-property (:id property)])]

    (rf/dispatch [::re/report-set-property property-id])    ;Refresh as property might have been changed from another view
    [view-panel {:property property 
                 :report report 
                 :ledger ledger 
                 :properties properties 
                 :property-charges property-charges
                 :props props}]))
