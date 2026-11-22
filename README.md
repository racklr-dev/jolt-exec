# jolt-exec

An in-guest exec daemon for jolt-microvm. Compile it to a standalone native
binary, bake it into a (glibc) guest rootfs, boot it as the entrypoint, and
run commands over a forwarded TCP port — no SSH handshake per call.

## Compile and mount

```bash
jolt build -m jolt.exec.daemon -o exec-daemon
```

`jolt build` produces a self-contained native binary (~29MB, glibc — so it runs
in distroless/debian images, not alpine/musl). The binary is baked into the
guest rootfs and booted directly (no shell needed).

## Protocol

One newline-delimited JSON request per TCP connection, one JSON response, then
close. Two forms:

```json
{"cmd": "echo hi", "cwd": "/", "timeout_ms": 30000}
{"argv": ["/usr/bin/python3", "-c", "print(1)"], "timeout_ms": 30000}
```

`cmd` is shell form (needs `/bin/sh`); `argv` is direct exec (distroless-safe).
Response: `{"stdout": "...", "stderr": "...", "exit": 0}`.

## Client

```clojure
(require '[jolt.exec.client :as c])
(c/sh 8787 "echo hi")                       ; shell form
(c/run 8787 ["/usr/bin/python3" "-c" "print(1)"])  ; direct form
```

## Why

SSH multiplexing already cut warm exec to ~60ms; this daemon cuts it further
(~17ms measured host-side, and connections run concurrently in one VM). It is
the substrate for high-throughput sandboxed exec and for an OpenFaaS-style
fan-out over a broker.

## License

EPL-2.0
