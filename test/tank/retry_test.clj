(ns tank.retry-test
  (:require [tank.retry :as tank.retry]
            [tank.utils :as tank.utils]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]))

(defn- abs [x] (max x (-' x)))

(defn sleep-mock []
  (let [vals (atom [])]
    {:values-atom vals
     :sleep-mock  (fn [v] (swap! vals conj v))}))

(def proc-exception (ex-info "I always fail" {:details "bleh"}))

(defn fail-until [n]
  (let [attempts (atom 0)]
    (fn []
      (if (< (swap! attempts inc) n)
        (throw proc-exception)
        :success!))))

(defspec simple-retry-always-use-same-sleep
  (prop/for-all [sleep-ms gen/pos-int
                 n-attempts (gen/fmap (partial + 10) gen/pos-int)]
                (let [{:keys [values-atom sleep-mock]} (sleep-mock)
                      proc                             (fail-until n-attempts)]
                  (with-redefs [tank.utils/sleep sleep-mock]
                    (tank.retry/with
                     (tank.retry/simple-sleep-config
                      (inc n-attempts) sleep-ms
                      :catch? (constantly true))
                     (proc))
                    (t/is (= (count @values-atom) n-attempts))
                    (t/is (every? #{sleep-ms} @values-atom))))))

(defspec simple-retry-throws-if-max-attempts-is-reached
  (prop/for-all [n-attempts (gen/fmap (partial + 10) gen/pos-int)]
                (let [proc (fail-until n-attempts)
                      config (tank.retry/simple-sleep-config (dec n-attempts) 0 :catch? (constantly true))]
                  (try
                    (tank.retry/with config (proc))
                    (catch clojure.lang.ExceptionInfo ex
                      (t/is (match? {:reason  ::tank.retry/max-attempts-reached
                                     :details {:retry-config config
                                               :last-failure (m/equals proc-exception)}}
                                    (ex-data ex))))))))

(defn expected-backoff [slot-time-ms n-attempts]
  "For more info, see formula in https://en.wikipedia.org/wiki/Exponential_backoff#Expected_backoff"
  (/ (*' slot-time-ms (-' (tank.utils/quick-expt 2M n-attempts) 1M)) 2M))

(defn roughly? [expected v ratio]
  (< (-' ratio) (-' (/ v expected) 1M) ratio))

(defspec exponential-backoff-sleep-ms-avg 100
  (prop/for-all [slot-time-ms (gen/fmap inc gen/pos-int)]
                (let [n-attempts    10
                      slot-time-ms  1
                      retry-config  (tank.retry/exponential-backoff-config n-attempts slot-time-ms :catch? (constantly true))
                      run-times     500
                      last-backoffs (atom [])]
                  (loop [run-counter 0]
                    (when (< run-counter run-times)
                      (let [{:keys [values-atom sleep-mock]} (sleep-mock)
                            proc                             (fail-until n-attempts)]
                        (with-redefs [tank.utils/sleep sleep-mock]
                          (tank.retry/with retry-config (proc)))
                        (swap! last-backoffs conj (last @values-atom)))

                      (recur (inc run-counter))))
                  (with-precision 10
                    (t/is (roughly? (expected-backoff slot-time-ms (dec n-attempts))
                                    (/ (apply +' @last-backoffs) run-times)
                                    0.15M))))))


