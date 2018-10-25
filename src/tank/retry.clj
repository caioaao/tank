(ns tank.retry)

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

(defn- generic
  [sleep-fn retry-strategy max-attempts catch-fn proc]
  (loop [[result v]    (try-run! proc catch-fn)
         attempts-left (dec max-attempts)]
    (case result
      ::succeeded v
      ::failed    (if (zero? attempts-left)
                    (throw! retry-strategy max-attempts v)
                    (do (Thread/sleep (sleep-fn attempts-left))
                        (recur (try-run! proc catch-fn) (dec attempts-left)))))))

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

(defmacro with-generic
  [sleep-fn retry-strategy max-attempts catch-fn & body]
  `(generic ~sleep-fn ~max-attempts ~catch-fn (fn [] ~@body)))

(defn with-simple-sleep
  [sleep-ms max-attempts catch-fn & body]
  `(with-generic
     (constantly ~sleep-ms)
     ::simple-sleep
     ~max-attempts
     ~catch-fn
     ~@body))

(defmacro with-exponential-backoff
  [slot-time-ms max-attempts catch-fn & body]
  `(with-generic
     (fn [attempts-left#]
       (backoff-time ~slot-time-ms (- ~max-attempts attempts-left#)))
     ::exponential-backoff
     ~max-attempts
     ~catch-fn
     ~@body))
