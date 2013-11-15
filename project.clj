(defproject com.zachallaun/datomic-cljs "0.0.1-SNAPSHOT"
  :description "Datomic REST client for ClojureScript"
  :url "https://github.com/zachallaun/datomic-cljs"
  :license {:name "MIT" :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2030"]
                 [org.clojure/core.async "0.1.242.0-44b1e3-alpha"]]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.0-alpha2"]]}}

  :cljsbuild {:builds {:example
                       {:source-paths ["src" "examples"]
                        :compiler {:target :nodejs
                                   :output-to "target/example.js"
                                   :optimizations :simple
                                   :pretty-print true}}
                       :test
                       {:source-paths ["src" "test"]
                        :compiler {:target :nodejs
                                   :output-to "target/test.js"
                                   :optimizations :simple
                                   :pretty-print true}}}})
