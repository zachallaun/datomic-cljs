(ns datomic-cljs.api
  (:require [datomic-cljs.http :as http]
            [cljs.nodejs :as nodejs]
            [cljs.core.async :as async]
            [cljs.reader :as reader])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def js-url (nodejs/require "url"))

(defprotocol IQueryDatomic
  (execute-query [db query-str inputs]))

(defrecord DatomicConnection [hostname port db-alias])

(defn connect
  "Create an abstract connection to a Datomic REST service by passing
   the following arguments:

     hostname, e.g. localhost;
     port, the port on which the REST service is listening;
     alias, the transactor alias;
     dbname, the name of the database being connected to."
  [hostname port alias dbname]
  (->DatomicConnection hostname port (str alias "/" dbname)))

(defn- get-edn
  "Make an async request for application/edn."
  [hostname path & {:keys [port protocol]
                    :or {port 80
                         protocol "http:"}}]
  (let [c-edn (async/chan)
        c-res (http/get {:protocol protocol
                         :hostname hostname
                         :port port
                         :path path
                         :headers {"Accept" "application/edn"}})]
    (go
      (let [[_ res] (<! c-res)] ;; TODO handle :error case
        (loop [chunks []]
          (if-let [chunk (<! (:c-body res))]
            (recur (conj chunks chunk))
            (do
              (->> chunks
                   (apply str)
                   (reader/read-string)
                   (>! c-edn))
              (async/close! c-edn))))))
    c-edn))

(defrecord DatomicNow [connection]
  IQueryDatomic
  (execute-query [{{:keys [hostname port db-alias]} :connection} q-str inputs]
    (let [args-str (-> {:db/alias db-alias}
                       (cons inputs)
                       (vec)
                       (prn-str))
          encoded-q-str (->> {:query {:q q-str :args args-str}}
                             (clj->js)
                             (.format js-url))
          path (str "/api/query" encoded-q-str)]
      (get-edn hostname path :port port))))

(defn db
  "Creates an abstract Datomic value that can be queried."
  [connection]
  (->DatomicNow connection))

(defn q
  "Execute a query against a database value with inputs. A core.async
   channel will be returned which will ultimately contain the result of
   the query, and will be closed when the query is complete."
  [query db & inputs]
  (execute-query db (prn-str query) inputs))
