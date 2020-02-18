(ns com.caioaao.tank.utils
  (:require [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as async.impl])
  (:import java.io.Closeable
           clojure.core.async.impl.channels.ManyToManyChannel
           java.time.Instant))

(defn sleep
  [ms]
  (async/<!! (async/timeout ms)))

(defn buffer-full?
  [^ManyToManyChannel chan]
  (async.impl/full? (.buf chan)))

(defn quick-expt
  ([x p r]
   (cond
     (zero? p)         r
     (zero? (mod p 2)) (recur (* x x) (quot p 2) r)
     :else             (recur (* x x) (quot p 2) (* r x))))
  ([x p]
   (quick-expt x p 1M)))

(defn close! [^Closeable resource]
  (.close resource))

(defn now []
  (java.time.Instant/now))
