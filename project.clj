(defproject datomic-cljs "0.1.0-SNAPSHOT"
  :description "Datomic REST client for ClojureScript running on Node.js"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1978"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.0-SNAPSHOT"]]}}

  :cljsbuild {:builds {:example
                       {:source-paths ["src" "examples"]
                        :compiler {:target :nodejs
                                   :output-to "target/datomic_cljs_example.js"
                                   :optimizations :simple
                                   :pretty-print true}}
                       :test
                       {:source-paths ["src" "test"]
                        :compiler {:target :nodejs
                                   :output-to "target/datomic_cljs_test.js"
                                   :optimizations :simple
                                   :pretty-print true}}}})
