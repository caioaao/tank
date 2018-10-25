(ns tank.utils)

(defn sleep
  [ms]
  (Thread/sleep ms))

(defn buffer-full?
  [chan]
  (.full? (.buf chan)))

(defn quick-expt
  ([x p r]
   (cond
     (zero? p)         r
     (zero? (mod p 2)) (recur (* x x) (quot p 2) r)
     :else             (recur (* x x) (quot p 2) (* r x))))
  ([x p]
   (quick-expt x p 1M)))
