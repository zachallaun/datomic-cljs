(ns datomic-cljs.http
  (:refer-clojure :exclude [get])
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as async]))

(def js-http (nodejs/require "http"))

(defn get
  "Make an asyncronous GET request for the given options, returning a
   core.async channel that will ultimately contain either [:success
   response] or [:error error-object]. In the case of success, the
   response will be a map containing:

     :status, the HTTP status code;
     :res, the Node.js response object;
     :c-body, a core.async channel containing streamed response body
              chunks (strings), which will be closed when streaming
              is done."
  [options]
  (let [c-res (async/chan)
        js-res (.get js-http (clj->js options)
                     (fn [res]
                       (let [c-body (async/chan 10)]
                         (.setEncoding res "utf8")
                         (.on res "data" #(async/put! c-body %))
                         (.on res "end" #(async/close! c-body))
                         (async/put! c-res
                                     [:success {:c-body c-body
                                                :status (.-statusCode res)
                                                :res res}]
                                     #(async/close! c-res)))))]
    (.on js-res "error" #(async/put! c-res
                                     [:error %]
                                     (fn [] (async/close! c-res))))
    c-res))
