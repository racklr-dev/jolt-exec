;; jolt.exec.bundle — bake the compiled exec-daemon binary (and the extra
;; shared libraries it links: ncurses/tinfo/uuid, which distroless lacks) into
;; a guest rootfs. The daemon binary is glibc-linked, so the guest must be a
;; glibc image.
(ns jolt.exec.bundle
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str])
  (:import [java.io File]))

(defn- copy-file! [src dst]
  (java.nio.file.Files/copy
   (java.nio.file.Paths/get src (into-array String []))
   (java.nio.file.Paths/get dst (into-array String []))
   (into-array java.nio.file.CopyOption
               [java.nio.file.StandardCopyOption/REPLACE_EXISTING])))

(defn- glibc-lib? [name]
  (or (str/starts-with? name "libc.")
      (str/starts-with? name "libm.")
      (str/starts-with? name "libpthread.")
      (str/starts-with? name "libdl.")
      (str/starts-with? name "librt.")
      (str/starts-with? name "libresolv.")
      (str/starts-with? name "libnsl.")
      (str/starts-with? name "libutil.")
      (str/starts-with? name "ld-linux")))

(defn- resolve-lib [name]
  (let [dirs ["/usr/lib/x86_64-linux-gnu" "/usr/lib" "/lib/x86_64-linux-gnu"
              "/lib" "/usr/local/lib"]]
    (some (fn [d]
            (let [f (File. d name)]
              (when (.exists f) (.getAbsolutePath f))))
          dirs)))

(defn bundle-daemon!
  "Copy the daemon binary and its extra (non-glibc) shared libraries into a
  guest rootfs, executable at guest-path (default /jolt-exec-daemon). Returns
  the guest path. The lib list comes from `ldd`; each is resolved against the
  host's standard library dirs and copied as its soname into the rootfs's
  /usr/lib/x86_64-linux-gnu."
  [daemon-binary rootfs & {:keys [guest-path] :or {guest-path "/jolt-exec-daemon"}}]
  (let [libdir (str rootfs "/usr/lib/x86_64-linux-gnu")
        _ (.mkdirs (File. libdir))
        _ (copy-file! daemon-binary (str rootfs guest-path))
        ldd-out (:out (shell/sh "ldd" daemon-binary))]
    (doseq [line (str/split-lines ldd-out)]
      (when-let [[_ name] (re-find #"\s*(\S+)" line)]
        (when-not (glibc-lib? name)
          (when-let [src (resolve-lib name)]
            (try
              (let [real (.getCanonicalPath (File. src))]
                (copy-file! real (str libdir "/" name)))
              (catch Exception _ nil))))))
    (java.nio.file.Files/setPosixFilePermissions
     (java.nio.file.Paths/get (str rootfs guest-path) (into-array String []))
     (java.nio.file.attribute.PosixFilePermissions/fromString "rwxr-xr-x"))
    guest-path))
