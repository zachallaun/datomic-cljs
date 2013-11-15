(ns datomic-cljs.test-macros
  (:refer-clojure :exclude [test]))

(defmacro test
  [s assertion]
  (let [message (str "Test failed: " s "\n" assertion "\n")]
    `(let [val# ~assertion]
       (when-not val#
         (throw (js/Error. ~message))))))

(defmacro go-test-all
  [& tests]
  (let [exs-gen (gensym "exs")
        tests (map (fn [test]
                     `(cljs.core.async/<!
                       (cljs.core.async.macros/go
                         (try ~test
                              (catch js/Error e
                                (swap! ~exs-gen conj e))))))
                   tests)]
    `(cljs.core.async/take!
      (cljs.core.async.macros/go
        (let [~exs-gen (atom [])]
          ~@tests
          (deref ~exs-gen)))
      #(.log js/console (prn-str %)))))
