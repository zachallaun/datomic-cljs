(defproject datomic-cljs "0.1.0-SNAPSHOT"
  :description "Datomic REST client for ClojureScript running on Node.js"

  :dependencies [[org.clojure/clojurescript "0.0-1978"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]]

  :profiles {:dev {:plugins [[lein-cljsbuild "0.3.4"]]}}

  :source-paths ["src"]

  :cljsbuild {:builds [{:id "datomic-cljs-dev"
                        :source-paths ["src"]
                        :compiler {:target :nodejs
                                   :output-to "datomic_cljs_dev.js"
                                   :optimizations :simple
                                   :pretty-print true}}]})
