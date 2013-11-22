(ns datomic-cljs.api
  (:require [datomic-cljs.http :as http]
            [datomic-cljs.util :as util]
            [cljs.core.async :as async :refer [>! <!]]
            [cljs.reader :as reader])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [>!x]]))


;;; Tagged literals
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype DbId [spec]
  Object
  (toString [_]
    (str "#db/id" spec))

  IPrintWithWriter
  (-pr-writer [this writer _]
    (-write writer (str this))))

(defn- read-dbid
  [spec]
  (if (vector? spec)
    (DbId. spec)
    (reader/reader-error nil "db/id literal expects a vector as its representation.")))

(reader/register-tag-parser! "db/id" read-dbid)

(let [!next-id (atom -1000001)]
  (defn tempid
    "Generate a tempid in the specified partition. Values of n from -1
     to -1000000, inclusive, are reserved for user-created tempids."
    ([partition]
       (let [id (DbId. [partition @!next-id])]
         (swap! !next-id dec)
         id))
    ([partition n]
       (DbId. [partition n]))))

;;; Protocols/implementations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defprotocol IDatomicDB
  (-q [db query inputs])
  (-entity [db eid])
  (-datoms [db index components])
  (-basis-t [db]))

(defprotocol IDatomicConnection
  (-conn-url-root [conn])
  (-transact [conn tx-data-str]))

(defrecord DatomicConnection [hostname port db-alias]
  IDatomicConnection
  (-conn-url-root [_]
    (str "http://" hostname ":" port))

  (-transact [conn tx-data-str]
    ;; TODO: return database values as :db-before and :db-after
    (let [path (str (-conn-url-root conn) "/data/" db-alias "/")]
      (http/body
       (http/request :post path {:edn true
                                 :form {:tx-data tx-data-str}})))))

(defrecord DatomicDB [conn implicit-args implicit-qs]
  IDatomicDB
  (-q [_ query inputs]
    (let [args (vec (cons implicit-args inputs))
          path (str (-conn-url-root conn) "/api/query")]
      (http/body
       (http/request :get path {:edn true
                                :qs (assoc implicit-qs
                                      :q (prn-str query)
                                      :args (prn-str args))}))))

  (-datoms [_ index components]
    (let [path (str (-conn-url-root conn) "/data/" (:db/alias implicit-args) "/-/datoms")]
      (http/body
       (http/request :get path {:edn true
                                :qs (assoc (merge implicit-qs components)
                                      :index (name index))}))))

  (-entity [_ eid]
    (let [path (str (-conn-url-root conn) "/data/" (:db/alias implicit-args) "/-/entity")]
      (http/body
       (http/request :get path {:edn true
                                :qs {:e eid
                                     :as-of (:as-of implicit-args)
                                     :since (:since implicit-args)}}))))

  (-basis-t [_]
    (let [c-basis (async/chan 1)]
      (go
        (if (:as-of implicit-args)
          (>!x c-basis (:as-of implicit-args))
          (let [path (str (-conn-url-root conn)
                          "/data/" (:db-alias conn)
                          "/" (or (:as-of implicit-args) "-") "/")
                res (<! (http/request :get path {:edn true}))]
            (if (instance? js/Error res)
              (>!x c-basis res)
              (>!x c-basis (-> res :body :basis-t))))))
      c-basis)))


;;; Mimicking datomic.api
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn connect
  "Create an abstract connection to a Datomic REST service by passing
   the following arguments:

     hostname, e.g. localhost;
     port, the port on which the REST service is listening;
     alias, the transactor alias;
     dbname, the name of the database being connected to."
  [hostname port alias db-name]
  (->DatomicConnection hostname port (str alias "/" db-name)))

(defn create-database
  "Create or connect to a Datomic database via a Datomic REST service
   by passing the following arguments:

     hostname, e.g. localhost;
     port, the port on which the REST service is listening;
     alias, the transactor alias;
     db-name, the name of the database being created.

   Returns a core.async channel eventually containing a database
   connection (as if using datomic-cljs.api/connect), or an error."
  [hostname port alias db-name]
  (let [c-conn (async/chan 1)
        conn (connect hostname port alias db-name)]
    (go
      (let [path (str (-conn-url-root conn) "/data/" alias "/")
            {:keys [status] :as res} (<! (http/request :post path {:edn true
                                                                   :form {:db-name db-name}}))]
        (cond (instance? js/Error res)
                (>!x c-conn res)
              (or (= status 200) (= status 201))
                (>!x c-conn conn)
              :else
                (>!x c-conn (js/Error.
                             (str "Could not create or connect to db: " status))))))
    c-conn))

(defn db
  "Creates an abstract Datomic value that can be queried."
  [{:keys [db-alias] :as conn}]
  (->DatomicDB conn {:db/alias db-alias} {}))

(defn as-of
  "Returns the value of the database as of some point t, inclusive.
   t can be a transaction number, transaction ID, or inst."
  [db t]
  (update-in db [:implicit-args] assoc :as-of t))

(defn since
  "Returns the value of the database since some point t, exclusive.
   t can be a transaction number, transaction ID, or inst."
  [db t]
  (update-in db [:implicit-args] assoc :since t))

(defn history
  "Returns a special database value containing all assertions and
   retractions across time. This database value can be used with
   datoms and index-range calls."
  [db]
  (update-in db [:implicit-qs] assoc :history true))

(defn limit
  "Returns a value of the database that limits the number of results
   from query and datoms to given number n."
  [db n]
  (update-in db [:implicit-qs] assoc :limit n))

(defn offset
  "Returns a value of the database that offsets the results of query
   and datoms by given number n."
  [db n]
  (update-in db [:implicit-qs] assoc :offset n))

(defn as-of-t
  "Returns the as-of point, or nil if none."
  [{{as-of :as-of} :implicit-args}]
  as-of)

(defn since-t
  "Returns the since point, or nil if none."
  [{{since :since} :implicit-args}]
  since)

(defn basis-t
  "Returns a core.async channel eventually containing the t of the
   the most recent transaction available via this db value."
  [db]
  (-basis-t db))

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
  [conn tx-data]
  (let [f (fn [body]
            (if (map? body)
              (assoc body
                :db-before (as-of (db conn) (get-in body [:db-before :basis-t]))
                :db-after  (as-of (db conn) (get-in body [:db-after  :basis-t])))
              body))]
    (async/map f
               [(-transact conn (if (string? tx-data) tx-data (prn-str tx-data)))]
               1)))

(defn q
  "Execute a query against a database value with inputs. Returns a
   core.async channel that will contain the result of the query, and
   will be closed when the query is complete."
  [query db & inputs]
  (-q db query inputs))

(defn- q-ffirst
  [query db & inputs]
  (let [c-res (async/chan 1)]
    (go
      (let [res (<! (apply q query db inputs))]
        (if (instance? js/Error res)
            (>!x c-res res)
            (>!x c-res (ffirst res)))))
    c-res))

(defn entity
  "Returns a map of the entity's attributes for the given id."
  [db eid]
  (-entity db eid))

(defn entid
  "Returns a core.async channel that will contain the entity id
   associated with a symbolic keyword, or the id itself if passed."
  [db ident]
  (if (number? ident)
    (util/singleton-chan ident)
    (q-ffirst '[:find ?e :in $ ?ident :where [?e :db/ident ?ident]] db ident)))

(defn ident
  "Returns a core.async channel that will contain the ident
   associated with an entity id, or the ident itself if passed."
  [db eid]
  (if (keyword? eid)
    (util/singleton-chan eid)
    (q-ffirst '[:find ?ident :in $ ?e :where [?e :db/ident ?ident]] db eid)))

(defn datoms
  "Raw access to the index data, by index. The index must be
   supplied, along with optional leading components."
  [db index & {:as components}]
  (-datoms db index components))

(defn index-range
  "Returns a range of datoms in the given index, starting from start,
   or the beginning if start is nil, and going to end, or through the
   end if end is nil."
  [db index start end]
  (-datoms db index {:start start :end end}))

(defn- squuid-seconds-component
  "Returns the current time rounded to the nearest second."
  []
  (-> (.now js/Date)
      (/ 1000)
      (Math/round)))

;; http://stackoverflow.com/a/2117523
;; 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
;;     var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
;;     return v.toString(16);
;; });
(defn squuid
  "Constructs a semi-sequential UUID. Useful for creating UUIDs that
   don't fragment indexes. Returns a UUID whose most significant 32
   bits are the current time in milliseconds, rounded to the nearest
   second."
  []
  (let [seconds-hex (.toString (squuid-seconds-component) 16)
        trailing (.replace "-xxxx-4xxx-yxxx-xxxxxxxxxxxx" (js/RegExp. "[xy]" "g")
                           (fn [c]
                             (let [r (bit-or (* 16 (Math/random)) 0)
                                   v (if (= c "x") r (bit-or (bit-and r 0x3) 0x8))]
                               (.toString v 16))))]
    (UUID. (str seconds-hex trailing))))

(defn squuid-time-millis
  "Get the time part of a squuid."
  [squuid]
  (-> (.-uuid squuid)
      (.slice 0 8)
      (js/parseInt 16)
      (* 1000)))

;; TODOs
(comment

  ;; from datomic.api

  (defn touch
    [entity])

  (defn filter
    [db pred])

  (defn entid-at
    [db part t-or-date])

  (defn entity-db
    [entity])

  (defn next-t
    [db])

  (defn part
    [eid])

  (defn tx-report-queue
    "queue is a core.async channel"
    [conn])

  (defn resolve-tempid
    [db tempids tempid])

  (defn t->tx
    [t])

  (defn tx->t
    [tx])

  (defn tempid
    ([partition])
    ([partition n]))

  ;; these might not be possible through the REST api
  (defn delete-database
    [...])
  (defn rename-database
    [...])
  (defn request-index
    [...])

  )
