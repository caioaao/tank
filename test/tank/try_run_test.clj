(ns tank.try-run-test
  (:require [tank.try-run :refer [try-run]]
            [clojure.test :as t]))

(def success-val {:foo 888})
(def failure-val {:foo 999})

(def expected-exception (ex-info "" {:details :ok}))
(def unexpected-exception (ex-info "" {:details :nok}))

(def always-success (constantly success-val))
(def always-failure (constantly failure-val))
(defn always-expected-exception [& _] (throw expected-exception))
(defn always-unexpected-exception [& _] (throw unexpected-exception))

(t/deftest try-run-defaults
  (t/is (= [:tank.try-run/succeeded success-val] (try-run always-success)))
  (t/is (= [:tank.try-run/succeeded failure-val] (try-run always-failure)))
  (try (try-run always-expected-exception) (catch Exception ex (t/is (= expected-exception ex))))
  (try (try-run always-unexpected-exception) (catch Exception ex (t/is (= unexpected-exception ex)))))

(defn catch? [ex]
  (case (-> (ex-data ex) :details)
    :ok  true
    :nok false))

(t/deftest with-catch-fn
  (t/is (= [:tank.try-run/succeeded success-val] (try-run always-success :catch? catch?)))
  (t/is (= [:tank.try-run/succeeded failure-val] (try-run always-failure :catch? catch?)))
  (t/is (= [:tank.try-run/exception expected-exception] (try-run always-expected-exception :catch? catch?)))
  (try (try-run always-unexpected-exception :catch? catch?) (catch Exception ex (t/is (= unexpected-exception ex)))))

(defn failed? [result]
  (case (:foo result)
    888 false
    999 true))

(t/deftest with-fail-fn
  (t/is (= [:tank.try-run/succeeded success-val] (try-run always-success :failed? failed?)))
  (t/is (= [:tank.try-run/failed failure-val] (try-run always-failure :failed? failed?)))
  (try (try-run always-expected-exception :failed? failed?) (catch Exception ex (t/is (= expected-exception ex))))
  (try (try-run always-unexpected-exception :failed? failed?) (catch Exception ex (t/is (= unexpected-exception ex)))))

(t/deftest with-both-fn
  (t/is (= [:tank.try-run/succeeded success-val] (try-run always-success :failed? failed? :catch? catch?)))
  (t/is (= [:tank.try-run/failed failure-val] (try-run always-failure :failed? failed? :catch? catch?)))
  (t/is (= [:tank.try-run/exception expected-exception] (try-run always-expected-exception :failed? failed? :catch? catch?)))
  (try (try-run always-unexpected-exception :failed? failed? :catch? catch?) (catch Exception ex (t/is (= unexpected-exception ex)))))
