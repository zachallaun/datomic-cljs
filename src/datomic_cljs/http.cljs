(ns datomic-cljs.http
  (:refer-clojure :exclude [get])
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as async]))

(def js-http (nodejs/require "http"))

(defn get
  [url]
  (let [c-res (async/chan)]
    (.get js-http url
          (fn [res]
            (let [c-body (async/chan 10)]
              (.setEncoding res "utf8")
              (.on res "data" #(async/put! c-body %))
              (.on res "end" #(async/put! c-body :done))
              (async/put! c-res
                          {:c-body c-body
                           :status (.-statusCode res)
                           :res res}))))
    c-res))
