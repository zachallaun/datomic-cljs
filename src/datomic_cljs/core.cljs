(ns datomic-cljs.core
  (:require [cljs.core.async :as async]
            [datomic-cljs.http :as http])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn go-go-http! [options]
  (go
    (let [[_ res] (<! (http/get options))]
      (loop []
        (when-let [piece (<! (:c-body res))]
          (println piece)
          (recur))))))

(defn go-go-go! []
  (go
    (while true
      (<! (async/timeout 1000))
      (.log js/console "go!"))))

(defn -main [& args]
  (go-go-http! {:protocol "http:"
                :hostname "localhost"
                :path "/api/query?q=%5B%3Afind+%3Fe+%3Fv+%3Ain+%24+%3Awhere+%5B%3Fe+%3Adb%2Fdoc+%3Fv%5D%5D&args=%5B%7B%3Adb%2Falias+%22db%2Fseattle%22%7D%5D&offset=&limit="
                :port "9898"
                :headers {"Accept" "application/edn"}}))

(set! *main-cli-fn* -main)
