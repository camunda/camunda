# Recording run sheet

What to have on screen while [run-demo.sh](run-demo.sh) drives the demo. The script itself carries
the narration for each use case, so this file only covers what the camera should be pointed at and
roughly how long each part takes.

Every use case here was executed end to end against a local build of `main`.

## Setup

- One terminal running `./run-demo.sh`. This terminal is your prompter and does not need to be in
  frame: it tells you what the use case is about, waits for Enter, runs it, and then says where to
  look.
- If instead you want the requests and responses on camera, run `./present-demo.sh` in the prompter
  terminal and paste each command it gives you into a second, filmed shell. Same use cases, same
  narration; it executes nothing itself.
- A browser on Operate at <http://localhost:8080/operate> (`demo` / `demo`). Neither script opens
  a browser window during the demo; c8run opens Operate once when the cluster starts, and after
  that you drive the browser. The two use cases that need Operate print the exact URL.
- Desktop Modeler with [models/order-process.bpmn](models/order-process.bpmn) open, for use case 3.
- The solution-proposal architecture diagram, for the framing before use case 1.
- Nothing to reset by hand. The script resets on start and tears down on exit, so it can be run
  again immediately after a take.

## Framing, before use case 1 (1 min)

Architecture diagram. One line: secret resolution used to be re-implemented per component, per
runtime, and differently on SaaS versus Self-Managed. This epic moves it to one central path, so
every consumer references secrets the same way and receives values already resolved.

## What to show, per use case

1. **The file-based secret store** (1 min). The directory listing. Call out `tls.crt` (valid file
   name, invalid reference name) and that `missingToken` is absent on purpose.
2. **Configuring the store** (1.5 min). The `camunda.secrets` block, then the startup log line. The
   three points: store id must be `default`, the cache is shared by broker and gateway, and the
   commented physical-tenant override is where per-tenant isolation goes.
3. **Referencing secrets in a model** (2 min). Modeler, on the service task's input mappings. This
   is the entire user-facing surface of the feature.
4. **Literal reference rejected** (1 min). The 400 and its message. Nothing to show but the text.
5. **Worker receives resolved values** (2 min). One activation request, a short pause, then the
   resolved values. The pause is the point: the job was parked, the secret was fetched in the
   background, and the same held request picked the job up. Resolution never blocks command
   processing, and the worker never issues a second call.
6. **Nothing leaks** (1.5 min). Operate, the instance's Variables tab, showing
   `Bearer camunda.secrets.apiToken` on screen while the worker already had the real value.
7. **Incident** (1.5 min). Operate, the instance with "Secret resolution error". Read the incident
   message out: it names the recovery path.
8. **Incident recovery** (1.5 min). Either press Retry in Operate or let the script resolve it over
   the API, then the job comes back with the value. No redeploy, no restart.
9. **Resolve endpoint and permissions** (3 min). The longest one. Admin, then denied, then a scoped
   grant, then the mixed batch. The point to land: `ACCESS_DENIED` even for a reference that does
   not exist, because authorization runs before the store lookup.
10. **Listing endpoint** (1.5 min). Three callers, three different answers, and `tls.crt` absent
    from all of them.
11. **Cluster variable carrying a reference** (2 min). The model names no secret at all. Show
    `models/cluster-variable-process.bpmn` next to the cluster variable's stored value.

Total: roughly 18 minutes at a calm pace, plus the framing.

## If you would rather drive it by hand

The numbered scripts under [scripts/](scripts) are what the driver calls, and each one is usable on
its own. [README.md](README.md) documents the manual sequence, including the keys you have to carry
between steps by hand.
