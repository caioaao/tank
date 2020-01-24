(ns com.caioaao.tank.retry
  (:require [com.caioaao.tank.utils :as tank.utils :refer [quick-expt]]
            [com.caioaao.tank.try-run :as try-run]))

(defn- throw!
  [config last-failure]
  (throw
   (ex-info "Couldn't execute function with number of attempts"
            {:reason ::max-attempts-reached
             :details {:retry-config config
                       :last-failure last-failure}})))

(defn backoff-time
  [slot-time-ms attempt]
  (-> (quick-expt 2M attempt)
      (-' 1M)
      rand bigdec
      (*' slot-time-ms)))

(defn sleep-fn
  [{:keys [strategy] :as sleep-args}]
  (case strategy
    ::simple-sleep        (constantly (:sleep-ms sleep-args))
    ::exponential-backoff (partial backoff-time (:slot-time-ms sleep-args))))

(defn config->runner
  [{:keys [max-attempts strategy-parameters catch? failed?]
    :or   {catch?  (constantly false)
           failed? (constantly false)}
    :as retry-config}]
  (let [sleep (sleep-fn strategy-parameters)]
    (fn [proc]
      (loop [attempt  0
             last-err nil]
        (if (>= attempt max-attempts)
          (throw! retry-config last-err)
          (do (tank.utils/sleep (sleep attempt))
              (let [[result v] (try-run/try-run proc
                                                :catch? catch?
                                                :failed? failed?)]
                (if (#{::try-run/failed ::try-run/exception} result)
                  (recur (inc attempt) v)
                  v))))))))

(defmacro with
  "Tries to run `body` using the provided configuration."
  {:style/indent 1}
  [runner-config & body]
  `(let [run# (config->runner ~runner-config)]
     (run# (fn [] ~@body))))

(defn simple-sleep-config
  "Between each attempt sleep for `sleep-ms` seconds."
  [max-attempts sleep-ms & {:keys [catch? failed?] :as control-fns}]
  (-> {:max-attempts        max-attempts
       :strategy-parameters {:strategy ::simple-sleep
                             :sleep-ms sleep-ms}}
      (merge control-fns)))

(defn exponential-backoff-config
  "Uses an exponential backoff algorithm, meaning that, for every attempt, it
  will wait K * `slot-time-ms`, where K is a random number between 0 and
  `2^c - 1`, `c` being the attempt index."
  [max-attempts slot-time-ms & {:keys [catch? failed?] :as control-fns}]
  (-> {:max-attempts        max-attempts
       :strategy-parameters {:strategy     ::exponential-backoff
                             :slot-time-ms slot-time-ms}}
      (merge control-fns)))
