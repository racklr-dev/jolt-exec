(ns jolt.test.latency
  (:require [jolt.exec.client :as c]))

;; warm up
(c/sh 8787 "true")
(let [n 50
      t (System/nanoTime)]
  (dotimes [_ n] (c/sh 8787 "true"))
  (let [ms (/ (- (System/nanoTime) t) 1e6)]
    (println "daemon exec:" (Math/round (double (/ ms n))) "ms/call over" n "calls")))
