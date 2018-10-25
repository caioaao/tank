(ns tank.circuit-breaker
  (:require [tank.leaky-bucket :as leaky-bucket]))


(defprotocol ICircuitBreaker
  (call! [this proc & override-params]))

(defrecord SimpleCircuitBreaker [leaky-bucket]
  ICircuitBreaker
  (call! [this proc & override-params]
    (if (leaky-bucket/full? leaky-bucket)
      ::tripped
      (try
        (proc)
        (catch Exception ex
          (leaky-bucket/put! leaky-bucket)
          (throw ex))))))

(defn simple
  [trip-threshold recovery-ms]
  (->SimpleCircuitBreaker (leaky-bucket/leaky-bucket trip-threshold recovery-ms)))
