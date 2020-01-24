(ns com.caioaao.tank.try-run-test
  (:require [com.caioaao.tank.try-run :as try-run]
            [matcher-combinators.test :refer [thrown-match?]]
            [clojure.test :as t])
  (:import clojure.lang.ExceptionInfo))

(def success-val {:foo 888})
(def failure-val {:foo 999})

(def expected-exception-data {:details :ok})
(def unexpected-exception-data {:details :nok})

(def expected-exception (ex-info "" expected-exception-data))
(def unexpected-exception (ex-info "" unexpected-exception-data))

(def always-success (constantly success-val))
(def always-failure (constantly failure-val))
(defn always-expected-exception [& _] (throw expected-exception))
(defn always-unexpected-exception [& _] (throw unexpected-exception))

(t/deftest try-run-defaults
  (t/is (= [::try-run/succeeded success-val] (try-run/try-run always-success)))
  (t/is (= [::try-run/succeeded failure-val] (try-run/try-run always-failure)))
  (t/is (thrown-match? expected-exception-data
                       (try-run/try-run always-expected-exception)))
  (t/is (thrown-match?                        unexpected-exception-data
                       (try-run/try-run always-unexpected-exception))))

(defn catch? [ex]
  (case (-> (ex-data ex) :details)
    :ok  true
    :nok false))

(t/deftest with-catch-fn
  (t/is (= [::try-run/succeeded success-val] (try-run/try-run always-success :catch? catch?)))
  (t/is (= [::try-run/succeeded failure-val] (try-run/try-run always-failure :catch? catch?)))
  (t/is (= [::try-run/exception expected-exception] (try-run/try-run always-expected-exception :catch? catch?)))
  (t/is (thrown-match? unexpected-exception-data
                       (try-run/try-run always-unexpected-exception :catch? catch?))))

(defn failed? [result]
  (case (:foo result)
    888 false
    999 true))

(t/deftest with-fail-fn
  (t/is (= [::try-run/succeeded success-val] (try-run/try-run always-success :failed? failed?)))
  (t/is (= [::try-run/failed failure-val] (try-run/try-run always-failure :failed? failed?)))
  (t/is (thrown-match? expected-exception-data
                       (try-run/try-run always-expected-exception :failed? failed?)))
  (t/is (thrown-match? unexpected-exception-data
                       (try-run/try-run always-unexpected-exception :failed? failed?))))

(t/deftest with-both-fn
  (t/is (= [::try-run/succeeded success-val] (try-run/try-run always-success :failed? failed? :catch? catch?)))
  (t/is (= [::try-run/failed failure-val] (try-run/try-run always-failure :failed? failed? :catch? catch?)))
  (t/is (= [::try-run/exception expected-exception] (try-run/try-run always-expected-exception :failed? failed? :catch? catch?)))
  (t/is (thrown-match? unexpected-exception-data
                       (try-run/try-run always-unexpected-exception :failed? failed? :catch? catch?))))

