(ns tank.circuit-breaker-test
  (:require [tank.circuit-breaker :as tank.circuit-breaker]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]
            [tank.utils :as tank.utils])
  (:import [clojure.lang ExceptionInfo]))

(def a-very-large-time 10000)

(def random-exception (ex-info "A random fail" {}))
(def failing-fn-gen (gen/elements [(fn [] (throw random-exception)) (constantly ::fail)]))
(def trip-threshold-gen (gen/fmap inc gen/pos-int))

(def successful-fn (constantly ::success))


(defspec circuit-breaker-trips-after-failed-attempts
  (prop/for-all [trip-threshold trip-threshold-gen
                 failing-fn failing-fn-gen]
                (let [cb             (tank.circuit-breaker/circuit-breaker
                                      trip-threshold a-very-large-time :failed? #{::fail})
                      failures-count (atom 0)]
                  (while (not (tank.circuit-breaker/tripped? cb))
                    (swap! failures-count inc)
                    (try (tank.circuit-breaker/call! cb failing-fn) (catch ExceptionInfo _)))
                  (try
                    (tank.circuit-breaker/call! cb failing-fn)
                    (catch Exception ex
                      (t/is (match? {:reason ::tank.circuit-breaker/tripped}
                                    (ex-data ex)))))
                  (tank.circuit-breaker/shutdown! cb)
                  (t/is (= trip-threshold @failures-count)))))

(defspec circuit-breaker-never-trips-if-recovers
  (prop/for-all [trip-threshold trip-threshold-gen
                 failing-fn failing-fn-gen]
                (let [cb (tank.circuit-breaker/circuit-breaker trip-threshold 0)]
                  (loop [num-calls 0]
                    (when (and (not (tank.circuit-breaker/tripped? cb))
                               (< num-calls trip-threshold))
                      (try (tank.circuit-breaker/call! cb failing-fn) (catch ExceptionInfo _))
                      (recur (inc num-calls))))
                  (while (tank.circuit-breaker/tripped? cb))
                  (let [final-result (tank.circuit-breaker/call! cb (constantly ::success))]
                    (tank.circuit-breaker/shutdown! cb)
                    (t/is (= ::success final-result))))))

(defspec circuit-breaker-never-trips-when-threshold-is-not-reached
  (prop/for-all [trip-threshold trip-threshold-gen]
                (let [cb      (tank.circuit-breaker/circuit-breaker trip-threshold a-very-large-time)
                      results (doall
                               (for [_ (range (+ trip-threshold 10))]
                                 (tank.circuit-breaker/call! cb (constantly ::success))))]
                  (tank.circuit-breaker/shutdown! cb)
                  (t/is (match? (repeat (+ trip-threshold 10) ::success) results)))))
