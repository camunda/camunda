# LiveBpmn — Hackday Demo Pack

> Three-minute pitch + transcript + slide content + the eye-openers we could add to push it
> further in the time remaining.

## The 30-second pitch

> Camunda already has the pieces — BPMN-as-code, an SDK that deploys, workers you can debug. But
> they live in different files, different threads, different intent. **LiveBpmn composes them
> into one runnable unit, and scopes every run to you alone.** A dev, a designer, or an AI can
> iterate on a process — define it, run it, debug it, throw it away — without ever touching
> production or stepping on a colleague.

## Slide content (drop these on slides 1–4)

### Slide 1 — *the problem isn't tools, it's composition + isolation*
> The pieces are already there:
>
> - **BPMN as code** — `zeebe-bpmn-model` (`Bpmn.createExecutableProcess(...)`).
> - **Deploy + workers** — the Java SDK (`client.newDeployResourceCommand()`, `newWorker()`).
> - **Lambda breakpoints** — your IDE.
>
> What's missing: a single source of truth that ties them together. One file you can read
> top-to-bottom, hit Run, and tweak — no Modeler-then-deploy-then-worker dance, no "where
> do I even start," no separate project just to try an idea.

### Slide 2 — *the kernel*
> **Everything as code. Debuggable end-to-end.**
>
> ```
> LiveBpmn.createExecutableProcess("order")
>     .startEvent()
>     .serviceTask("validate", job -> Map.of("valid", true))
>     .serviceTask("ship", job -> { /* ← breakpoint */ })
>     .endEvent()
>     .run(5, cluster);
> ```

### Slide 3 — *what just happened*
> 30 seconds. Java file → `main()` → Camunda 8 cluster → 5 instances → 5 breakpoint hits →
> Operate URL printed. No `mvn deploy`, no docker-compose, no separate worker app.

### Slide 4 — *what's next*
> - AI writes the Java; the runner verifies by execution.
> - Property-based testing for processes.
> - JUnit / `camunda-process-test` integration — same DSL, assertion-grade.
>
> *Side note:* ran into two upstream bugs while building this (gateway NPE on
> task-listener activation; `/v2/process-instances/search` rejecting `CANCELED`).
> Reproducers + suggested fixes in [`UPSTREAM_BUGS.md`](UPSTREAM_BUGS.md).
>
> **(See [VISION.md](VISION.md).)**

## Demo prep checklist (do this 5 minutes before)

1. **Start a local Camunda 8 cluster** so the demo doesn't wait on container boot. Either:
   - `cd c8run && ./c8run start` (preferred — faster, simpler), or
   - `docker compose up -d` with the snippet from [README.md](README.md).

   Verify:
   ```bash
   curl -fsS http://localhost:8080/v2/topology > /dev/null && echo "ready"
   ```

2. **Open Operate** at <http://localhost:8080/operate> — leave it on screen 2.

3. **Confirm the examples point at the local cluster.** All three (`MinimalDemo`, `OrderDemos`,
   `LoadDemo`) use `LiveBpmn.cluster().localhost()` already. If the demo machine has no local
   cluster, swap to `.testcontainer()` and accept the boot wait.

4. **Pre-warm the JVM.** Run `MinimalDemo.main()` once before the audience arrives so the JIT
   warmups don't add visible latency mid-demo.

## 3-minute live demo transcript

**Beat 1 — `MinimalDemo.java` (0:00 → 0:40)**

> "Smallest demo first. Twelve lines of Java. One service task, one lambda. The lambda calls
> `job.fail` if there's no name, otherwise `job.complete` with the greeting. Standard Java."
>
> *Set a breakpoint inside the lambda. Right-click → Run.*
>
> "Connects to my local cluster — instant. The runner registers our lambda as a real Camunda
> worker, the broker activates a job, breakpoint hits *inside the IDE*. `job.variable(...)` —
> that's the variable our previous task wrote. Step out, instances complete."

**Beat 2 — `OrderDemos.java` (0:40 → 1:30)**

> "Now a real flow. Validate, charge, ship. Same `main()` runs in two modes — inline lambdas
> at the top, binding API at the bottom. Same handlers, same Operate output, two ways to wire."
>
> *Run with default (inline). Click the `Operate:` URL in console.*
>
> "There's our run in Operate, three instances completing. The elementIds —
> `validate`, `charge`, `ship` — stay clean."

**Beat 3 — `ListenerDemo.java` (1:30 → 2:15)**

> "Listeners, same `.on(...)` surface. Execution listeners on a service task —
> `start` and `end` — plus task listeners on a user task — `assigning`, `completing`. Same
> lambda shape as workers, debuggable the same way."
>
> *Run; show the interleaved `[el-*]` / `[tl-*]` log lines.*
>
> "Lifecycle hooks become Java lambdas. No separate worker app, no listener configuration
> file — they're sitting in the same builder as the tasks themselves."

**Beat 4 — `LoadDemo.java` (2:15 → 2:40)**

> "Same idea, scaled. Fifty instances, paced 100 ms apart. `RunOptions.of(50).pacing(...)`.
> Watch them trickle into Operate."
>
> *Open Operate; show the moving picture.*

**Beat 5 — close on the vision (2:40 → 3:00)**

> "Two things make this matter. **Composition** — Camunda already had BPMN-as-code, deploy,
> workers, breakpoints. They were in different files. Now they're in one. **Isolation** — every
> run gets a per-user prefix. Two devs, two tries, an AI agent, all on the same cluster, none
> stepping on each other.
>
> Where this goes: AI doesn't emit BPMN we have to deploy — it emits *this* Java directly,
> runs it, observes the output, self-corrects. The runner is the verification engine. Plus
> property-based testing for processes — QuickCheck-for-workflows. When everything is code and
> debuggable, the IDE becomes the BPM platform."

## Eye-opener features to add for the demo

Ranked by demo punch per hour of work. Pick the ones that fit the time budget:

### 🔥 Tier 1 — Big "wow" per LOC

**1. Live BPMN file watch + auto-redeploy** *(~1 hour)*
`LiveBpmn.fromFileWatching(Path)` — polls the file for mtime changes; on save, redeploys with the
new model and re-binds. Open Modeler, save, watch instances flow through the new diagram in
seconds. The "process design as a conversation" beat.

**2. AI loop with `LiveBpmn.fromString(javaSource)`** *(~2 hours, with a stub LLM)*
JDK `javax.tools.JavaCompiler` to compile a string of Java into a `LiveBpmn` invocation. Wire to
a tiny `AgentLoop` that asks Claude API to design a flow, runs it, feeds back results, asks for
revisions. Even with a hardcoded fake LLM (just rotates between three handlers), this is the
keynote-slide demo — *generated code runs and self-corrects in front of the audience*.

**3. Auto-open Operate URL in the default browser** *(~5 minutes)*
`Desktop.getDesktop().browse(URI)` after `createInstance`. Audience sees the browser pop open
with their run already loaded.

### ⭐ Tier 2 — Solid amplifiers

**4. ASCII process visualisation in the console** *(~45 minutes)*
After deploy, render the topology as a quick ASCII diagram with the prefixed elementIds.
Reinforces "we deployed exactly this" vs. opaque XML.

**5. Live worker stats during run** *(~30 minutes)*
Background scheduler that prints `[stats] validate=12/50 charge=8/50 ship=4/50 in-flight=3` every
2 s while `await()` is blocked. Makes long runs feel responsive instead of silent.

**6. `Run.dumpModelTo(Path)`** *(~15 minutes)*
Writes the prefixed BPMN to disk so the audience can open it in Modeler and *see* the prefix
rewrite. Concretises what's otherwise abstract.

### 🎯 Tier 3 — Polish

**7. JUnit 5 extension `@LiveBpmnTest`** *(~1 hour)*
Each test method gets a scoped `Run` and `cluster`; assertion helpers for `runCompleted`,
`incidentsEmpty`, `instances == n`. Replaces docker-compose + camunda-process-test boilerplate
with a one-liner. Demo the same flow as a passing JUnit test in a CI run.

**8. `LiveBpmn.fromMarkdown(Path)`** *(~30 minutes)*
Extract the first ` ```java ` fenced block and execute it. Onboarding markdown that literally
runs. Tied to VISION #3.

**9. `LiveBpmn.observeOperate(processId, cluster)`** *(~45 minutes)*
Read-only attach to an existing deployed process via the SDK search API; same `Run`-style
counters but on whatever's already running. Bridges to the production-mirror story without
building a real one.

### 🚀 Tier 4 — Ambitious / multi-hour

**10. Hot-swap a worker mid-run** *(~3 hours)*
Cancel an existing worker subscription, register a new lambda for the same jobType, watch
in-flight jobs continue with the old logic but new ones use the new logic. Demo during a paced
run: edit the lambda → save → next instance picks up the change. Drama.

**11. Embedded Operate-lite TUI** *(~4 hours)*
A `Run.dashboard()` that draws an ANSI-coloured live view (instance grid, per-task counts,
incidents, throughput sparkline). The runner's own dashboard, no browser needed.

## Recommendation for the 3-minute slot

Lock in tiers 1-3, in order of confidence: **3 (auto-open URL)**, **6 (dump prefixed BPMN)**,
**5 (live stats)**. Three small features, total ~50 minutes of work, all visibly active during
the demo. Tier 1's live-file-watch (#1) is the showstopper if you have an hour to spare. The AI
loop (#2) is a separate demo that needs its own slot — too rich to cram into 3 minutes alongside
the basics.

## Things to NOT do for this demo

- Don't open Modeler. Costs context-switch time, audience doesn't know what they're looking at,
  and the runner story is the IDE story.
- Don't show the full BPMN XML. The point of LiveBpmn is that you don't have to look at it.
- Don't do `LoadDemo` with 1000 instances. Visually overwhelming and the broker takes seconds.
  50 paced is the sweet spot.
- Don't try to live-debug a bug. Murphy's law has bigger plans for that.
