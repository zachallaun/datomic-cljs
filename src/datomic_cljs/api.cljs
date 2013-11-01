(ns datomic-cljs.api
  (:require [datomic-cljs.http :as http]
            [cljs.nodejs :as nodejs]
            [cljs.core.async :as async]
            [cljs.reader :as reader])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def js-url (nodejs/require "url"))

(defprotocol IQueryDatomic
  (execute-query [db query-str inputs]))

(defprotocol ITransactDatomic
  (execute-transaction! [db tx-data-str]))

(defrecord DatomicConnection [hostname port db-alias]
  ITransactDatomic
  (execute-transaction! [_ tx-data-str]
    (http/receive-edn (http/post {:protocol "http:"
                                  :hostname hostname
                                  :port port
                                  :path (str "/data/" db-alias "/")
                                  :headers {"Accept" "application/edn"
                                            "Content-Type" "application/x-www-form-urlencoded"}}
                                 {:tx-data tx-data-str}))))

(defn connect
  "Create an abstract connection to a Datomic REST service by passing
   the following arguments:

     hostname, e.g. localhost;
     port, the port on which the REST service is listening;
     alias, the transactor alias;
     dbname, the name of the database being connected to."
  [hostname port alias dbname]
  (->DatomicConnection hostname port (str alias "/" dbname)))

(defrecord DatomicDB [connection implicit-args]
  IQueryDatomic
  (execute-query [_ q-str inputs]
    (let [args-str (-> implicit-args
                       (cons inputs)
                       (vec)
                       (prn-str))
          encoded-q-str (->> {:query {:q q-str :args args-str}}
                             (clj->js)
                             (.format js-url))
          path (str "/api/query" encoded-q-str)]
      (http/receive-edn (http/get {:protocol "http:"
                                   :hostname (:hostname connection)
                                   :port (:port connection)
                                   :path path
                                   :headers {"Accept" "application/edn"}})))))

(defn db
  "Creates an abstract Datomic value that can be queried."
  [{:keys [db-alias] :as connection}]
  (->DatomicDB connection {:db/alias db-alias}))

(defn as-of
  "Returns the value of the database as of some point t, inclusive.
   t can be a transaction number, transaction ID, or inst."
  [{:keys [connection implicit-args]} t]
  (->DatomicDB connection (assoc implicit-args :as-of t)))

(defn q
  "Execute a query against a database value with inputs. Returns a
   core.async channel that will contain the result of the query, and
   will be closed when the query is complete."
  [query db & inputs]
  (execute-query db (prn-str query) inputs))

(defn transact
  "Submits a transaction to the database for writing. The transaction
   data is sent to the Transactor and, if transactAsync, processed
   asynchronously.

   tx-data is a list of lists, each of which specifies a write
   operation, either an assertion, a retraction or the invocation of
   a data function. Each nested list starts with a keyword identifying
   the operation followed by the arguments for the operation.

   Returns a core.async channel that will contain a map with the
   following keys:

     :db-before, the database value before the transaction;
     :db-after, the database value after the transaction;
     :tx-data, the collection of Datums produced by the transaction;
     :tempids, an argument to resolve-tempids."
  [connection tx-data]
  (execute-transaction! connection (prn-str tx-data)))
