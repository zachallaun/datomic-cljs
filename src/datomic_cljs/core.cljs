(ns datomic-cljs.core
  (:require [cljs.core.async :as async]
            [datomic-cljs.http :as http])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn go-go-http! [url]
  (go
    (let [res (<! (http/get url))]
      (loop []
        (let [piece (<! (:c-body res))]
          (when-not (= piece :done)
            (println piece)
            (recur)))))))

(defn go-go-go! []
  (go
    (while true
      (<! (async/timeout 1000))
      (.log js/console "go!"))))

(defn -main [& args]
  (go-go-http! "http://www.google.com"))

(set! *main-cli-fn* -main)
