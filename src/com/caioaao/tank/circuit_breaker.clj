(ns com.caioaao.tank.circuit-breaker
  (:require [com.caioaao.tank.leaky-bucket :as leaky-bucket]
            [com.caioaao.tank.try-run :as try-run]
            [com.caioaao.tank.utils :as utils])
  (:import java.io.Closeable))

(defprotocol ICircuitBreaker
  (tripped? [this])
  (call! [this proc])
  (^{:deprecated "1.1.0"} shutdown! [this]))

(defrecord SimpleCircuitBreaker [leaky-bucket failed?]
  ICircuitBreaker
  (call! [this proc]
    (if (tripped? this)
      (throw (ex-info "Circuit breaker is tripped"
                      {:reason              ::tripped
                       :circuit-breaker     this}))
      (let [[status result] (try-run/try-run proc
                                             :failed? failed?
                                             :catch? (constantly true))]
        (when (#{::try-run/failed ::try-run/exception} status)
          (leaky-bucket/put! leaky-bucket))
        (when (= status ::try-run/exception)
          (throw result))
        result)))

  (tripped? [_this]
    (leaky-bucket/full? leaky-bucket))

  (shutdown! [this]
    (utils/close! this))

  Closeable
  (close [_this]
    (utils/close! leaky-bucket)))

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
