# Avoid DependsOn
`avoid_depends_on`

**Rule:** Use `dependsOn` only for lifecycle tasks that have no actions. Between tasks that do work, declare inputs and outputs and let Gradle infer the ordering.

---

- **Fix:** Branch on whether an artifact actually flows between the two tasks.

  **An artifact flows** (the depended-on task writes something the depender reads) — wire the producer's output into the consumer's input: `inputs.file(tasks.named<Producer>("produce").map { it.outputFile })`, or `from(tasks.named(...))` for a copy-like task. Delete the `dependsOn`.

  **Nothing flows and the edge is pure ordering** — both tasks touch the same file, or the first only prints. Input wiring cannot express this and is not the fix. Choose by what the ordering must guarantee:

  | The follow-up must… | Use |
  |---|---|
  | always happen as part of the second task | fold the first task's logic into the second — extract it into a class or function both call, then delete the first task's edge. Sharing an implementation is reuse, not duplication. |
  | always trail the first whenever the first runs | `finalizedBy` on the producer |
  | only be ordered when both are already in the graph | `mustRunAfter` |

  **Do not record this as unfixable.** "Removing it would change behavior" or "the other task's output would have to be duplicated" means a branch above was skipped — the fold row exists for exactly that case.
- **Don't:**

  ```kotlin
  // ...
  tasks.register<SimpleTranslationTask>("translateBad") {
      dependsOn(tasks.named("helloWorld"))
  }
  ```

- **Do:**

  ```kotlin
  // ...
  tasks.register<SimpleTranslationTask>("translateGood") {
      inputs.file(tasks.named<SimplePrintingTask>("helloWorld").map { messageFile })
  }
  ```
