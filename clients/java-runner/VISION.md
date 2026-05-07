# LiveBpmn — Vision

> Where this could go beyond the hackday.

## The through-line: **everything as code, debuggable end-to-end**

Today, BPMN sits in a `.bpmn` file. Workers sit in a service. Deployments sit in a CI pipeline.
Tests sit in another module. Onboarding sits in Confluence. Each one a different surface, a
different tool, a different mental context. None of them debuggable from a single breakpoint.

LiveBpmn collapses all of it into one runnable Java file:

- The **process model** is code — the fluent chain or `fromFile(...)` adoption.
- The **worker logic** is code — lambdas with IDE breakpoints.
- The **deployment** is code — `.run(N, cluster)` is the deployer.
- The **cluster** is code — `cluster().testcontainer()` boots one.
- The **test** is code — same `main()` you ran becomes an `@Test` with `runner.assertCompleted(...)`.
- The **tutorial** is code — onboarding markdown with executable cells.
- The **agent** is code — generated, runnable, observable, correctable.

When everything is code and debuggable, *the IDE becomes the BPM platform*. Stack traces cross
worker / process / cluster boundaries unbroken. You can step from a service-task lambda into a
gateway-condition evaluation into the SDK call into the broker response. There is one truth.

The vision items below all extend the same kernel: once that gap is gone, what becomes possible
that wasn't before?

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

## 3. Executable onboarding & runbooks

Today: an "Onboarding" wiki page with diagrams the new hire can't actually run. A Confluence
runbook with a screenshot of Operate from a year ago. A post-mortem with a stack trace and no way
to reproduce.

Tomorrow: every one of those is an executable Markdown file. New team member opens
`Onboarding.md` in IntelliJ, runs cells inline, sees the order flow execute, sets a breakpoint in
cell 3, sees how an incident is created in cell 5. Onboarding becomes *practice* instead of
*reading*.

```markdown
## Day 1: How an order moves through our system

```java
LiveBpmn.fromFile("orders.bpmn")
    .bind("validate", validate)  // ← step into this on cell 3
    .bind("charge",   charge)
    .bind("ship",     ship)
    .run(3, cluster).await();
```

Click "Run" on this cell. You should see three orders complete. Now break on `charge` and
inspect `paymentMethod` for instance 3 — it should be `credit-card` because amount ≥ 500.
```

Same pattern works for runbooks (`IncidentX.md` reproduces the failure with one click) and
post-mortems (`PR-2378-postmortem.md` runs the actual broken flow against the actual fixed flow,
side by side). Knowledge that exists only as static text becomes knowledge that *runs*.

Implementation is light: integrate with IntelliJ's Markdown plugin or build a tiny
`LiveBpmn.fromMarkdown(...)` that extracts the first ```java fenced block. No new platform
needed.

---

## 4. The JavaScript sibling — Play with code workers

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
