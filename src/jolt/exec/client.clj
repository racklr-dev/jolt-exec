;; jolt.exec.client — talk to a jolt-exec daemon over a forwarded TCP port.
;; Protocol: one newline-delimited JSON request, one JSON response, then close.
(ns jolt.exec.client
  (:require [clojure.data.json :as json])
  (:import [java.net Socket]
           [java.io BufferedReader InputStreamReader BufferedWriter OutputStreamWriter]))

(defn exec
  "Send one request map and return {:stdout :stderr :exit}. request: a shell
  form {:cmd ...} or a direct form {:argv [...]}, plus :cwd and :timeout_ms."
  ([port request] (exec "127.0.0.1" port request))
  ([host port request]
   (let [s (Socket. host port)
         out (BufferedWriter. (OutputStreamWriter. (.getOutputStream s)))
         in (BufferedReader. (InputStreamReader. (.getInputStream s)))]
     (try
       (.write out (json/write-str request))
       (.newLine out)
       (.flush out)
       (let [resp (json/read-str (.readLine in))]
         {:stdout (or (get resp "stdout") "")
          :stderr (or (get resp "stderr") "")
          :exit (get resp "exit")})
       (finally (.close s))))))

(defn sh
  "Run a shell command string via the daemon (guest must have /bin/sh)."
  [port cmd & {:keys [cwd timeout-ms] :or {cwd "/" timeout-ms 30000}}]
  (exec port {"cmd" cmd "cwd" cwd "timeout_ms" timeout-ms}))

(defn run
  "Run a direct argv vector via the daemon (no shell needed — distroless-safe)."
  [port argv & {:keys [cwd timeout-ms] :or {cwd "/" timeout-ms 30000}}]
  (exec port {"argv" (vec argv) "cwd" cwd "timeout_ms" timeout-ms}))
