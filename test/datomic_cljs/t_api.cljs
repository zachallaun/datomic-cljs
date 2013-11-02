(ns datomic-cljs.t-api
  (:refer-clojure :exclude [test])
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :as async :refer [<! >!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.test-macros :refer [go-test-all test]]))

;; ASSUMPTIONS:
;; 1. you have a Datomic REST service running on localhost:9898
;; 2. it has a transactor alias called 'db'
;; 3. you don't care that a bunch of random test dbs are going
;;    to be created; we can't delete them yet from the REST api

(def test-db-name (str "datomic-cljs-test-" (rand-int 1e8)))

(defn all-the-tests []
  (go-test-all
    (test "Can create a new database"
      (let [conn (<! (d/create-database "localhost" 9898 "db" test-db-name))]
        (satisfies? d/ITransactDatomic conn)))

    (test "Can connect to an existing database"
      (let [conn (d/connect "localhost" 9898 "db" test-db-name)]
        (satisfies? d/ITransactDatomic conn)))))

(set! *main-cli-fn* all-the-tests)
