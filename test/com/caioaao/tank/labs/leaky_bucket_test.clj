(ns com.caioaao.tank.labs.leaky-bucket-test
  (:require [com.caioaao.tank.labs.leaky-bucket :as leaky-bucket]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test :refer [match?]])
  (:import java.util.Date))

(def as-of #inst "2012-12-12T01:02:03.345Z")

(defn- inst+ms [t delta-ms]
  (Date. ^long (+ (inst-ms t) delta-ms)))

(deftest timestamp-leaky-bucket
  (testing "consumes tokens based on instant"
    (is (match? {::leaky-bucket/token-counter 7}
                (-> (reduce (fn [s _] (leaky-bucket/offer-token s as-of))
                            (leaky-bucket/empty-bucket 18 15 as-of)
                            (range 13))
                    (leaky-bucket/at-time-t (inst+ms as-of 90)))))
    (is (match? {::leaky-bucket/token-counter 7}
                (-> (reduce (fn [s _] (leaky-bucket/offer-token s as-of))
                            (leaky-bucket/empty-bucket 18 15 as-of)
                            (range 13))
                    (leaky-bucket/at-time-t (inst+ms as-of 93)))))
    (is (match? {::leaky-bucket/token-counter 8}
                (-> (reduce (fn [s _] (leaky-bucket/offer-token s as-of))
                            (leaky-bucket/empty-bucket 18 15 as-of)
                            (range 13))
                    (leaky-bucket/at-time-t (inst+ms as-of 89)))))
    (is (match? {::leaky-bucket/token-counter 0}
                (-> (reduce (fn [s _] (leaky-bucket/offer-token s as-of))
                            (leaky-bucket/empty-bucket 18 15 as-of)
                            (range 13))
                    (leaky-bucket/at-time-t (inst+ms as-of 123902039092302N))))))
  (testing "returns nil when trying to add a token to full bucket"
    (is (not (-> (leaky-bucket/empty-bucket 1 10 as-of)
                 (leaky-bucket/offer-token as-of)
                 (leaky-bucket/offer-token as-of)))))
  (testing "can add token on empty bucket"
    (is (match? {::leaky-bucket/token-counter 1
                 ::leaky-bucket/last-update   as-of}
                (-> (leaky-bucket/empty-bucket 3 10 as-of)
                    (leaky-bucket/offer-token as-of))))))
