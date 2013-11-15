(ns datomic-cljs.util
  (:require [cljs.core.async :as async]))

(defn singleton-chan
  "Returns a closed core.async channel containing only element."
  [element]
  (let [c (async/chan 1)]
    (async/put! c element #(async/close! c))
    c))
