(ns tank.retry-test
  (:require [tank.retry :as tank.retry]
            [tank.utils :as tank.utils]
            [clojure.test :as t]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [assert-check]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]))

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

(def simple-retry-always-use-same-sleep
  (prop/for-all [sleep-ms gen/pos-int
                 n-attempts (gen/fmap (partial + 10) gen/pos-int)]
                (let [{:keys [values-atom sleep-mock]} (sleep-mock)
                      proc                             (fail-until n-attempts)]
                  (with-redefs [tank.utils/sleep sleep-mock]
                    (tank.retry/with-simple-sleep sleep-ms (inc n-attempts) (constantly true)
                      (proc))
                    (t/is (= (count @values-atom) n-attempts))
                    (t/is (every? #{sleep-ms} @values-atom))))))

(def simple-retry-throws-if-max-attempts-is-reached
  (prop/for-all [n-attempts (gen/fmap (partial + 10) gen/pos-int)]
                (let [proc (fail-until n-attempts)]
                  (try
                    (tank.retry/with-simple-sleep 0 (dec n-attempts) (constantly true)
                      (proc))
                    (catch clojure.lang.ExceptionInfo ex
                      (t/is (match? {:max-attempts   (dec n-attempts)
                                     :retry-strategy ::tank.retry/simple-sleep
                                     :last-exception (m/equals proc-exception)}
                                    (ex-data ex))))))))

(t/deftest simple-retry
  (tc/quick-check 100 simple-retry-always-use-same-sleep)
  (tc/quick-check 100 simple-retry-throws-if-max-attempts-is-reached))


(defn expected-backoff [slot-time-ms n-attempts]
  "For more info, see formula in https://en.wikipedia.org/wiki/Exponential_backoff#Expected_backoff"
  (/ (* slot-time-ms (tank.utils/quick-expt 2M n-attempts)) 2))

(def exponential-backoff-sleep-ms-avg
  (prop/for-all [slot-time-ms (gen/fmap inc gen/pos-int)
                 n-attempts (gen/fmap (partial + 100) gen/pos-int)]
                (let [{:keys [values-atom sleep-mock]} (sleep-mock)
                      proc                             (fail-until n-attempts)]
                  (with-redefs [tank.utils/sleep sleep-mock]
                    (tank.retry/with-exponential-backoff slot-time-ms (inc n-attempts) (constantly true)
                      (proc))
                    (t/is (= (count @values-atom) n-attempts))
                    (t/is (< (Math/abs (- (expected-backoff slot-time-ms n-attempts)
                                          (-> (apply + @values-atom) (/ n-attempts))))
                             0.01M))))))

(t/deftest exponential-backoff
  (tc/quick-check 100 exponential-backoff-sleep-ms-avg))
