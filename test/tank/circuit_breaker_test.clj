(ns tank.circuit-breaker-test
  (:require [tank.circuit-breaker :as tank.circuit-breaker]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [assert-check]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [tank.utils :as tank.utils]))

(def a-very-large-time 10000)

(def random-exception (ex-info "A random fail" {}))

(def circuit-breaker-trips-after-failed-attempts
  (prop/for-all [trip-threshold (gen/fmap inc gen/pos-int)]
                (let [cb (tank.circuit-breaker/circuit-breaker trip-threshold a-very-large-time)
                      exceptions-count (atom 0)]
                  (while (not (tank.circuit-breaker/tripped? cb))
                    (try
                      (tank.circuit-breaker/call! cb #(throw random-exception))
                      (catch clojure.lang.ExceptionInfo ex
                        (swap! exceptions-count inc))))
                  (try
                    (tank.circuit-breaker/call! cb #(throw random-exception))
                    (catch Exception ex
                      (t/is (match? {:reason         ::tank.circuit-breaker/tripped
                                     :last-exception (m/equals random-exception)}
                                    (ex-data ex)))))
                  (t/is (= trip-threshold @exceptions-count))
                  (tank.circuit-breaker/shutdown! cb))))

(def circuit-breaker-never-trips-if-recovers
  (prop/for-all [trip-threshold (gen/fmap inc gen/pos-int)]
                (let [cb (tank.circuit-breaker/circuit-breaker trip-threshold 0)
                      exceptions-count (atom 0)]
                  (while (not (tank.circuit-breaker/tripped? cb))
                    (try
                      (tank.circuit-breaker/call! cb #(throw random-exception))
                      (catch clojure.lang.ExceptionInfo ex
                        (swap! exceptions-count inc))))
                  (tank.utils/sleep 1000)
                  (t/is (= ::success (tank.circuit-breaker/call! cb (constantly ::success))))
                  (t/is (= trip-threshold @exceptions-count))
                  (tank.circuit-breaker/shutdown! cb))))

(def circuit-breaker-never-trips-when-threshold-is-not-reached
  (prop/for-all [trip-threshold (gen/fmap inc gen/pos-int)]
                (let [cb (tank.circuit-breaker/circuit-breaker trip-threshold 0)]
                  (t/is (match? (repeat (+ trip-threshold 10) ::success)
                                (for [_ (range (+ trip-threshold 10))]
                                  (tank.circuit-breaker/call! cb (constantly ::success)))))
                  (tank.circuit-breaker/shutdown! cb))))

(t/deftest circuit-breaker
  (tc/quick-check 100 circuit-breaker-trips-after-failed-attempts)
  (tc/quick-check 10 circuit-breaker-never-trips-if-recovers)
  (tc/quick-check 100 circuit-breaker-never-trips-when-threshold-is-not-reached))
