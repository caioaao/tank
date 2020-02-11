(ns com.caioaao.tank.retry-test
  (:require [clojure.test :as t]
            [clojure.test.check.generators :as gen]
            [com.caioaao.tank.retry :as tank.retry]
            [com.caioaao.tank.utils :as tank.utils]
            [com.gfredericks.test.chuck :as chuck]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [thrown-match?]]))

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

(t/deftest simple-retry
  (checking "always uses same sleep" (chuck/times 50)
            [sleep-ms gen/pos-int
             n-attempts (gen/fmap #(-> % (min 10) (max 100)) gen/pos-int)
             :let [{:keys [values-atom sleep-mock]} (sleep-mock)
                   proc                             (fail-until n-attempts)]]
            (with-redefs [tank.utils/sleep sleep-mock]
              (tank.retry/with
               (tank.retry/simple-sleep-config
                (inc n-attempts) sleep-ms
                :catch? (constantly true))
               (proc))
              (t/is (= (count @values-atom) n-attempts))
              (t/is (every? #{sleep-ms} @values-atom))))

  (checking "throws if max attempts is reached" (chuck/times 10)
            [n-attempts (gen/fmap (partial + 10) gen/pos-int)
             :let [proc   (fail-until n-attempts)
                   config (tank.retry/simple-sleep-config (dec n-attempts) 0 :catch? (constantly true))]]
            (t/is (thrown-match? {:reason  ::tank.retry/max-attempts-reached
                                  :details {:retry-config config
                                            :last-failure (m/equals proc-exception)}}
                                 (tank.retry/with config (proc))))))

(defn expected-backoff [slot-time-ms n-attempts]
  "For more info, see formula in https://en.wikipedia.org/wiki/Exponential_backoff#Expected_backoff"
  (with-precision 60 (/ (*' slot-time-ms (-' (tank.utils/quick-expt 2M n-attempts) 1M)) 2M)))

(defn avg [vs] (with-precision 60 (/ (reduce +' vs) (count vs))))

(defn roughly? [expected v ratio]
  (with-precision 60 (< (-' ratio) (-' (/ v expected) 1M) ratio)))

(defn last-backoff [retry-config n-attempts]
  (let [{:keys [values-atom sleep-mock]} (sleep-mock)
        proc                             (fail-until n-attempts)]
    (with-redefs [tank.utils/sleep sleep-mock]
      (tank.retry/with retry-config (proc)))
    (last @values-atom)))

(t/deftest exponential-backoff
  (checking "average sleep ms matches expected" (chuck/times 100)
            [n-attempts (gen/choose 5 15)
             :let [slot-time-ms 1
                   retry-config  (tank.retry/exponential-backoff-config n-attempts
                                                                        slot-time-ms
                                                                        :catch? (constantly true))
                   last-backoffs (repeatedly 500 #(last-backoff retry-config n-attempts))]]
            (t/is (roughly? (expected-backoff slot-time-ms (dec n-attempts))
                            (avg last-backoffs)
                            0.15M))))
