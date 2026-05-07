# LiveBpmn — Hackday Demo Pack

> Three-minute pitch + transcript + slide content + the eye-openers we could add to push it
> further in the time remaining.

## The 30-second pitch

> **Process automation just got the IDE treatment.** Define a BPMN process, plug in workers as
> Java lambdas, hit run — your file deploys to a real Camunda 8 cluster, fires N instances, and
> your IDE breakpoints fire inside the workers. One file, one keystroke, one truth — process,
> workers, deployment, cluster, all as code and debuggable end-to-end.

## Slide content (drop these on slides 1–4)

### Slide 1 — *the problem*
> Today: BPMN file. Worker service. CI pipeline. Test module. Wiki page. Five surfaces. Five
> tools. None debuggable end-to-end.

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
> AI-generated Java that the runner verifies by execution. Property-based tests for processes.
> Markdown onboarding that runs. The IDE becomes the BPM platform. **(See VISION.md.)**

## 3-minute live demo transcript

**Beat 1 — open `MinimalDemo.java` (0:00 → 0:30)**

> "Smallest demo first. Twelve lines of Java. One service task, one lambda. The lambda calls
> `job.fail` if there's no name, otherwise `job.complete` with the greeting. Standard Java —
> nothing exotic."
>
> *Right-click → Run.*
>
> "JVM starts, container boots — that's slf4j-simple streaming Camunda's startup logs into our
> console. Twenty seconds. Watch."

**Beat 2 — set a breakpoint, re-run (0:30 → 1:15)**

> *Set a breakpoint inside the `print` task lambda.*
>
> "Now I'm going to set a breakpoint inside the `print` task. Re-run."
>
> *Wait for first breakpoint hit.*
>
> "There. The runner registered our lambda as a real Camunda worker, the broker activated a
> job, and we're sitting inside our IDE on a real production-grade execution. `job.variable("greeting", String.class)` — that's the variable our previous task wrote. Step out, three more
> hits — there's a fail in the middle one because of an empty name."

**Beat 3 — open `OrderDemos.java`, switch to bindings (1:15 → 2:00)**

> "Now a real flow. Validate, charge, ship. Same `main()` runs in two modes — inline lambdas
> at the top, binding API at the bottom. Same handlers, same Operate output, two ways to wire."
>
> *Run with default (inline). Show the prefixed `Operate:` URL in console.*
>
> "Click the URL — there's our run in Operate, three instances completing, prefixed with my
> username and a random suffix so two devs running this at the same time don't collide. The
> elementIds — `validate`, `charge`, `ship` — stay clean."

**Beat 4 — `LoadDemo.java` (2:00 → 2:30)**

> "Same idea, scaled. Fifty instances, paced 100 ms apart. `RunOptions.of(50).pacing(...)`.
> Watch them trickle into Operate."
>
> *Open Operate; show the moving picture.*

**Beat 5 — close on the vision (2:30 → 3:00)**

> "That's the kernel: process and workers and deployment and cluster, all as code, all
> debuggable in one place. Three things this unlocks. **One** — AI doesn't generate BPMN
> files we have to deploy; it generates this Java directly, runs it, observes the output, and
> self-corrects. The runner is the verification engine. **Two** — property-based testing for
> processes; QuickCheck-for-workflows. **Three** — onboarding markdown that runs. New hire
> opens `Day1.md`, executes cells inline, sees real instances flow.
>
> When everything is code and debuggable, the IDE becomes the BPM platform."

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
