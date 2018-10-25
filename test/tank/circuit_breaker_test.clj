(ns tank.circuit-breaker-test
  (:require [tank.circuit-breaker :as tank.circuit-breaker]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [assert-check]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]))

(def a-very-large-time 1000000000M)

(def random-exception (ex-info "A random fail" {}))

(def circuit-breaker-trips-after-failed-attempts-prop
  (prop/for-all [trip-threshold (gen/fmap inc gen/pos-int)]
                (let [cb (tank.circuit-breaker/circuit-breaker trip-threshold a-very-large-time)
                      exceptions-count (atom 0)]
                  (while (not (tank.circuit-breaker/tripped? cb))
                    (try
                      (tank.circuit-breaker/call! cb #(throw random-exception))
                      (catch clojure.lang.ExceptionInfo ex
                        (swap! exceptions-count inc))))
                  (t/is (= (tank.circuit-breaker/call! cb #(throw random-exception))
                           ::tank.circuit-breaker/tripped))
                  (t/is (= @exceptions-count trip-threshold)))))


(t/deftest circuit-breaker-trips-after-threshold
  (tc/quick-check 100 circuit-breaker-trips-after-failed-attempts-prop))
