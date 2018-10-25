(ns tank.leaky-bucket
  (:require [clojure.core.async :as async]
            [tank.utils :refer [sleep buffer-full?]]))

(defn- channel [capacity leak-ms]
  (let [bucket (async/chan (async/buffer capacity))]
    (async/go-loop [result (async/<! bucket)]
      (when result
        (sleep leak-ms)))
    bucket))

(defprotocol ILeakyBucket
  (put!
    [this]
    [this timeout-ms])
  (maybe-put! [this])
  (full? [this])
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

  (full? [this]
    (buffer-full? bucket-ch))

  (stop! [this]
    (async/close! channel)))


(defn leaky-bucket
  "Returns a leaky bucket with defined capacity and leak in milliseconds. For more
  info, see the wikipedia article: https://en.wikipedia.org/wiki/Leaky_bucket"
  [capacity leak-ms]
  (->LeakyBucket (channel capacity leak-ms)))
