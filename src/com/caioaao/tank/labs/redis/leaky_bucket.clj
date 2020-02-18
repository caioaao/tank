(ns com.caioaao.tank.labs.redis.leaky-bucket
  (:require [com.caioaao.tank.leaky-bucket :as leaky-bucket]
            [taoensso.carmine :as car]
            [clojure.java.io :as io]
            [com.caioaao.tank.utils :as utils]))

(def ^:private load-script
  (memoize
   (fn [script-name]
     (-> (str "com/caioaao/tank/labs/redis/leaky_bucket/" script-name)
         io/resource
         slurp))))

(defn- update-state* [{:keys [keys-prefix leak-ms]} as-of]
  (car/lua (load-script "update_state.lua")
           {:update-time-key (str keys-prefix "updated_at")
            :counter-key     (str keys-prefix "counter")}
           {:leak-ms leak-ms
            :as-of   (inst-ms as-of)}))

(defn- offer-token* [{:keys [keys-prefix capacity]}]
  ;; Assumes state is up to date
  (car/lua (load-script "offer_token.lua")
           {:counter-key (str keys-prefix "counter")}
           {:capacity capacity}))

(defn update-state! [conn config as-of]
  (car/wcar conn (update-state* config as-of)))

(defn offer-token! [conn config as-of]
  (last
   (car/wcar conn
             (update-state* config as-of)
             (offer-token* config))))

(defrecord LeakyBucket
    [conn-opts config]
  leaky-bucket/ILeakyBucket
  (offer-token! [this]
    (offer-token! conn-opts config (utils/now)))

  (full? [this]
    ;; TODO
    )

  java.io.Closeable
  (close [this]))


(comment
  (offer-token! {} {:keys-prefix "", :leak-ms 500, :capacity 1000}
                (java.util.Date.))
  )
