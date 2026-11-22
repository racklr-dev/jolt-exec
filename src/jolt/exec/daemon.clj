;; jolt.exec.daemon — in-guest exec daemon. Compiled to a standalone native
;; binary with `jolt build -m jolt.exec.daemon -o exec-daemon`, then baked into
;; a (glibc) guest rootfs and booted as the entrypoint. Listens for one
;; newline-delimited JSON exec request per connection:
;;   {"cmd" "echo hi"}        shell form (needs /bin/sh)
;;   {"argv" ["x" "y"]}       direct form (no shell — distroless-safe)
;;   {"cwd" "/" "timeout_ms" N}
;; and replies with one JSON line {"stdout" "..." "stderr" "..." "exit" N}.
(ns jolt.exec.daemon
  (:require [clojure.data.json :as json])
  (:import [java.net ServerSocket]
           [java.io File BufferedReader InputStreamReader
            BufferedWriter OutputStreamWriter ByteArrayOutputStream]))

(defn- drain [in]
  (let [baos (ByteArrayOutputStream.)
        buf (byte-array 8192)]
    (loop []
      (let [n (.read in buf)]
        (when (pos? n)
          (.write baos buf 0 n)
          (recur))))
    (String. (.toByteArray baos) "UTF-8")))

(defn- run-request
  [{:strs [cmd argv cwd timeout_ms]}]
  (let [timeout-ms (long (or timeout_ms 30000))
        cwd (or cwd "/")
        argv (if argv (vec argv) ["sh" "-c" cmd])
        pb (ProcessBuilder. (into-array String argv))
        _ (.directory pb (File. cwd))
        proc (.start pb)
        out (future (drain (.getInputStream proc)))
        err (future (drain (.getErrorStream proc)))]
    (if (.waitFor proc timeout-ms java.util.concurrent.TimeUnit/MILLISECONDS)
      {"stdout" @out "stderr" @err "exit" (.exitValue proc)}
      (do
        (.destroyForcibly proc)
        {"stdout" @out "stderr" @err "exit" -1 "timed_out" true}))))

(defn- handle [sock]
  (try
    (let [in (BufferedReader. (InputStreamReader. (.getInputStream sock)))
          line (.readLine in)]
      (when line
        (let [req (json/read-str line)
              resp (run-request req)
              out (BufferedWriter. (OutputStreamWriter. (.getOutputStream sock)))]
          (.write out (json/write-str resp))
          (.newLine out)
          (.flush out))))
    (catch Exception _ nil)
    (finally
      (try (.close sock) (catch Exception _ nil)))))

(defn -main [& args]
  (let [port (if (seq args) (Long/parseLong (first args)) 8080)
        server (ServerSocket. port)]
    (binding [*out* *err*]
      (println (str "jolt-exec-daemon listening on " port)))
    (loop []
      (let [sock (.accept server)]
        (future (handle sock))
        (recur)))))
