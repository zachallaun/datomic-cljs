(defproject com.zachallaun/datomic-cljs "0.0.1-alpha-1"
  :description "Datomic REST client for ClojureScript"
  :url "https://github.com/zachallaun/datomic-cljs"
  :license {:name "MIT" :url "http://opensource.org/licenses/MIT"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2069"]
                 [org.clojure/core.async "0.1.256.0-1bf8cf-alpha"]]

  :profiles {:dev {:plugins [[lein-cljsbuild "1.0.0"]]}}

  :cljsbuild {:builds {:test
                       {:source-paths ["src" "test"]
                        :compiler {:target :nodejs
                                   :output-to "target/test.js"
                                   :optimizations :simple
                                   :pretty-print true}}

                       :browser-test
                       {:source-paths ["src" "test"]
                        :compiler {:output-to "target/browser-test.js"
                                   :optimizations :simple
                                   :pretty-print true}}

                       :example
                       {:source-paths ["src" "examples"]
                        :compiler {:target :nodejs
                                   :output-to "target/example.js"
                                   :optimizations :simple
                                   :pretty-print true}}}})
