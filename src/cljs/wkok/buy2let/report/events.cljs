(ns wkok.buy2let.report.events
  (:require [re-frame.core :as rf]
            [clojure.string :as s]
            [wkok.buy2let.shared :as shared]
            [wkok.buy2let.site.events :as se]
            [wkok.buy2let.crud.subs :as cs]
            [wkok.buy2let.site.subs :as ss]
            [wkok.buy2let.report.subs :as rs]
            [wkok.buy2let.backend.protocol :as bp]
            [wkok.buy2let.backend.impl :as impl]
            [day8.re-frame.http-fx]))

(rf/reg-event-db
 ::view-report
 (fn [db [_ options]]
   (let [{:keys [property-id from-month from-year to-month to-year]} options]
     (-> (assoc-in db [:report :from :month] from-month)
         (assoc-in [:report :from :year] from-year)
         (assoc-in [:report :to :month] to-month)
         (assoc-in [:report :to :year] to-year)
         (assoc-in [:site :active-property] property-id)
         (assoc-in [:site :active-page] :report)
         (assoc-in [:site :heading] "Report")))))

(rf/reg-event-fx
 ::reconcile-nav
 (fn [cofx [_ _]]
   (let [db (:db cofx)
         property (-> (get-in db [:site :active-property]) name)
         from-year (-> (get-in db [:report :from :year]) name)
         from-month (-> (get-in db [:report :from :month]) name)
         to-year (-> (get-in db [:report :to :year]) name)
         to-month (-> (get-in db [:report :to :month]) name)]
     
     (js/window.location.assign (str "#/report/" property "/" from-month "/" from-year "/" to-month "/" to-year)))))

(rf/reg-event-fx
 ::report-set-property
 (fn [cofx [_ p]]
   (let [db (:db cofx)
         property (keyword p)
         months (shared/month-range (get-in db [:report :from])
                                    (get-in db [:report :to]))
         local-db-fx (-> (:db cofx)
                         (assoc-in [:site :active-property] property)
                         (assoc-in [:report :result :months] months)
                         (assoc-in [:site :show-progress] false)
                         shared/calc-totals)
         remote-db-fx (when (not (= "--select--" p))
                        (shared/get-ledger-fx db property months))]
     
     (rf/dispatch [::reconcile-nav])
     (if (empty? (vals remote-db-fx))
       {:db local-db-fx}
       (merge {:db             (assoc-in local-db-fx [:site :show-progress] true)}
              remote-db-fx)))))

(rf/reg-event-fx
 ::report-set-year
 (fn [cofx [_ type y]]
   (let [db (:db cofx)
         property (get-in db [:site :active-property])
         months (case type
                  :from (shared/month-range {:year (keyword y) :month (get-in db [:report :from :month])}
                                            (get-in db [:report :to]))
                  :to (shared/month-range (get-in db [:report :from])
                                          {:year (keyword y) :month (get-in db [:report :to :month])}))
         local-db-fx (-> (:db cofx)
                         (assoc-in [:report type :year] (keyword y))
                         (assoc-in [:report :result :months] months)
                         (assoc-in [:site :show-progress] false)
                         shared/calc-totals)
         remote-db-fx (when (not (= "--select--" property))
                        (shared/get-ledger-fx db property months))]

     (rf/dispatch [::reconcile-nav])
     (if (empty? remote-db-fx)
       {:db local-db-fx}
       (merge {:db             (assoc-in local-db-fx [:site :show-progress] true)}
              remote-db-fx)))))

(rf/reg-event-fx
  ::report-set-month
  (fn [cofx [_ type m]]
    (let [db (:db cofx)
          property (get-in db [:site :active-property])
          months (case type
                   :from (shared/month-range {:year (get-in db [:report :from :year]) :month (keyword m)}
                                             (get-in db [:report :to]))
                   :to (shared/month-range (get-in db [:report :from])
                                           {:year (get-in db [:report :to :year]) :month (keyword m)}))
          local-db-fx (-> (:db cofx)
                    (assoc-in [:report type :month] (keyword m))
                    (assoc-in [:report :result :months] months)
                    (assoc-in [:site :show-progress] false)
                    shared/calc-totals)
          remote-db-fx (when (not (= "--select--" property))
                         (shared/get-ledger-fx db property months))]

     (rf/dispatch [::reconcile-nav])
      (if (empty? remote-db-fx)
        {:db local-db-fx}
        (merge {:db             (assoc-in local-db-fx [:site :show-progress] true)}
               remote-db-fx)))))

(rf/reg-event-db
  ::report-show-invoices-toggle
  (fn [db [_ _]]
    (assoc-in db [:report :show-invoices] 
              (not (get-in db [:report :show-invoices])))))

(defn calc-invoice-path [db property-charges month-year]
  (let [year (-> (:year month-year) name)
        month (-> (:month month-year) name)
        account-id (-> (get-in db [:security :account]) name)
        property-id (-> (get-in db [:site :active-property]) name)]
    (->> (filter #(= true (get-in db [:ledger (get-in db [:site :active-property]) (:year month-year) (:month month-year) :breakdown (:id %) :invoiced]))
                 property-charges)
         (map (fn [charge]
                {:storagePath (str "data/" account-id "/ledger/" property-id "/" year "/" month "/" (-> (:id charge) name))
                 :localPath   (:name charge)
                 :localName   (str year "-" month)})))))

(defn calc-invoice-paths [db property-charges]
  (mapcat #(calc-invoice-path db property-charges %) (get-in db [:report :result :months])))

(defn calc-file-name [db]
  (let [property-id (get-in db [:site :active-property])
        property-name (-> (shared/by-id property-id (-> (:properties db) vals)) :name (s/replace #"[^A-Za-z0-9]+" ""))
        from (str (-> (get-in db [:report :from :year]) name) "-" (-> (get-in db [:report :from :month]) name))
        to (str (-> (get-in db [:report :to :year]) name) "-" (-> (get-in db [:report :to :month]) name))]
    (str "Invoices-" property-name "-From-" from "-To-" to)))



(rf/reg-event-fx
 ::zip-invoices
 [(rf/inject-cofx ::shared/gen-id)]
 (fn [cofx [_ property-charges]]
   (let [db (:db cofx)]
     (merge {:db                (assoc-in db [:site :show-progress] true)}
            (bp/zip-invoices-fx impl/backend
                                {:account-id (get-in db [:security :account])
                                 :uuid (-> (:id cofx) name)
                                 :file-name (calc-file-name db)
                                 :invoice-paths (calc-invoice-paths db property-charges)
                                 :on-success #(do
                                                (rf/dispatch [:download-invoices (:path %)])
                                                (rf/dispatch [::se/show-progress false]))
                                 :on-error #(do
                                              (rf/dispatch [::se/show-progress false])
                                              (rf/dispatch [::se/dialog {:heading "Oops, an error!"
                                                                         :message (str %)}]))})))))


(rf/reg-event-fx
 :download-invoices
 (fn [_ [_ path]]
   (bp/blob-url-fx impl/backend
                   {:path path
                    :on-success #(js/window.open %)
                    :on-error #(rf/dispatch [::se/dialog {:heading "Oops, an error!"
                                                          :message %}])})))



(defn calc-options
  [{:keys [property-id from-month from-year to-month to-year]}]
  (let [properties @(rf/subscribe [::cs/properties])
        active-property (let [ap @(rf/subscribe [::ss/active-property])]
                          (if (= "--select--" ap) nil ap))
        report-from-year @(rf/subscribe [::rs/report-year :from])
        report-from-month @(rf/subscribe [::rs/report-month :from])
        report-to-year @(rf/subscribe [::rs/report-year :to])
        report-to-month @(rf/subscribe [::rs/report-month :to])]
    {:property-id (or (-> property-id keyword)
                      active-property
                      (->> properties first :id)
                      :--select--)
     :from-year (or (-> from-year keyword)
                    report-from-year
                    (:last-year shared/default-cal))
     :from-month (or (-> from-month keyword)
                     report-from-month
                     (:last-month shared/default-cal))
     :to-year (or (-> to-year keyword)
                  report-to-year
                  (:this-year shared/default-cal))
     :to-month (or (-> to-month keyword)
                   report-to-month
                   (:this-month shared/default-cal))}))



