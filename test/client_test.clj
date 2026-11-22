(ns jolt.test.exec-client
  (:require [jolt.exec.client :as c]))

;; shell form
(let [r (c/sh 8787 "echo daemon-ok")]
  (assert (zero? (:exit r)) (str "exit " (:exit r)))
  (assert (clojure.string/includes? (:stdout r) "daemon-ok"))
  (println :shell (:stdout r)))

;; direct argv form (no shell) + nonzero exit
(let [r (c/run 8787 ["/bin/sh" "-c" "echo argv-ok; exit 3"])]
  (assert (= 3 (:exit r)) (str "exit " (:exit r)))
  (assert (clojure.string/includes? (:stdout r) "argv-ok"))
  (println :argv (:stdout r) :exit (:exit r)))

;; stderr capture
(let [r (c/sh 8787 "echo oops >&2")]
  (assert (clojure.string/includes? (:stderr r) "oops"))
  (println :stderr-captured true))

(println "jolt-exec daemon client OK")
