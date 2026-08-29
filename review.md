# Android Session Recovery Real-Gateway QA

Date: 2026-07-24

Scope: debug APK installed over the existing authenticated client on a physical
Android device. The configured remote Gateway was used; no mock Gateway or
local endpoint was involved.

## Passed

- Sent an isolated recovery test request and observed the initial SSE stream
  open on the real Gateway.
- Force-stopped the app while that stream was active, then cold-started it.
  The client resumed the same server run with a persisted event cursor and
  rendered one completed assistant response, without duplicating the user
  message or creating a second run.
- Observed `end=EndEvent` in the Android debug stream log. The UI changed from
  the active-run stop control back to the send control only after finalization.
- Cold-started once more after terminal completion. No new stream opened and
  `dumpsys activity services com.deerflow.mobile` reported no active service,
  confirming that the persisted active-run record and RunService were cleared.
- No `AndroidRuntime` crash was present in the device log during the flow.

## Network-Interruption Coverage

The device supports a per-package networking firewall command, so it was used
to deny traffic only for `com.deerflow.mobile` during a second real request.
On this ROM the command did not terminate the already-open SSE TCP socket; the
stream continued through `EndEvent`. It therefore does not provide a valid
in-process network-loss/reconnect sample.

The original global-network method was intentionally not used because ADB is
connected over Wi-Fi and disabling the device network could strand the test
session. The process-death/cold-start recovery path above is validated against
the real Gateway; a dedicated USB-connected device or a controllable network
proxy is still needed to prove the live in-process reconnect path.
