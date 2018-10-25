(ns tank.circuit-breaker
  (:require [tank.leaky-bucket :as leaky-bucket]))

(defprotocol ICircuitBreaker
  (tripped? [this])
  (call! [this proc])
  (shutdown! [this]))

(defrecord SimpleCircuitBreaker [leaky-bucket]
  ICircuitBreaker
  (tripped? [_this]
    (leaky-bucket/full? leaky-bucket))

  (call! [this proc]
    (if (tripped? this)
      ::tripped
      (try
        (proc)
        (catch Exception ex
          (leaky-bucket/put! leaky-bucket)
          (throw ex)))))

  (shutdown! [_this]
    (leaky-bucket/stop! leaky-bucket)))

(defn circuit-breaker
  "Creates a circuit breaker.
  Params:

  `trip-threshold`: number denoting amount of fails before the circuit
  breaker is tripped

  `recovery-ms`: time, in milliseconds, for resetting one error count."
  [trip-threshold recovery-ms]
  (->SimpleCircuitBreaker (leaky-bucket/leaky-bucket trip-threshold recovery-ms)))
