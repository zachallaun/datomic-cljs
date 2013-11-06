(ns datomic-cljs.http
  (:refer-clojure :exclude [get])
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :as async :refer [<!]]
            [cljs.reader :as reader])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [>!x]]))

(def ^:private js-request (nodejs/require "request"))

(defn request
  "Make an async request to the given uri, returning a core.async
   channel eventually containing either an error or a response map
   containing the following:

     :status, the HTTP status code;
     :headers, a map of HTTP response headers;
     :body, the response body;
     :js-resp, the original JS response object.

   opts is the same options map described in the Request docs:
   https://github.com/mikeal/request#requestoptions-callback

   Additionally, opts supports {:edn true} which sets the Accept
   header to application/edn and parses the response body as edn
   if the response content-type is application/edn."
  ([method uri]
     (request method uri {}))
  ([method uri opts]
     (let [c-resp (async/chan 1)
           {edn? :edn headers :headers} opts
           opts (assoc opts
                  :method (case method
                            :get "GET"
                            :post "POST"
                            :put "PUT"
                            :head "HEAD")
                  :headers (if edn?
                             (assoc (or headers {}) :accept "application/edn")
                             headers))]
       (js-request (clj->js (assoc opts :uri uri))
                   (fn [err resp body]
                     (go
                       (>!x c-resp
                            (or err
                                (let [headers (js->clj (.-headers resp)
                                                       :keywordize-keys true)]
                                  {:status (.-statusCode resp)
                                   :headers headers
                                   :body (if (and edn? (re-find #"edn" (:content-type headers)))
                                           (reader/read-string body)
                                           body)
                                   :js-resp resp}))))))
       c-resp)))

(defn body
  "Expects a response channel, and returns a channel that will
   eventually contain either an error or the response body."
  [c-resp]
  (let [c-body (async/chan 1)]
    (go
      (let [resp (<! c-resp)]
        (if (instance? js/Error resp)
          (>!x c-body resp)
          (>!x c-body (:body resp)))))
    c-body))
