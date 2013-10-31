(ns datomic-cljs.api
  (:require [datomic-cljs.http :as http]
            [cljs.nodejs :as nodejs]
            [cljs.core.async :as async]
            [cljs.reader :as reader])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def js-url (nodejs/require "url"))

(defn connect
  "Create an abstract connection to a Datomic REST service by passing
   the following arguments:

     hostname, e.g. localhost;
     port, the port on which the REST service is listening;
     alias, the transactor alias;
     dbname, the name of the database being connected to."
  [hostname port alias dbname]
  {:hostname hostname
   :port port
   :alias alias
   :dbname dbname})

(defprotocol IQueryDatomic
  (execute-query! [db query-str]))

(defrecord DatomicNow [connection]
  IQueryDatomic
  (execute-query! [{{:keys [hostname port alias dbname]} :connection} q-str]
    (let [c-query (async/chan)
          args-str (prn-str [{:db/alias (str alias "/" dbname)}])
          encoded-q-str (->> {:query {:q q-str :args args-str}}
                             (clj->js)
                             (.format js-url))
          path (str "/api/query" encoded-q-str)
          c-res (http/get {:protocol "http:"
                           :hostname hostname
                           :port port
                           :path path
                           :headers {"Accept" "application/edn"}})]
      (go
        (let [[_ res] (<! c-res)]
          (loop [chunks []]
            (if-let [chunk (<! (:c-body res))]
              (recur (conj chunks chunk))
              (do
                (->> chunks
                     (apply str)
                     (reader/read-string)
                     (>! c-query))
                (async/close! c-query))))))
      c-query)))

(defn db
  "Creates an abstract Datomic value that can be queried."
  [connection]
  (->DatomicNow connection))

(defn q
  "Execute a query against a database value with inputs. A core.async
   channel will be returned which will ultimately contain the result of
   the query, and will be closed when the query is complete."
  [query db] ;; TODO: [query db & inputs]
  (execute-query! db (prn-str query)))




(comment
  (def conn (connect "localhost" 9898 "db" "seattle"))

  (go
    (-> (<! (q '[:find ?n :where [?_ :community/name ?n]] (db conn)))
        (ffirst)
        (println)))

  )