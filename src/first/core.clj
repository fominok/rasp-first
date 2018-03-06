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
    [message (if message
               {:queue (rest sorted-queue) ;; Update ts and remove message from q
                :ts (:ts message)}         ;; for a new state
               state)]))

(defn extrude-from-chan! [chan state lbts-msg]
  (send chan rest) ;; Remove message from chan

  ;; Update ts for new state, inner queue stay unchanged buffer
  [lbts-msg (assoc state :ts (:ts lbts-msg))])

(defn generic-process [in out process-fn]
  (loop [state {:queue [] :ts 0}] ;; Init process fn with zero ts and empty queue
    (let [[lbts-chan lbts-msg] (get-lbts in)] ;; Get LBTS channel and message
      (if (some? lbts-msg) ;; Don't do anything until LBTS is known
        (let [[msg new-state] ;; Get next msg to process from queue or chan buffer
              (or (extrude-local-queue state lbts-msg)
                  (extrude-from-chan! (get in lbts-chan)
                                      state lbts-msg))
              [to-send result-state] (process-fn msg new-state)]
          (doall
           (for [[ch m] to-send]
             (send (get out ch) m)))
          (recur (merge new-state result-state)))
        (recur state)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args])
