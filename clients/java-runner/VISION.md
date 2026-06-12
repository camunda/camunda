# LiveBpmn — Vision

> Where this could go beyond the hackday.

## The through-line: **composition + isolation, all as code**

Camunda already had the pieces:

- **BPMN as code** via `zeebe-bpmn-model` (`Bpmn.createExecutableProcess(...)`).
- **Deploy + worker registration** via the Java SDK.
- **Breakpoints** in any worker lambda — that's just Java.

What was missing was the *seam* that unified them and a way to **scope a run to one person**.
Today, two devs both trying out an "order" process on the same cluster collide. An AI agent
iterating on a workflow blows away whatever the previous iteration deployed. There was no
private universe.

LiveBpmn brings two things:

1. **Composition** — process model, worker logic, deployment, cluster, all in one fluent unit
   you can `Run/Debug` from your IDE.
2. **Isolation** — every `.run()` is prefixed with `<user>-<5-char-random>`, pinned to its own
   `processDefinitionKey`, tagged for findability. Two simultaneous runs from two different
   devs, or twenty parallel iterations from one AI agent, never collide.

When the surface is unified *and* every run is private, the IDE becomes the BPM platform — a
process, its workers, its deployment, and its tests are one debuggable artifact. The vision
items below all extend that kernel.

---

## 1. Agentic workflow substrate (the next bet)

**Pitch:** the LLM doesn't just emit BPMN — it emits LiveBpmn Java code, and the lambdas
themselves are tool/LLM calls. The runner becomes the orchestration layer for self-correcting
agentic workflows.

```java
LiveBpmn.createExecutableProcess("research")
    .startEvent()
    .serviceTask("plan",       job -> ai.plan(job.variable("topic", String.class)))
    .serviceTask("search",     job -> ai.searchWeb(job.variable("plan", List.class)))
    .serviceTask("synthesise", job -> ai.synthesise(job.variable("hits", List.class)))
    .endEvent()
    .run(1, cluster);
```

Why this is the right framing:

- **The LLM emits Java, not XML.** Java compiles, type-checks, and runs in the same loop the
  developer is in. XML is opaque to both. The LLM can iterate on the *running* code.
- **Lambdas as tools.** Each service task becomes a tool the LLM can author, register, and invoke.
  The runner's worker pool is the tool-routing layer Camunda already shipped — no new
  infrastructure needed.
- **Self-correcting loops.** The LLM observes `Run.workersHandled()` / incidents / variables, then
  edits its own Java and re-runs. The runner is the verification engine.
- **Audit trail for free.** Every agent decision becomes a process instance in Operate, with full
  variable history. Human-in-the-loop is just adding a user task.

Sketch of what to build:

- `LiveBpmn.fromString(String javaSource)` — compile + load LiveBpmn Java from text. Spike with
  the JDK's `javax.tools` compiler API.
- A small `AgentLoop` driver: `LLM → Java → run → observation → LLM → ...` until a goal predicate
  passes or a budget is hit.
- An example: `LangChain4jLoopDemo` that asks Claude to design and self-correct a flow against a
  test fixture.

This is the keynote-slide demo: an AI designs and runs a workflow end-to-end while the audience
watches the instances move in Operate.

---

## 2. Property-based testing for processes

QuickCheck/Hypothesis, but for BPMN. Generate randomised initial variables, run thousands of
instances against the runner, assert invariants — `total charges == amount * (1 + tax)`, `no
instance lingers in 'review' for more than 60s`, `every approved order has exactly one shipment`.

```java
LiveBpmn.fromFile("order.bpmn")
    .bind("validate", validateLambda)
    .bind("charge",   chargeLambda)
    .bind("ship",     shipLambda)
    .property("totals match",   inst -> inst.var("billed").equals(inst.var("amount")))
    .property("never stuck",    inst -> inst.timeIn("review").compareTo(Duration.ofMinutes(1)) < 0)
    .runMany(10_000, generators, cluster)
    .reportViolations();
```

Surfaces the edge cases nobody would think to enumerate by hand. Every BPMN should have one of
these next to it in CI. Builds straight on top of `RunOptions.variables(IntFunction)`.

---

## 3. The JavaScript sibling — Play with code workers

LiveBpmn is the IDE/runtime answer. Camunda Play is the browser/design-time answer. Today they
don't talk: a designer plays a process in the modeler, then a developer rewrites the worker logic
in Java in a different tool. Two environments, two contexts, two heads.

What if Play grew a worker-code panel where you write JS lambdas alongside the BPMN you're
designing? Same kernel as LiveBpmn, but in the browser:

```javascript
// in Play's worker panel, next to the diagram:
serviceTask('validate', job =>
  job.complete({ valid: job.variable('amount') > 0 })
);
```

A designer can sketch the flow, drop in throwaway worker code, and *see it run on real
instances* — the same way LiveBpmn does for Java devs. The Play UI gains the missing 20%: real
worker logic, not just connector mocks.

This is **a Web Modeler product proposal, not a Java-runner feature.** But it's the natural
companion: same kernel idea, different surface. Worth noting that the architectural choice we
made (lambdas-as-workers) translates trivially across runtimes — Java today, JS in the modeler
tomorrow, Python in a notebook the day after.

---

## Further ideas

Smaller follow-ups, less ambitious than the three above but easy to imagine on top of the kernel:

- **Executable onboarding / runbooks / post-mortems.** Markdown files where the first ```java
  cell is a runnable LiveBpmn snippet. New hires execute, breakpoint, learn by doing instead of
  by reading. Implementation: a tiny `LiveBpmn.fromMarkdown(...)`.
- **JUnit 5 extension `@LiveBpmnTest`.** Each test method gets a scoped `Run` plus assertion
  helpers (`assertCompleted`, `assertNoIncidents`). Replaces docker-compose + camunda-process-test
  boilerplate.
- **`Run.dumpModelTo(Path)`** so the deployed (prefixed) BPMN can be opened in Modeler for
  inspection.
- **Live BPMN file watch + auto-redeploy.** `fromFileWatching(path)` triggers a redeploy on
  every save — designer in Modeler, dev in IDE, instances flow through the new diagram seconds
  later.

## What we explicitly aren't building

- **Production-mirror / observe-only mode.** `c8ctl` already covers this; no need to duplicate.
- **Process REPL (jshell-style).** Considered, dropped — the file-based loop is already fast
  enough, a REPL would add complexity without changing what's possible.
- **Capacity planner / Monte-Carlo simulator.** Niche. The property-based testing path covers the
  most useful slice.
- **Migration / diff tool for BPMN versions.** Real problem, but not adjacent enough to the
  runner kernel — would be its own product.

---

## If we build one thing next

**The agentic substrate.** It reframes LiveBpmn from "developer convenience" to "the runtime
verification layer for AI-designed processes." That's the difference between a hackday tool and
a platform feature.
