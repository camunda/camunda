# `camunda.processInstance` Expression Context

This document specifies the `camunda.processInstance` FEEL expression context. It is available in
any FEEL expression evaluated by the engine within a running process instance — input and output
mappings, sequence-flow conditions, job-type/retries/assignee/due-date expressions, listener
expressions, multi-instance collections, and so on. The context currently exposes two properties —
`key` and `businessId` — letting a model reference attributes of the running process instance
without relying on a service task or worker to inject them.

The feature implements the first slice of the broader
[`camunda.*` expression namespace tracked in #10987](https://github.com/camunda/camunda/issues/10987).
Other contexts in that issue (`camunda.process`, `camunda.userTask`, …) are out of scope here.

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

The namespace is registered globally for the engine's FEEL evaluation, so it resolves wherever the
engine evaluates an expression in the scope of a running process instance. Example sites:

- input mappings (`zeebe:input`) and output mappings (`zeebe:output`),
- sequence-flow conditions,
- job type / retries / assignee / candidate-groups / due-date expressions,
- execution and task listener variable expressions,
- multi-instance input collections and completion conditions,
- signal names and message correlation key expressions,
- decision references (`zeebe:calledDecision`).

The only remaining limitation: evaluations that are **not** tied to a running process instance —
for example, ad-hoc decision evaluation via the API outside of any process — have no element-
instance scope to look up. In those cases `camunda.processInstance.key` and
`camunda.processInstance.businessId` both resolve to `null`, because
`ProcessInstanceEvaluationContext` short-circuits when `processInstanceKey < 0`.

### Precedence

The lookup order in an expression is:

1. Body-provided variables (e.g. mapping input variables on the record).
2. Process / element instance variables visible from the activating element's scope, walked up the
   scope tree.
3. Tenant-scoped cluster variables.
4. Global cluster variables.
5. The `camunda` namespace (`camunda.vars.*`, `camunda.processInstance.*`).

Process variables therefore **shadow** the namespace. A user who has a process variable called
`camunda` will see that variable, not the namespace — matching the existing precedence for
`camunda.vars`.

## Examples

### Pass the key to a job worker (input mapping)

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

### Use the key in a job type expression

```xml
<bpmn:serviceTask id="route">
  <bpmn:extensionElements>
    <zeebe:taskDefinition
      type="=&quot;worker-&quot; + string(camunda.processInstance.key)" />
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

The engine activates a job whose type embeds the process instance key — useful for routing or for
isolating workers per instance. The same pattern works for sequence-flow conditions
(`=camunda.processInstance.key = parentPik`), due-date expressions, and any other engine FEEL site.

### Consistent across the whole instance

The same `camunda.processInstance.key` resolves to the same value in every expression evaluated
within a single process instance, regardless of how deeply nested the activating element is. This is
because resolution looks up the element instance's `ProcessInstanceRecord`, which carries the same
`processInstanceKey` for every element in the instance.

## Design

```
all engine FEEL expressions ─────►┌───────────────────────────────────┐
(input/output mappings,           │ expressionProcessor               │
 conditions, job type,            │   camunda.vars                    │
 listeners, …)                    │   camunda.processInstance         │
                                  └───────────────────────────────────┘
```

A single `ExpressionProcessor` is constructed in
[`BpmnBehaviorsImpl`](../../zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnBehaviorsImpl.java).
Its `camunda` namespace registers both `vars` and `processInstance`, so every consumer of the
processor — `BpmnVariableMappingBehavior` for input and output mappings, `ExpressionBehavior` for
everything else — sees the same namespace.

[`ProcessInstanceEvaluationContext`](../../zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/expression/ProcessInstanceEvaluationContext.java)
is a `ScopedEvaluationContext`. On `processScoped(scopeKey)` it looks up the element instance by
key, reads `processInstanceKey` and `businessId` from its `ProcessInstanceRecord`, and stores them.
`getVariable("key")` and `getVariable("businessId")` then return the cached values encoded as
MessagePack. When `processInstanceKey < 0` — i.e. the evaluation is not tied to a running instance —
the context short-circuits and both properties resolve to `null`.

## Testing

[`CamundaProcessInstanceContextTest`](../../zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/expression/CamundaProcessInstanceContextTest.java)
covers:

- `key` resolves at top-level and sub-process input mappings.
- `key` composes with other FEEL operators (`string(...)`, concatenation).
- `businessId` resolves to the value set on `CreateProcessInstance`.
- `businessId` resolves to `null` when none was set.
- `businessId` composes for child-id derivation in a sub-process input mapping.
- The namespace resolves in output mappings.
- The namespace resolves in job type expressions.
- The namespace resolves in sequence-flow conditions.

## Future extensions

Other `camunda.*` contexts queued in #10987 — `camunda.process.{key, id, name, version,
versionTag}`, `camunda.userTask.{assignee, dueDate, followUpDate}`, etc. — may want narrower
scoping than `processInstance`. `camunda.userTask`, for instance, only makes sense within a
user-task lifecycle and should not leak into unrelated expressions. Each new context is a chance
to decide whether to extend the global `camunda` namespace or register a scoped variant for
specific expression types.
