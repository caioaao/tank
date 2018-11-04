(ns tank.pre.http-kit
  (:require [org.httpkit.client :as http]
            [clojure.core.async :as async]
            [tank.retry :as retry]
            [tank.circuit-breaker :as circuit-breaker])
  (:import [clojure.lang IDeref]))

(defn- success? [response-code]
  (<= 200 response-code 299))

(defn- retriable-code?
  [response-code]
  (= 500 response-code))

(defn- retriable-verb?
  [verb]
  (contains? #{:get :head :put :delete} verb))

(defn- succeeded?
  [response]
  (retriable-verb? (response :status)))

(defn- catch?
  [http-verb ex]
  (and (not (some-> (ex-data ex) :reason (= ::circuit-breaker/tripped)))
       (retriable-verb? http-verb)))

(defn request
  [circuit-breaker retry-config {:keys [method] :as request-options}]
  (future
    (retry/with (merge retry-config
                       {:catch?  (partial catch? method)
                        :failed? (complement succeeded?)})
      (circuit-breaker/call! circuit-breaker @(http/request request-options)))))

(defn circuit-breaker
  [trip-threshold recovery-ms]
  (circuit-breaker/circuit-breaker trip-threshold recovery-ms
                                   :failed? (complement succeeded?)))
