# jolt-exec — in-guest exec daemon for jolt-microvm

An in-guest exec daemon for [jolt-microvm](https://github.com/racklr-dev/jolt-microvm):
compile it to a standalone native binary, bake it into a (glibc) guest rootfs,
boot it as the entrypoint, and run commands over a forwarded TCP port — no SSH
handshake per call.

## Requirements

- [jolt](https://github.com/jolt-lang/jolt) 0.8.28+ (to build the daemon and
  run the tests)
- A glibc guest for the daemon binary (distroless/debian images work; alpine
  musl does not)

## Build

```bash
jolt build -m jolt.exec.daemon -o exec-daemon
```

`jolt build` produces a self-contained native binary (~29MB, glibc). Bake it
into the guest rootfs and boot it directly (no shell needed).

## Usage

The daemon speaks one newline-delimited JSON request per TCP connection, one
JSON response, then close:

```json
{"cmd": "echo hi", "cwd": "/", "timeout_ms": 30000}
{"argv": ["/usr/bin/python3", "-c", "print(1)"], "timeout_ms": 30000}
```

`cmd` is shell form (needs `/bin/sh`); `argv` is direct exec (distroless-safe).
Response: `{"stdout": "...", "stderr": "...", "exit": 0}`.

Client (from jolt):

```clojure
(require '[jolt.exec.client :as c])
(c/sh 8787 "echo hi")                        ; shell form
(c/run 8787 ["/usr/bin/python3" "-c" "print(1)"])  ; direct form
```

## API

| Function | Args | Returns | Errors |
|---|---|---|---|
| `sh` | `port cmd & {:keys [cwd timeout-ms]}` | `{:stdout :stderr :exit}` | throws on connect/timeout |
| `run` | `port argv & {:keys [cwd timeout-ms]}` | `{:stdout :stderr :exit}` | throws on connect/timeout |

The daemon's `-main` listens on a port (default 8080). SSH multiplexing cut
warm exec to ~60ms; this daemon cuts it further (~17ms measured host-side),
and connections run concurrently in one VM.

## Status

beta — the client/daemon round-trip is tested (`test/jolt/client_test.clj`,
`test/jolt/latency_test.clj`). It is the substrate for high-throughput
sandboxed exec and for a broker fan-out, which are not yet implemented here.

## License

EPL-2.0

## Learn more

- [jolt-lang](https://github.com/jolt-lang/jolt) — the language and toolchain
- [jolt-microvm](https://github.com/racklr-dev/jolt-microvm) — the consumer that boots this daemon
- [jolt build](https://github.com/jolt-lang/jolt) — the native-image build used to compile the daemon
