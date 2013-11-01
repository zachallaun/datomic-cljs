# datomic-cljs

What is this; Clojure?

**datomic-cljs** provides a Datomic API similar to that of a Clojure peer, for use in ClojureScript applications targeting Node.js.

```clj
(ns sweet-node-cljs-app
  (:require [datomic-cljs.api :as d]
            [cljs.core.async :as async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [<?]]))

;; Assuming I have a Datomic REST service running locally on port 9898, with
;; a transactor aliased to "db" and a database called "seattle".
(def conn (d/connect "localhost" 9898 "db" "seattle"))

(go
  (->> (d/db conn)
       (<? (d/q '[:find ?n :where [?_ :community/name ?n]]))
       ffirst
       println))
;; prints "Capitol Hill Triangle"
```
