(ns datomic-cljs.t-api
  (:require [cemerick.cljs.test :as t]
            [datomic-cljs.api :as d])
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing)]))

(deftest totally-fails
  (is (= 1 1)))
