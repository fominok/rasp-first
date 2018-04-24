(ns first.core
  (:gen-class))

(defn get-lbts [chans]
  ;; Get map of chan names and first messages
  (let [msgs (reduce (fn [acc [n c]] (assoc acc n (-> c deref first))) {} chans)]
    (if (some nil? (vals msgs))  ;; Check if some channel is empty
      nil                        ;; (cannot decide lower time bound)
      (let [cm (->> msgs         ;; Sort messages by ts with channel index
                    (sort-by (comp :ts val)) ;; Get lbts pair of [chan message]
                    first)]
        cm))))

(defn extrude-local-queue [state lbts-msg]
  (let [sorted-queue (sort-by :ts (:queue state)) ;; Sort messages by ts in local q
        message (first (take-while #(<= (:ts %) (:ts lbts-msg))
                                   sorted-queue))] ;; Get first LBTS compiant message
    (when message
      [message (if message
                 ;; Update ts and remove message from q
                 {:queue (vec (rest sorted-queue))
                  :ts (:ts message)}         ;; for a new state
                 state)])))

(defn extrude-from-chan! [chan state lbts-msg]
  (send chan (comp vec rest)) ;; Remove message from chan
  ;; Update ts for new state, inner queue stay unchanged buffer
  [lbts-msg (assoc state :ts (:ts lbts-msg))])

(def ^:dynamic running (atom true)) ;; Set control atom from calling thread
;; (control from test execution)

(defn generic-process [in out process-fn & {:keys [init-state]}]
  ;; Init process fn with zero ts and empty queue
  (doall (for [c (vals out)]
           (send c conj {:ts 0 :event :null-msg})))
  (loop [state (merge {:queue [] :ts 0} init-state)]
    (if @running
      (let [[lbts-chan lbts-msg] (get-lbts in)] ;; Get LBTS channel and message
        (if (some? lbts-msg) ;; Don't do anything until LBTS is known
          (let [[msg new-state] ;; Get next msg to process from queue or chan buffer
                (or (extrude-local-queue state lbts-msg)
                    (extrude-from-chan! (get in lbts-chan)
                                        state lbts-msg))
                ts (inc (:ts new-state))

                ;; Process message through implementation (bank, store, whatever)
                [to-send-lite result-state] (process-fn msg new-state)

                ;; Set timestamp for output messages
                to-send (reduce (fn [acc [k v]]
                                  (assoc acc k (assoc v :ts ts))) {} to-send-lite)
                ;; Send null-messages by default
                mandatory-to-send
                (into {} (map vector (keys out)
                              (repeat {:event :null-msg :ts ts})))]
            (doall
             ;; Override null-messages if needed and send to `out` channels
             (for [[ch m] (merge mandatory-to-send to-send)]
               (send (get out ch) conj m)))
            (recur (merge new-state result-state)))
          (recur state))))))

(defn withdraw [state amount]
  (if (>= (:cash state) amount)
    [{:bank>client {:event :withdraw-success}}
     (update state :cash - amount)]
    [{:bank>client {:event :withdraw-failed}}
     state]))

(defn -main [& args]
  (let [chans {:store>bank (agent [])
               :client>bank (agent [])
               :bank>client (agent [])
               :client>store (agent [])}
        run-ctrl (atom true)
        bank-proc
        (partial
         generic-process
         (select-keys chans [:store>bank
                             :client>bank])
         (select-keys chans [:bank>client])
         (fn [msg state]
           (Thread/sleep 500)
           (case (:event msg)
             :deposit [{} (update state :cash (fnil + 0) (:amount msg))]
             :withdraw (withdraw state (:amount msg))
             :credit [{} (update state :cash (fnil - 0) (:amount msg))]
             [{} state])))
        store-proc
        (partial
         generic-process
         (select-keys chans [:client>store])
         (select-keys chans [:store>bank])
         (fn [msg state]
           (Thread/sleep 500)
           (case (:event msg)
             :buy-in-credit [{} (update state :queue conj
                                        {:ts (+ (:ts msg) 1)
                                         :event :notify-bank})]
             :notify-bank [{:store>bank {:event :credit
                                         :amount 1337}}]
             [{} state])))
        client-proc
        (partial
         generic-process
         (select-keys chans [:bank>client])
         (select-keys chans [:client>store
                             :client>bank])
         (fn [msg state]
           (Thread/sleep 117)
           (case (:event msg)
             :deposit [{:client>bank {:event :deposit :amount 1500}} state]
             :buy-in-credit [{:client>store {:event :buy-in-credit}} state]
             :withdraw [{:client>bank {:event :withdraw :amount 200}} state]
             :withdraw-failed (do (println "withdraw failed") [{} state])
             :withdraw-success (do (println "withdraw success") [{} state])
             [{} state]))
         :init-state {:queue [{:ts 0 :event :deposit}
                              {:ts 3 :event :buy-in-credit}
                              {:ts 5 :event :withdraw}]})]
    (binding [running run-ctrl]

      (future (bank-proc))
      (future (store-proc))
      (future (client-proc))
      (Thread/sleep 20000)
      (swap! run-ctrl not))))
