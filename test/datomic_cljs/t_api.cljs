(ns datomic-cljs.t-api
  (:refer-clojure :exclude [test])
  (:require [datomic-cljs.api :as d]
            [datomic-cljs.http :as http]
            [cljs.reader :as reader]
            [cljs.core.async :as async :refer [<! >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [<?]]
                   [datomic-cljs.test-macros :refer [go-test-all test]]))

;; ASSUMPTIONS:
;; 1. you have a Datomic REST service running on localhost:9898
;; 2. it has a transactor alias called 'db'
;; 3. you don't care that a bunch of random test dbs are going
;;    to be created; we can't delete them yet from the REST api
;; 4. if you're running this in the browser, you've set up CORS
;;    permissions; see "Using Datomic REST" in the README

(def test-db-name (str "datomic-cljs-test-" (rand-int 1e8)))
(.log js/console "Starting tests using db" test-db-name)

(def connect-args ["localhost" 9898 "db" test-db-name])

(defn all-the-tests [schema data]
  (go-test-all

    (test "can read and print db/id tagged literals"
      (try
        (reader/read-string "#db/id :foo")
        false
        (catch js/Error e
          true))
      (instance? d/DbId (reader/read-string "#db/id[:db.part/db]"))
      (= "#db/id[:db.part/db]"    (str (reader/read-string "#db/id[:db.part/db]")))
      (= "#db/id[:db.part/db -1]" (str (reader/read-string "#db/id[:db.part/db -1]"))))

    (test "can create a new database"
      (let [conn (<? (apply d/create-database connect-args))]
        (satisfies? d/ITransactDatomic conn)))

    (test "can connect to an existing database"
      (satisfies? d/ITransactDatomic (apply d/connect connect-args)))

    (let [conn (apply d/connect connect-args)
          schema-tx-data (<? (d/transact conn schema))
          data-tx-data (<? (d/transact conn data))

          ;; helper queries
          all-names '[:find ?n :where [?_ :person/name ?n]]]

      (test "can transact scheme and data"
        (and (map? schema-tx-data)
             (map? data-tx-data)))

      (test "can make simple queries"
        (let [result (<? (d/q all-names (d/db conn)))]
          (= 3 (count result))))

      (test "can query with inputs"
        (-> (<? (d/q '[:find ?e :in $ ?n :where [?e :person/name ?n]]
                     (d/db conn) "Frank"))
            ffirst
            number?))

      (test "can limit query results"
        (let [db (-> (d/db conn)
                     (d/limit 1))
              result (<? (d/q all-names db))]
          (= 1 (count result))))

      (test "can offset query results"
        (let [db (-> (d/db conn)
                     (d/offset 1))
              result (<? (d/q all-names db))]
          (= 2 (count result))))

      (test "can compose limit/offset"
        (let [db (-> (d/db conn)
                     (d/offset 1)
                     (d/limit 1))
              result (<? (d/q all-names db))]
          (= 1 (count result))))

      (test "can query with inputs"
        (-> (<? (d/q '[:find ?e :in $ ?n :where [?e :person/name ?n]]
                     (d/db conn) "Frank"))
            ffirst
            number?))

      (test "can access entity maps"
        (let [query '[:find ?e :where [?e :person/name "Caroll"]]
              eid (ffirst (<? (d/q query (d/db conn))))]
          (->> (<? (d/entity (d/db conn) eid))
               :person/friends
               count
               (= 2))))

      (test "can query past database value"
        (let [eid-query '[:find ?e :where [?e :person/name "Becky"]]
              [[eid]] (<? (d/q eid-query (d/db conn)))

              tx-data [[:db/add eid :person/name "Wilma"]]
              {{t :basis-t} :db-before} (<? (d/transact conn tx-data))

              name-query '[:find ?n :in $ ?e :where [?e :person/name ?n]]
              before (ffirst (<? (d/q name-query (d/as-of (d/db conn) t) eid)))
              after (ffirst (<? (d/q name-query (d/db conn) eid)))]
          (and (= before "Becky")
               (= after "Wilma"))))

      (test "can access the basis-t of a db"
        (let [t (<? (d/basis-t (d/db conn)))]
          (and (number? t)
               (= (dec t) (<? (d/basis-t (d/as-of (d/db conn) (dec t))))))))

      (test "can get entity ids from idents and vice versa"
        (let [eid (<? (d/entid (d/db conn) :person/age))]
          (and (number? eid)
               (= :person/age (<? (d/ident (d/db conn) eid)))))
        (= 12345       (<? (d/entid (d/db conn) 12345)))
        (= :person/age (<? (d/ident (d/db conn) :person/age))))

      (test "can access raw index data with datoms"
        (-> (<? (d/datoms (d/limit (d/db conn) 10) :eavt))
            first :e (= 0)))

      (test "can narrow raw index data result by specifying components"
        (let [data (<? (d/datoms (d/db conn) :eavt :e 0))]
          (every? #(= 0 (:e %)) data)))

      (comment
        (test "history")
        (test "index-range"))

      )))

(if http/node-context?
  (let [js-fs (js/require "fs")]
    (set! *main-cli-fn* (fn []
                          (all-the-tests (.readFileSync js-fs "resources/friend_schema.edn" "utf8")
                                         (.readFileSync js-fs "resources/friend_data.edn" "utf8")))))
  (go
    (let [schema (<! (http/body (http/request :get "/resources/friend_schema.edn")))
          data   (<! (http/body (http/request :get "/resources/friend_data.edn")))]
      (all-the-tests schema data))))


(comment

  ;; delete ALL THE DBS!
  (do (require 'datomic.api)
      (doseq [db (map #(str "datomic:free://localhost:4334/" %) dbs)]
        (datomic.api/delete-database db)))

  )
