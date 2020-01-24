(ns com.caioaao.tank.circuit-breaker-test
  (:require [clojure.test :as t]
            [clojure.test.check.generators :as gen]
            [com.caioaao.tank.circuit-breaker :as tank.circuit-breaker]
            [com.gfredericks.test.chuck :as chuck]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [matcher-combinators.test :refer [match? thrown-match?]])
  (:import clojure.lang.ExceptionInfo))

(def a-very-large-time 10000)

(def random-exception (ex-info "A random fail" {}))
(def failing-fn-gen (gen/elements [(fn [] (throw random-exception)) (constantly ::fail)]))
(def trip-threshold-gen (gen/fmap inc gen/pos-int))

(def successful-fn (constantly ::success))

(t/deftest circuit-breaker-test
  (checking "trips after failed attempts reach threshold" (chuck/times 10)
            [trip-threshold trip-threshold-gen
             failing-fn failing-fn-gen
             :let [cb             (tank.circuit-breaker/circuit-breaker
                                   trip-threshold a-very-large-time :failed? #{::fail})
                   failures-count (atom 0)]]
            (while (not (tank.circuit-breaker/tripped? cb))
              (swap! failures-count inc)
              (try (tank.circuit-breaker/call! cb failing-fn) (catch ExceptionInfo _)))
            (t/is (thrown-match? {:reason ::tank.circuit-breaker/tripped}
                                 (tank.circuit-breaker/call! cb failing-fn)))
            (tank.circuit-breaker/shutdown! cb)
            (t/is (= trip-threshold @failures-count)))

  (checking "never trips if there is time to recover between failures" (chuck/times 10)
            [trip-threshold trip-threshold-gen
             failing-fn failing-fn-gen
             :let [cb (tank.circuit-breaker/circuit-breaker trip-threshold 0)]]
            (loop [num-calls 0]
              (when (and (not (tank.circuit-breaker/tripped? cb))
                         (< num-calls trip-threshold))
                (try (tank.circuit-breaker/call! cb failing-fn) (catch ExceptionInfo _))
                (recur (inc num-calls))))
            (while (tank.circuit-breaker/tripped? cb))
            (let [final-result (tank.circuit-breaker/call! cb (constantly ::success))]
              (tank.circuit-breaker/shutdown! cb)
              (t/is (= ::success final-result))))

  (checking "doesn't trip if threshold is not reached" (chuck/times 10)
            [trip-threshold trip-threshold-gen
             :let [cb      (tank.circuit-breaker/circuit-breaker trip-threshold a-very-large-time)
                   results (doall
                            (for [_ (range (+ trip-threshold 10))]
                              (tank.circuit-breaker/call! cb (constantly ::success))))]]
            (tank.circuit-breaker/shutdown! cb)
            (t/is (match? (repeat (+ trip-threshold 10) ::success) results))))
