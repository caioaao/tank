(ns tank.utils)

(defn sleep
  [ms]
  (Thread/sleep ms))

(defn buffer-full?
  [chan]
  (.full? (.buf chan)))
