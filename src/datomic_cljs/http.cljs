(ns datomic-cljs.http
  (:refer-clojure :exclude [get])
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<!]]
            [cljs.reader :as reader])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def js-http (nodejs/require "http"))
(def js-querystring (nodejs/require "querystring"))

(defn encode-query
  "Encodes a map as a urlencoded querystring."
  [m]
  (.stringify js-querystring (clj->js m)))

(defn async-response-body-handler
  "Handles an asyncronous request, writing the a [:success response]
   pair to c-res, handling a streamed body, and closing c-res when
   done."
  [c-res]
  (fn [res]
    (let [c-body (async/chan 10)]
      (.setEncoding res "utf8")
      (.on res "data" #(async/put! c-body %))
      (.on res "end" #(async/close! c-body))
      (async/put! c-res
                  [:success {:c-body c-body
                             :status (.-statusCode res)
                             :res res}]
                  #(async/close! c-res)))))

(defn get
  "Make an asyncronous GET request with the given options, returning
   a core.async channel that will ultimately contain either [:success
   response] or [:error error-object]. In the case of success, the
   response will be a map containing:

     :status, the HTTP status code;
     :res, the Node.js response object;
     :c-body, a core.async channel containing response body chunks
              (strings), which will be closed when streaming is done."
  [options]
  (let [c-res (async/chan)
        js-req (.get js-http
                     (clj->js options)
                     (async-response-body-handler c-res))]
    (.on js-req "error" #(async/put! c-res
                                     [:error %]
                                     (fn [] (async/close! c-res))))
    c-res))

(defn post
  "Make an asyncronous POST request with the given options and data,
   returning a core.async channel that will ultimately contain either
   [:success response] or [:error error-object]. In the case of success,
   the response will be a map containing:

     :status, the HTTP status code;
     :res, the Node.js response object;
     :c-body, a core.async channel containing response body chunks
              (strings), which will be closed when streaming is done."
  ([options]
     (post options ""))
  ([{:keys [headers] :as options} data]
     (let [c-write-data (async/chan 10)
           c-res (async/chan)
           post-data (encode-query data)
           js-req (.request js-http
                            (clj->js
                             (assoc options
                               :method "POST"
                               :headers (assoc (or headers {})
                                          "Content-Length" (.byteLength js/Buffer post-data))))
                            (async-response-body-handler c-res))]
       (.on js-req "error" #(async/put! c-res
                                        [:error %]
                                        (fn [] (async/close! c-res))))
       (.write js-req post-data)
       (.end js-req)
       c-res)))

(defn receive-edn
  "Given a response channel, eventually receive chunked edn on :c-body
   and forward it onto returned core.async channel."
  [c-res]
  (let [c-edn (async/chan)]
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
