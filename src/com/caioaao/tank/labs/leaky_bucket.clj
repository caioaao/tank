(ns com.caioaao.tank.labs.leaky-bucket
  (:require [clojure.core.async :as async]
            [com.caioaao.tank.leaky-bucket :as leaky-bucket]
            [com.caioaao.tank.utils :refer [buffer-full? sleep] :as utils]))

(defn at-time-t
  "Advances bucket state to timestamp `t`"
  [{::keys [token-counter last-update leak-ms] :as bucket} t]
  (merge bucket
         {::last-update   t
          ::token-counter (->> (quot (- (inst-ms t)
                                        (inst-ms last-update))
                                     leak-ms)
                               (- token-counter)
                               (max 0))}))

(defn full?
  [{::keys [token-counter capacity]}]
  (>= token-counter capacity))

(defn offer-token
  "Tries to add a token to the bucket. If successful, returns the bucket with a
  new token. Returns nil otherwise"
  [{::keys [capacity] :as bucket} t]
  (let [bucket-now (at-time-t bucket t)]
    (when-not (full? bucket-now)
      (update bucket-now ::token-counter inc))))

(defn empty-bucket
  ([capacity leak-ms]
   (empty-bucket capacity leak-ms (utils/now)))
  ([capacity leak-ms t-0]
   {::token-counter 0
    ::last-update   t-0
    ::capacity      capacity
    ::leak-ms       leak-ms}))

(defrecord LeakyBucketTimestampBased [state-ref]
  leaky-bucket/ILeakyBucket
  (offer-token! [this]
    (dosync
     (if-let [state' (offer-token @state-ref
                                  (utils/now))]
       (do (ref-set state-ref state') ::sent)
       ::dropped)))

  (full? [this]
    (dosync (full? (alter state-ref at-time-t (utils/now)))))

  java.io.Closeable
  (close [_this] nil))

(defn leaky-bucket
  "Returns a leaky bucket with defined capacity and leak in milliseconds. For more
  info, see the wikipedia article: https://en.wikipedia.org/wiki/Leaky_bucket"
  ([capacity leak-ms]
   (->LeakyBucketTimestampBased
    (ref (empty-bucket capacity leak-ms)))))
