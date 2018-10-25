(ns tank.retry
  (:require [tank.utils :refer [quick-expt sleep]]))

(defn- try-run!
  [proc catch-fn]
  (try
    [::succeeded (proc)]
    (catch Exception ex
      (if (catch-fn ex)
        [::failed ex]
        (throw ex)))))

(defn- throw!
  [retry-strategy max-attempts last-exc]
  (throw
   (ex-info "Couldn't execute function with number of attempts"
            {:max-attempts   max-attempts
             :retry-strategy retry-strategy
             :last-exception last-exc})))

(defn generic
  [sleep-fn retry-strategy max-attempts catch-fn proc]
  (loop [attempt 0
         last-err nil]
    (if (>= attempt max-attempts)
      (throw! retry-strategy max-attempts last-err)
      (do (sleep (sleep-fn attempt))
          (let [[result v] (try-run! proc catch-fn)]
            (case result
              ::succeeded v
              ::failed    (recur (inc attempt) v)))))))

(defmacro with-simple-sleep
  [sleep-ms max-attempts catch-fn & body]
  `(generic (constantly ~sleep-ms) ::simple-sleep ~max-attempts ~catch-fn (fn [] ~@body)))

(defn backoff-time
  [slot-time-ms attempt]
  (* (rand-int (- (quick-expt 2M attempt) 1M))
     (bigdec slot-time-ms)))

(defmacro with-exponential-backoff
  [slot-time-ms max-attempts catch-fn & body]
  `(generic
     (fn [attempt#]
       (backoff-time ~slot-time-ms attempt#))
     ::exponential-backoff
     ~max-attempts
     ~catch-fn
     (fn [] ~@body)))
