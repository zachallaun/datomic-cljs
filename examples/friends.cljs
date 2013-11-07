(ns datomic-cljs.examples.friends
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :as async :refer [<! >!]]
            [cljs.nodejs :as nodejs])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [<?]]))

(def js-fs (nodejs/require "fs"))

(def friend-schema (.readFileSync js-fs "resources/friend_schema.edn" "utf8"))
(def friend-data (.readFileSync js-fs "resources/friend_data.edn" "utf8"))

(defn -main [& args]
  (go

    ;; Create and connect to a new Datomic database.
    (let [conn (<? (d/create-database "localhost" 9898 "db" "friends"))]

      ;; Transact our schema and seed data.
      (<? (d/transact conn friend-schema))
      (<? (d/transact conn friend-data))

      ;; Find Caroll's entity id.
      (let [caroll-eid (ffirst (<? (d/q '[:find ?e :where [?e :person/name "Caroll"]]
                                        (d/db conn))))]

        ;; Using her entity id, get her entity map, and use that to
        ;; see all her friends.
        (println (-> (<? (d/entity (d/db conn) caroll-eid))
                     :person/friends)))

      ;; Frank wants to start going by Franky.
      (let [frank-eid (ffirst (<? (d/q '[:find ?e :where [?e :person/name "Frank"]]
                                       (d/db conn))))

            tx-data (<? (d/transact conn [[:db/add frank-eid :person/name "Franky"]]))

            {{before-t :basis-t} :db-before} tx-data]

        ;; But we know he used to just be Frank.
        (println (<? (d/q '[:find ?n :in $ ?e :where [?e :person/name ?n]]
                          (d/as-of (d/db conn) before-t)
                          frank-eid)))))))

(set! *main-cli-fn* -main)
