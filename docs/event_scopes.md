# Event scopes
To understand what event scopes are, we first need to understand what events are. This description provides a summary of the BPMN 2.0 specification and combines it with how it works in Zeebe.

In BPMN, **events** are flow nodes that represent that something can happen that affects the flow of the process.

There are three main types of events:
- **start events**, i.e. a start of a process
- **end events**, i.e. the end of a path in a process
- **intermediate events**, i.e. indicates where something can happen between the start and end of a process; intermediate events attached to an activity boundary are called boundary events

and two different flavors of events:
- **catch events**, i.e. events that catch a trigger
- **throw events**, i.e. events that throw a trigger (sometimes referred to as a result)

So events represent where in the process "something" can happen. This "something" is called a **trigger**. When a *trigger occurs*, the engine *forwards the trigger* to the catch event. The spec also refers to this as *triggering the catch event*.

There are multiple ways to **forward a trigger**:
- publication, e.g. when a message is published it can be correlated to an event
- direct resolution, e.g. timer triggers are implicitly thrown
- propagation, i.e. forwarded from the location where the event has been thrown to the innermost enclosing scope instance (we'll discuss scopes later) with an attached event able to catch the trigger, e.g. throwing and catching a bpmn error
- cancellation, e.g. a termination of the process instance
- compensation, not yet available in zeebe

When triggered, events can **interrupt** an active flow. The following events are able to interrupt:
- start event of event sub-processes, i.e. when triggered it terminates the (sub-)process encompassing the event sub-process
- boundary event, i.e. when triggered it terminates the activity to which it is attached

**Scopes** are used to define the semantics of:
- visibility of data, i.e. an activity's visibility of process variables
- event resolution, e.g. error propagation through the enclosing scopes
- starting/stopping of token execution

In Zeebe, we refer to scopes in multiple forms:
- **flow scopes**, i.e. the element encompassing a specific element, aka parent-child relation; e.g. a sub-process is the flow scope of a service task when it directly encompasses this service task. Note, sometimes the indirect encompassing element (non-parent ancestor) is also referred to as flow scope.
- event scopes, described below

The **event scope** refers to the element in the process that must be *active* in order for an occurred *trigger* to be forwarded to the catch event. For example, the boundary event is in **scope** when the attached element is *active*. In Zeebe, we sometimes say that an event scope can be triggered, meaning: a trigger can be forwarded to a catch event possibly interrupting the active activity.
- For a boundary event, the event scope refers to the element where it is attached to.
- For an event subprocess, the event scope refers to the flow scope that contains it (i.e. the process or the embedded subprocess).
- For an event connected to an event-based gateway, the event scope refers to the event-based gateway.
- For elements that are actively waiting for events (e.g. intermediate catch events), the event scope refers to the element itself.

An **event scope instance** is an instance of an event scope. It refers to a specific element instance for a specific catch event instance. Event scope instances are persisted in the state (as `EventScopeInstance`) but not represented on the log stream. Event triggers are persisted along with the event scope instance in the state, and are represented on the log stream as `ProcessEvent` records.

The engine uses event scope instances:
- to find the relevant catch event when a trigger occurs
- to determine whether the catch event can be triggered, e.g. boundary events attached to an activity can no longer be triggered when the activity is already interrupted by an attached boundary event

An event scope can be triggered if no interrupting event was triggered (i.e. it is not interrupted). If an interrupting catch event was triggered then no other event can be triggered, except for boundary events. If an interrupting boundary event was triggered then no other events, including boundary events, can be triggered, i.e. it is not **accepting** any events.

An event scope instance has 4 properties:
- `accepting`, it is not accepting any events
- `interrupted`, it is interrupted, but may still be accepting events for boundary events
- `interruptingElementIds` TBD
- `boundaryElementIds` TBD

