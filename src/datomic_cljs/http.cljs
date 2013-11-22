(ns datomic-cljs.http
  (:require [cljs.core.async :as async :refer [<!]]
            [cljs.reader :as reader]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [datomic-cljs.macros :refer [>!x]]))

(def node-context?
  (and (exists? js/exports)
       (not= js/exports (this-as context (.-exports context)))))

;;; browser shims
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- urlencode-kvs [kvs]
  (->> (for [[k v] kvs
             :when (not (nil? v))]
         (str (js/encodeURIComponent (name k))
              "="
              (js/encodeURIComponent v)))
       (str/join "&")))

(defn- urlencode-qs [qs-kvs]
  (str "?" (urlencode-kvs qs-kvs)))

(defn- parse-headers [header-str]
  (into {} (for [header (str/split-lines header-str)
                 :let [[k v] (str/split header #":")
                       [k v] [(str/trim k) (str/trim v)]]]
             [(keyword (str/lower-case k)) v])))

(defn- browser-request [{:keys [method uri headers qs form]
                         :or {method "GET" headers {}}
                         :as opts}
                        callback]
  (let [js-req (js/XMLHttpRequest.)
        query-str (if qs (urlencode-qs qs) "")
        url (str uri query-str)
        headers (if form
                  (assoc headers "Content-Type" "application/x-www-form-urlencoded")
                  headers)]

    (.addEventListener js-req "load"
                       (fn []
                         ;; emulate node response... sort of
                         (set! (.-headers js-req)
                               (parse-headers (.getAllResponseHeaders js-req)))
                         (set! (.-statusCode js-req) (.-status js-req))
                         (callback nil js-req (.-response js-req))))

    ;; The REST server 'sploding probably results in a CORS error on our end
    (.addEventListener js-req "error"
                       (fn [e]
                         (.preventDefault e)
                         (callback e js-req nil)))

    (.open js-req method url true)

    (doseq [[k v] (or headers {})]
      (.setRequestHeader js-req (name k) v))

    (set! (.-responseType js-req) "text")

    (if form
      (.send js-req (urlencode-kvs form))
      (.send js-req))))


(def ^:private js-request nil)
(if node-context?
  (set! js-request (let [req (try (js/require "request")
                                  (catch js/Error e
                                    (.log js/console "Error: Cannot find module 'request'.\nSee datomic-cljs README for installation and dependency notes.")
                                    (.exit js/process 1)))]
                     (fn [opts cb]
                       (req (clj->js opts) cb))))
  (set! js-request browser-request))

(defn- response-handler [c-resp edn?]
  (fn [err resp body]
    (async/put!
     c-resp
     (or err
         (let [headers (js->clj (.-headers resp)
                                :keywordize-keys true)]
           {:status (.-statusCode resp)
            :headers headers
            :body (if (and edn? (re-find #"edn" (:content-type headers)))
                    (reader/read-string body)
                    body)
            :js-resp resp}))
     #(async/close! c-resp))))

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
       (js-request (assoc opts :uri uri)
                   (response-handler c-resp edn?))
       c-resp)))

(defn body
  "Expects a response channel, and returns a channel that will
   eventually contain either the response body (on successful status
   code) or an error (if the request fails or an unsuccessful status
   code was returned)."
  [c-resp]
  (let [c-body (async/chan 1)]
    (go
      (let [resp (<! c-resp)]
        (>!x c-body
             (cond (instance? js/Error resp)
                   resp

                   (not (<= 200 (:status resp) 299))
                   (js/Error. (str "Unsuccessful HTTP status code returned: " (:status resp)))

                   :else
                   (:body resp)))))
    c-body))
