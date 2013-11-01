(ns datomic-cljs.core
  (:require [cljs.core.async :as async :refer [<! >!]]
            [datomic-cljs.http :as http]
            [datomic-cljs.api :as d])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn go-go-post! [options data]
  (go
    (let [[_ res] (<! (http/post options data))]
      (loop []
        (when-let [piece (<! (:c-body res))]
          (println piece)
          (recur))))))

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

  #_(let [conn (d/connect "localhost" 9898 "db" "seattle")]
    (go
      (println
       (ffirst
        (<! (d/q '[:find ?eid :in $ ?n :where [?eid :community/name ?n]]
                 (d/db conn) "Bar community!"))))))

  (go-go-post! {:protocol "http:"
                :hostname "localhost"
                :port 9898
                :path "/data/db/seattle/"
                :headers {"Accept" "application/edn"
                          "Content-Type" "application/x-www-form-urlencoded"}}
               {:tx-data "[[:db/add 17592186045489 :community/name \"Baz community!\"]]"})


)

(set! *main-cli-fn* -main)


(comment

  (let [conn (d/connect "localhost" 9898 "db" "seattle")]
    (go
      (println
       (ffirst
        (<! (d/q '[:find ?eid :in $ ?n :where [?eid :community/name ?n]]
                 (d/db conn) "Capitol Hill Triangle"))))))

  #_(go-go-http! {:protocol "http:"
                  :hostname "localhost"
                  :path "/api/query?q=%5B%3Afind%20%3Fe%20%3Fv%20%3Ain%20%24%20%3Awhere%20%5B%3Fe%20%3Adb%2Fdoc%20%3Fv%5D%5D&args=%5B%7B%3Adb%2Falias%20%22db%2Fseattle%22%7D%5D"
                :port "9898"
                :headers {"Accept" "application/edn"}})

  )
