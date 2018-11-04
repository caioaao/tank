(ns tank.circuit-breaker
  (:require [tank.leaky-bucket :as leaky-bucket]
            [tank.try-run :refer [try-run]]))

(defprotocol ICircuitBreaker
  (tripped? [this])
  (call! [this proc])
  (shutdown! [this]))

(defrecord SimpleCircuitBreaker [leaky-bucket failed?]
  ICircuitBreaker
  (tripped? [_this]
    (leaky-bucket/full? leaky-bucket))

  (call! [this proc]
    (if (tripped? this)
      (throw (ex-info "Circuit breaker is tripped"
                      {:reason              ::tripped
                       :circuit-breaker     this}))
      (let [[status result] (try-run proc :failed? failed? :catch? (constantly true))]
        (when (#{:tank.try-run/failed :tank.try-run/exception} status)
          (leaky-bucket/put! leaky-bucket))
        (when (= status :tank.try-run/exception)
          (throw result))
        result)))

  (shutdown! [_this]
    (leaky-bucket/stop! leaky-bucket)))

(defn circuit-breaker
  "Creates a circuit breaker.
  Params:

  `trip-threshold`: number denoting amount of fails before the circuit
  breaker is tripped

  `recovery-ms`: time, in milliseconds, for resetting one error count.

  `failed?`: optional function that will run on the result of `proc`. If it
    evaluates to `true`, the result counts as a failure and will consume
    from the bucked. Defaults to `(constantly false)`"
  [trip-threshold recovery-ms & {:keys [failed?]
                                 :or   {failed? (constantly false)}}]
  (->SimpleCircuitBreaker
   (leaky-bucket/leaky-bucket trip-threshold recovery-ms)
   failed?))
