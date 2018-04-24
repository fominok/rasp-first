(ns first.core-test
  (:require [clojure.test :refer :all]
            [first.core :refer :all]))

(deftest lbts-test
  (let [chans {:chan1 (agent [{:ts 1} {:ts 2}])
               :chan2 (agent [{:ts 3} {:ts 4}])
               :chan3 (agent [])
               :chan4 (agent [{:ts 5}])}]
    (testing "Get LBTS"
      (is
       (= (get-lbts (dissoc chans :chan3))
          [:chan1 {:ts 1}])))
    (testing "Blocked execution"
      (is (= (get-lbts chans)
             nil)))))

(deftest local-queue-test
  (let [state {:ts 0 :queue [{:ts 3} {:ts 4}]}]
    (testing "Low LBTS"
      (let [rs (extrude-local-queue state {:ts 2})]
        (is (nil? rs))))
    (testing "High enough LBTS"
      (let [[message new-state] (extrude-local-queue state {:ts 8})]
        (is (= message {:ts 3}))
        (is (= new-state {:ts 3 :queue [{:ts 4}]}))))))

(deftest chan-extrude-test
  (let [chans {:chan1 (agent [{:ts 1} {:ts 2}])
               :chan2 (agent [{:ts 3} {:ts 4}])
               :chan4 (agent [{:ts 5}])}]
    (testing "Update channel and state"
      (let [[lbts-chan lbts-msg]
            (get-lbts chans)
            state {:ts 0 :queue []}
            [msg new-state] (extrude-from-chan! (get chans lbts-chan) state lbts-msg)]
        (is (= lbts-msg msg))
        (is (= new-state (assoc state :ts 1)))
        (is (= @(:chan1 chans) [{:ts 2}]))))))

(deftest generic-process-test
  (let [chans {:1>3 (agent [])
               :2>3 (agent [])
               :3>1 (agent [])
               :3>2 (agent [])}
        run-ctrl (atom true)]
    (binding [running run-ctrl]
      (future (generic-process
               (select-keys chans [:1>3 :2>3])
               (select-keys chans [:3>1 :3>2])
               (fn [msg state]
                 (case (:event msg)
                   :deposit [{:3>1 {:event :notify :text "Deposit successful"}}
                             (update state :cash (fnil + 0) (:amount msg))]
                   [{} state])))))
    (send (:1>3 chans) conj {:event :deposit :amount 1337 :ts 1})
    (send (:2>3 chans) conj {:event :null-msg :ts 2})
    (Thread/sleep 100)
    (swap! run-ctrl not)
    (is (= @(:3>1 chans) [{:event :notify :text "Deposit successful" :ts 1}]))
    eprintln "DEAD"is (= @(:3>2 chans) [{:event :null-msg :ts 1}]))))
