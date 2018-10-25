(ns tank.leaky-bucket
  (:require [clojure.core.async :as async]))

(defn- channel [capacity leak-ms]
  (let [bucket (async/buffer capacity)]
    (async/go-loop [result (async/<! bucket)]
      (when result
        (Thread/sleep leak-ms)))
    bucket))

(defprotocol ILeakyBucket
  (put!
    [this]
    [this timeout-ms])
  (maybe-put! [this])
  (stop! [this]))

(defrecord LeakyBucket [bucket-ch]
  ILeakyBucket
  (put! [this]
    (async/>!! bucket-ch ::bucket-token)
    ::sent)

  (put! [this timeout-ms]
    (let [timeout-ch (async/timeout timeout-ms)]
      (async/alt!
        timeout-ch ::timed-out
        [[bucket-ch ::bucket-token]] ::sent)))

  (maybe-put! [this]
    (if (async/offer! channel ::bucket-token)
      ::sent
      ::dropped))

  (stop! [this]
    (async/close! channel)))


(defn leaky-bucket [capacity leak-ms]
  (->LeakyBucket (channel capacity leak-ms)))
