# Events

This is a description of events and event scopes. It combines a summary of the BPMN 2.0 specification with how they work in Zeebe.

In BPMN, **events** are flow nodes that represent that something can happen that affects the flow of the process.

There are three main types of events:
- **start events**, i.e. a start of a process
- **end events**, i.e. the end of a path in a process
- **intermediate events**, i.e. indicates where something can happen between the start and end of a process; intermediate events attached to an activity boundary are called boundary events

and two different flavors of events:
- **catch events**, i.e. events that catch a trigger
- **throw events**, i.e. events that throw a trigger (sometimes referred to as a result)

See [BPMN (v2.0.2): 10.5 Events](https://www.omg.org/spec/BPMN/2.0.2/PDF#10.5%20Events) for more details about events.

## Triggers

So events represent where in the process "something" can happen. This "something" is called a **trigger**. When a *trigger occurs*, the engine *forwards the trigger* to the catch event. The spec also refers to this as *triggering the catch event*.

According to the spec, there are multiple ways to **forward a trigger**:
- publication, e.g. when a message is published it can be correlated to an event
- direct resolution, e.g. timer triggers are implicitly thrown
- propagation, i.e. forwarded from the location where the event has been thrown to the innermost enclosing scope instance (we'll discuss scopes later) with an attached event able to catch the trigger, e.g. throwing and catching a bpmn error
- cancellation, e.g. a termination of the process instance (not handled as an event trigger in Zeebe)
- compensation, not yet available in zeebe
- (Zeebe also considers job completion as a trigger that is forwarded to the respective job worker task, but this is not part of the BPMN spec)

When a trigger is forwarded to a catch event, it has some effect in the process. Depending on the type, the catch event is activated (e.g. start event) or completed (if already active, e.g. non-boundary intermediate event) and the process execution can continue.

In addition, some catch events that had a trigger forwarded can **interrupt** an active flow. An interrupted active flow is *terminated*. Catch events will only interrupt the active flow if they are *interrupting*. Catch events that are *non-interrupting* won't interrupt the active flow. The following catch events can be *interrupting* or *non-interrupting*, all others are *non-interrupting*:
- start event of event sub-processes, i.e. when triggered it interrupts the (sub-)process encompassing the event sub-process
- boundary event, i.e. when triggered it interrupts the activity to which it is attached

See [BPMN (v2.0.2): 10.5.1 Concepts](https://www.omg.org/spec/BPMN/2.0.2/PDF#10.5.1%20Concepts) for more details about triggers.

See [BPMN (v2.0.2): 10.5.6 Handling Events](https://www.omg.org/spec/BPMN/2.0.2/PDF#10.5.6%20Handling%20Events) for more details on what should happen when a trigger is forwarded to a catch event.

## Scopes

**Scopes** are used to define the semantics of:
- visibility of data, i.e. an activity's visibility of process variables
- event resolution, e.g. error propagation through the enclosing scopes
- starting/stopping of token execution

See [BPMN (v2.0.2): 10.5.7 Scopes](https://www.omg.org/spec/BPMN/2.0.2/PDF#10.5.7%20Scopes) for more details about scopes.

In Zeebe, we refer to scopes in multiple forms (these are not described by the BPMN spec):
- **flow scopes**, i.e. the element encompassing a specific element, aka parent-child relation; e.g. a sub-process is the flow scope of a service task when it directly encompasses this service task. Note, sometimes the indirect encompassing element (non-parent ancestor) is also referred to as flow scope.
- event scopes, described below

### Event scopes

The **event scope** refers to the element in the process that must be *active* in order for a *trigger* to be forwarded to the catch event. For example, we can only forward the trigger to a boundary event if its attached element is *active*. Or in other words, the boundary event is in **scope** when the attached element is *active*.

- For a boundary event, the event scope refers to the element where it is attached to.
- For the start event in an event subprocess, the event scope refers to the flow scope that contains the event subprocess (i.e. the process or the embedded subprocess).
- For an event connected to an event-based gateway, the event scope refers to the event-based gateway.
- For elements that are actively waiting for events (e.g. intermediate catch events), the event scope refers to the element itself.

In Zeebe, we sometimes say that an event scope can be triggered, meaning: a trigger can be forwarded to a catch event.

Event scopes are not entities in Zeebe. Instead, Zeebe only cares about event scope instances.

### Event scope instances

An **event scope instance** is an instance of an event scope. It directly refers to a specific element instance, because it is stored in the state (in the event scope column family) under the key of that element instance. This means, we can directly access the event scope for an element instance. We'll see later what we use this for.

> **Note**  
> Event scope instances are persisted in the state (as `EventScopeInstance`) but not represented on the log stream. In contrast, event triggers are persisted along with the event scope instance in the state, and are represented on the log stream as `ProcessEvent` records.

The engine uses event scope instances:
- ~~to find the relevant catch event when a trigger occurs~~ - It would be reasonable to expect that the engine uses the event scope instance to find the relevant catch event for a trigger, but that is not the case. We'll discuss how this works later.
- to determine whether the trigger can be forwarded to the catch event, e.g. boundary events attached to an activity can no longer be triggered when the activity is already interrupted by an attached boundary event

An event scope can be triggered if no interrupting event was triggered (i.e. it is not interrupted). If an interrupting catch event was triggered then no other event can be triggered, except for boundary events. If an interrupting boundary event was triggered then no other events, including boundary events, can be triggered, i.e. it is not **accepting** any events.

An event scope instance has 4 properties:
- `accepting`, when `false` the event scope instance is not accepting any triggers.
- `interrupted`, when `true` it is interrupted, but may still be accepting events for boundary events.
- `interruptingElementIds`, the element IDs of the catch events that can interrupt the event scope instance. This property doesn't change during the event scope instance's lifetime.
- `boundaryElementIds`, the element IDs of the boundary events that are attached to the event scope instance. This property doesn't change during the event scope instance's lifetime.
