(defproject first "0.1.0-SNAPSHOT"
  :description "Distributed systems: first lab"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/core.async "0.4.474"]]
  :main ^:skip-aot first.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
