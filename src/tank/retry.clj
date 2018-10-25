(ns tank.retry
  (:require [tank.utils :refer [sleep]]))

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
  (loop [attempts-left max-attempts
         last-err nil]
    (if (zero? attempts-left)
      (throw! retry-strategy max-attempts last-err)
      (do (sleep (sleep-fn attempts-left))
          (let [[result v] (try-run! proc catch-fn)]
            (case result
              ::succeeded v
              ::failed    (recur (dec attempts-left) v)))))))

(defmacro with-simple-sleep
  [sleep-ms max-attempts catch-fn & body]
  `(generic (constantly ~sleep-ms) ::simple-sleep ~max-attempts ~catch-fn (fn [] ~@body)))

(defn- quick-expt
  ([x p r]
   (cond
     (zero? p)         r
     (zero? (mod p 2)) (recur (* x x) (quot p 2) r)
     :else             (recur (* x x) (quot p 2) (* r x))))
  ([x p]
   (quick-expt x p 1M)))

(defn- backoff-time
  [slot-time-ms attempt]
  (* (rand (quick-expt 2M attempt))
     slot-time-ms))

(defmacro with-exponential-backoff
  [slot-time-ms max-attempts catch-fn & body]
  `(generic
     (fn [attempts-left#]
       (backoff-time ~slot-time-ms (- ~max-attempts attempts-left#)))
     ::exponential-backoff
     ~max-attempts
     ~catch-fn
     (fn [] ~@body)))
