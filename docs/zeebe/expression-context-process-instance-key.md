# Spec: `camunda.processInstance.key` in FEEL Expressions

**Scope:** v1 covers the `camunda.processInstance.key` property only. Other `camunda.processInstance` properties (e.g. `parentProcessInstanceKey`) and the full `camunda.process` context are out of scope for this version.

**Availability:** All FEEL expressions evaluated during process instance execution.

---

## What users experience

Process modelers can read the key of the currently executing process instance directly in any FEEL expression evaluated during process instance execution, without needing a service task or job worker to retrieve and store it first.

### Expression

```
camunda.processInstance.key
```

### Value

A number (64-bit integer) that uniquely identifies the process instance. This is the same key visible in Operate and returned by the Create Process Instance API. The value is stable for the entire lifetime of the process instance and does not change.

---

## Where the expression is available

`camunda.processInstance.key` is available in any FEEL expression evaluated while a process instance is executing:

- Input and output mapping expressions
- Sequence flow conditions
- Script task expressions
- Service task, user task, and job type/retries expressions
- Business rule task decision ID expressions
- Call activity process ID expressions
- User task property expressions (assignee, candidate groups/users, due date, follow-up date, priority)
- Execution listener and task listener expressions
- Multi-instance input collection and completion condition expressions
- Timer catch event expressions (intermediate and boundary)
- Message catch event name and correlation key expressions
- Signal catch event name expressions
- Error and escalation code expressions
- Conditional event expressions

It is **not** available in expressions evaluated before a process instance exists:
- Timer start event expressions (cycle, date, duration)
- Top-level conditional start event expressions (no process instance exists at evaluation time)
- Deployment-time expression validation

---

## What the key refers to in different scopes

The key always returns the key of the process instance currently executing the expression — there are no exceptions or special cases.

Embedded subprocesses and multi-instance bodies execute within the same process instance as their surrounding process, so the key is the same throughout. Call activities spawn a separate child process instance, so expressions in the calling process and expressions inside the called process naturally return different keys — each returns the key of the process instance executing it.

|                                        Context                                        |   What `camunda.processInstance.key` returns   |
|---------------------------------------------------------------------------------------|------------------------------------------------|
| Top-level process                                                                     | The key of the process instance                |
| Inside an embedded subprocess                                                         | The key of the same process instance           |
| Inside a multi-instance body                                                          | The key of the same process instance           |
| Expression evaluated in the calling process (e.g. input mapping on the call activity) | The key of the calling process instance        |
| Expression evaluated inside the called process                                        | The key of the called (child) process instance |

To access the calling process instance's key from inside a called process, pass it as an input variable from the call activity. There is no `parentProcessInstanceKey` in this version.

---

## Variable naming

`camunda.processInstance.key` is provided by the engine and is not a process variable. It is not visible in the Variables section in Operate.

The entire `camunda` namespace is reserved. If a process variable named `camunda` exists, that variable is not accessible via its name in expressions where the `camunda` context is available. Avoid naming process variables `camunda`.

---

## Usage examples

### Pass the process instance key to a service task

Input mapping on a service task:

|             Source              |    Target     |
|---------------------------------|---------------|
| `= camunda.processInstance.key` | `instanceKey` |

The job worker receives `instanceKey` as a local variable and can include it in an outgoing request to an external system for correlation.

### Include the key in a notification

In a send task or connector, compose a message body using the key directly:

```
= {
  "subject": "Order update",
  "body": "Your order is being processed (ref: " + string(camunda.processInstance.key) + ")"
}
```

### Store the key as a queryable process variable

To enable Operate queries by process instance key, capture it as a process variable early in the process. Use an output mapping on the start event or an input mapping on the first task:

|             Source              |        Target        |
|---------------------------------|----------------------|
| `= camunda.processInstance.key` | `processInstanceKey` |

After that mapping executes, `processInstanceKey` is a regular process variable visible in Operate and usable in downstream expressions by name.

### Use the key as a unique correlation anchor

When a process must send a message and later receive a correlated reply, the process instance key can serve as a stable correlation value — no extra UUID generation or service task required:

Input mapping on the send task:

```
= { "correlationRef": camunda.processInstance.key, "payload": myData }
```

The receiving end sends back a message correlated on the same value.

---

## Test coverage

The following test cases verify that `camunda.processInstance.key` resolves correctly (or not) in
each expression location. Tests live in
`zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/expression/ProcessInstanceExpressionContextTest.java`.

The test class is structured so that expression-location coverage and the property under test are
orthogonal. When a new `camunda.processInstance` property is added (e.g.
`camunda.processInstance.businessId`), a new parameterized pass over the same set of locations
should be sufficient — no new location tests are needed unless the new property has different
availability semantics.

### Available — key resolves to the process instance key

**Input and output mappings**

1. Input mapping: `= camunda.processInstance.key` mapped to a variable; assert the variable value
   equals the process instance key.
2. Output mapping: `= camunda.processInstance.key` mapped to a variable; assert the variable value
   equals the process instance key.

**Sequence flow condition**

3. `= camunda.processInstance.key > 0` on a sequence flow from an exclusive gateway; assert that
   branch is taken.

**Job type expressions** (service task, script task, business rule task, send task)

4. `= string(camunda.processInstance.key)` as job type; parameterized over service task, script
   task, business rule task, and send task; assert `JobRecord.type` equals
   `String.valueOf(instanceKey)`.

**Job retries expression** (service task, script task, business rule task, send task)

5. `= if camunda.processInstance.key > 0 then 3 else 1` as job retries; parameterized over
   service task, script task, business rule task, and send task; assert retries equals 3.

**Call activity process ID**

6. `= if camunda.processInstance.key > 0 then "child" else "x"` as process ID (with a `child`
   process deployed); assert the child process instance is created.

**Business rule task decision ID**

7. `= if camunda.processInstance.key > 0 then "decision" else "x"` as decision ID (with a
   `decision` DMN deployed); assert the decision is evaluated.

**User task properties**

8. Assignee: `= string(camunda.processInstance.key)`; assert `UserTaskRecord.assignee` equals the
   string key.
9. Candidate groups: `= [string(camunda.processInstance.key)]`; assert candidate groups contains
   the string key.
10. Candidate users: `= [string(camunda.processInstance.key)]`; assert candidate users contains the
    string key.
11. Due date: `= if camunda.processInstance.key > 0 then "2040-01-01T00:00Z" else null`; assert the
    due date is set.
12. Follow-up date: same pattern; assert the follow-up date is set.
13. Priority: `= if camunda.processInstance.key > 0 then 50 else 1`; assert priority equals 50.

**Listener expressions**

14. Execution listener job type: `= string(camunda.processInstance.key)`; assert the listener
    `JobRecord.type` equals the string key.
15. Task listener job type: same as above.

**Multi-instance**

16. Input collection: `= [camunda.processInstance.key]`; assert the loop runs exactly once and the
    element variable equals the key.
17. Completion condition: `= numberOfCompletedInstances = 1 and camunda.processInstance.key > 0`; assert
    the loop completes after one iteration.

**Timer catch events**

18. Intermediate timer catch (duration): `= if camunda.processInstance.key > 0 then "PT0S" else "PT1H"`;
    assert the timer fires immediately.
19. Timer boundary event (duration): same as above.

**Message catch events**

20. Correlation key: `= string(camunda.processInstance.key)`; assert the message subscription is
    created with that correlation key.
21. Message name: `= string(camunda.processInstance.key)`; assert the subscription is created with
    that message name.

**Signal catch event**

22. Signal name: `= string(camunda.processInstance.key)`; assert the signal subscription is created
    with that name.

**Error and escalation**

23. Error boundary event code: `= string(camunda.processInstance.key)` on the catch event; throw a
    matching error from a service task; assert the error is caught.
24. Escalation boundary event code: same pattern with escalation.

**Conditional event**

25. Conditional intermediate catch: `= camunda.processInstance.key > 0 and trigger = true`; trigger
    a variable update; assert the condition fires.
26. Event subprocess conditional start: `= camunda.processInstance.key > 0 and trigger = true`;
    trigger a variable update; assert the event subprocess start event is activated (key resolves
    to the enclosing process instance key).

**Scope semantics**

27. Embedded subprocess — same key: input mapping on a task inside an embedded subprocess uses
    `= camunda.processInstance.key`; assert the variable value equals the enclosing process
    instance key (same key, not a sub-scope key).
28. Call activity — expression in the calling process: input mapping on the call activity uses
    `= camunda.processInstance.key`; assert the variable value equals the calling process instance
    key.
29. Call activity — expression in the called process: input mapping on the first task inside the
    called process uses `= camunda.processInstance.key`; assert the variable value equals the child
    process instance key, which differs from the calling process instance key captured in case 28.

### Not available — before a process instance exists

These expression locations are all evaluated in a context where no process instance exists yet —
timer start events are scheduled at deployment time, top-level conditional start events are checked
at process-definition level, and deployment-time expression validation runs before any instance is
created. In all cases, `camunda.processInstance.key` resolves to `null` — it does not cause an
evaluation error. No dedicated test exists for the top-level conditional start event null case; the
null-outside-context behavior is already demonstrated by the timer start event tests (30–32).

30. Timer start event (duration): `= if camunda.processInstance.key = null then "PT1S" else "PT1H"`;
    assert the timer fires via the 1-second (null) path.
31. Timer start event (cycle): same pattern; assert the cycle timer schedules via the null path.
32. Timer start event (date): `= if camunda.processInstance.key = null then "2040-01-01T00:00Z" else "2020-01-01T00:00Z"`;
    assert the timer is scheduled for the far-future date (null path).
33. Deploy a process that contains `= camunda.processInstance.key` in an input mapping; assert the
    deployment succeeds (the engine does not reject expressions referencing
    `camunda.processInstance.key` at deployment time).

