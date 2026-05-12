# `camunda.processInstance` Input-Mapping Context

This document specifies the `camunda.processInstance` FEEL expression context, available in input
mapping expressions on any BPMN flow node. The context currently exposes two properties — `key` and
`businessId` — letting a model reference attributes of the running process instance without
relying on a service task or worker to inject them.

The feature implements the input-mapping slice of the broader
[`camunda.*` expression namespace tracked in #10987](https://github.com/camunda/camunda/issues/10987).
Other contexts in that issue (`camunda.process`, `camunda.userTask`, …) are intentionally out of
scope here.

## Why

Two recurring needs from the field:

- **Pass the process instance key as a technical correlation reference** — e.g., as a variable on
  an outgoing job, in a notification email, or as the message correlation key in a callback.
  Without this context, users had to introduce a service task that copies the key from the
  execution into a process variable.
- **Derive child-instance business ids from the parent's** — modelers can compose ids like
  `=camunda.processInstance.businessId + "-shipment"` instead of generating a fresh unrelated id.

## What

### Surface

| FEEL expression                       | Type     | Resolves to                                                   |
|---------------------------------------|----------|---------------------------------------------------------------|
| `camunda.processInstance.key`         | `number` | The process instance key.                                     |
| `camunda.processInstance.businessId`  | `string` | The business id from `CreateProcessInstance`, or `null` when no business id was set. |

`null` (rather than `""`) for an unset business id lets users distinguish absence from a deliberate
empty value and fall back with FEEL's `or`:

```
camunda.processInstance.businessId or "no-bid"
```

### Where it works

Only in **input mapping expressions** (`zeebe:input` on any flow node — process, sub-process,
activity, event). It is **not** visible in:

- output mappings (`zeebe:output`),
- sequence flow conditions,
- job type / retries / assignee / candidate-groups / due-date expressions,
- listener variable expressions,
- decision references,
- any other FEEL evaluation in the engine.

In those contexts, the path resolves to `null` (or produces a type incident if a non-null type is
required, e.g. for `zeebe:taskDefinition type`).

This narrow scope is deliberate. The broader breakdown in #10987 plans to scope different
`camunda.*` contexts to different expressions (e.g., `camunda.userTask` on output mappings of user
tasks). Leaking `processInstance` everywhere up front would foreclose those decisions.

### Precedence

The lookup order in an input mapping expression is:

1. Body-provided variables (mapping input variables on the record).
2. Process / element instance variables visible from the activating element's scope, walked up the
   scope tree.
3. Tenant-scoped cluster variables.
4. Global cluster variables.
5. The `camunda` namespace (`camunda.vars.*`, `camunda.processInstance.*`).

Process variables therefore **shadow** the namespace. A user who has a process variable called
`camunda` will see that variable, not the namespace — matching the existing precedence for
`camunda.vars`.

## Examples

### Pass the key to a job worker

```xml
<bpmn:serviceTask id="notify">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="notify-worker" />
    <zeebe:ioMapping>
      <zeebe:input source="=camunda.processInstance.key" target="pik" />
    </zeebe:ioMapping>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

The job activation payload will contain `pik` set to the running process instance's key.

### Derive a child id from the parent's business id

```
=camunda.processInstance.businessId + "-shipment-" + string(orderLine)
```

If the parent process was started with `businessId = "ORDER-42"` and `orderLine = 3`, the input
mapping yields `"ORDER-42-shipment-3"`.

### Consistent across nested input mappings

The same `camunda.processInstance.key` resolves to the same value in every input mapping evaluated
within a single process instance, regardless of how deeply nested the activating element is. This is
because resolution looks up the element instance's `ProcessInstanceRecord`, which carries the same
`processInstanceKey` for every element in the instance.

## Design

```
                                  ┌───────────────────────────────────┐
zeebe:input expression ──────────►│ inputMappingExpressionProcessor   │
                                  │   camunda.vars                    │
                                  │   camunda.processInstance         │
                                  └───────────────────────────────────┘

zeebe:output, conditions,         ┌───────────────────────────────────┐
job type, listener expr.  ───────►│ expressionProcessor               │
                                  │   camunda.vars                    │
                                  └───────────────────────────────────┘
```

Two `ExpressionProcessor` instances are constructed in
[`BpmnBehaviorsImpl`](../../zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnBehaviorsImpl.java).
Both share the same `camundaVars` sub-context, so `camunda.vars.*` works everywhere. Only the
input-mapping processor's `camunda` namespace also registers `processInstance`.

[`BpmnVariableMappingBehavior`](../../zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnVariableMappingBehavior.java)
holds both processors; `applyInputMappings` uses the input-mapping one, `applyOutputMappings` keeps
the regular one.

[`ProcessInstanceEvaluationContext`](../../zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/expression/ProcessInstanceEvaluationContext.java)
is a `ScopedEvaluationContext`. On `processScoped(scopeKey)` it looks up the element instance by
key, reads `processInstanceKey` and `businessId` from its `ProcessInstanceRecord`, and stores them.
`getVariable("key")` and `getVariable("businessId")` then return the cached values encoded as
MessagePack.

### Why two processors instead of `ExpressionProcessor.prependContext`?

Prepending a fresh `NamespacedEvaluationContext` with `camunda → processInstance` at input mapping
evaluation time would have shadowed `camunda.vars` — `CombinedEvaluationContext` returns the first
context whose `camunda` lookup yields a non-null result, not a merged view. Building two processors
sharing the inner `camundaVars` keeps the two namespaces co-located in input mappings while keeping
`processInstance` invisible to everything else.

## Testing

[`CamundaProcessInstanceContextTest`](../../zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/expression/CamundaProcessInstanceContextTest.java)
covers:

- `key` resolves at top-level and sub-process input mappings.
- `key` composes with other FEEL operators (`string(...)`, concatenation).
- `businessId` resolves to the value set on `CreateProcessInstance`.
- `businessId` resolves to `null` when none was set.
- `businessId` composes for child-id derivation in a sub-process input mapping.
- The namespace does **not** resolve in output mappings (the mapped variable comes out as `null`).
- The namespace does **not** resolve in a job type expression (results in an incident referring to
  the offending expression).

## Future extensions

Other `camunda.*` contexts queued in #10987 — `camunda.process.{key, id, name, version,
versionTag}`, `camunda.userTask.{assignee, dueDate, followUpDate}`, etc. — are expected to follow
the same pattern: a dedicated `ScopedEvaluationContext` registered into a context-specific
`ExpressionProcessor` for the expression types where it should be visible. Each new context is a
chance to revisit whether the namespace should be added to one of the existing processors or get
its own.
