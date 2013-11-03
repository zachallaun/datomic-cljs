(ns datomic-cljs.macros)

(defmacro >!x
  "The same as (do (>! c val) (close! c))"
  [c val]
  `(let [c# ~c]
     (cljs.core.async/>! c# ~val)
     (cljs.core.async/close! c#)))

(defmacro <?
  "Takes a value from a core.async channel, throwing the value if it
  is a js/Error."
  [c]
  `(let [val# (cljs.core.async/<! ~c)]
     (if (instance? js/Error val#)
       (throw val#)
       val#)))
