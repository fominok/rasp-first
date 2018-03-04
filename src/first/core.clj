(ns first.core
  (:gen-class))

(defn extrude! [& chans]
  (let [msgs (map (comp peek deref) chans)]
    (if (some nil? msgs) ;; Check if some channel is empty
      nil                ;; (cannot decide lower time bound)
      (let [[i m] (->> msgs          ;; Sort messages by ts with channel index
                      (map-indexed vector)
                      (sort-by (comp :ts second))
                      first)]
        (send (nth chans i) pop) ;; Remove last message from indexed channel
        m)))) ;; Return actual message

(defn -main
  "I don't do a whole lot ... yet."
  [& args])

(def chan1 (agent [{:ts 1} {:ts 2}]))
(def chan2 (agent [{:ts 3} {:ts 4}]))
(def chan3 (agent [{:ts 0}]))
(def chan4 (agent [{:ts 2}]))

(comment

  (extrude! chan1 chan2 chan3 chan4)

  )
