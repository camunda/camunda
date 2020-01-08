# Event Subprocess

An event subprocess is a subprocess that is triggered by an event. It can be added globally to the process or locally inside an embedded subprocess.

![event-subprocess](/bpmn-workflows/event-subprocesses/event-subprocess.png)

An event subprocess must have exactly one start event of one of the following types:

* [Timer](/bpmn-workflows/timer-events/timer-events.html)
* [Message](/bpmn-workflows/message-events/message-events.html)
* [Error](/bpmn-workflows/error-events/error-events.html)

An event subprocess behaves like a boundary event but is inside the scope instead of being attached to the scope. Like a boundary event, the event subprocess can be interrupting or non-interrupting (indicated in BPMN by a solid or dashed border of the start event). The start event of the event subprocess can be triggered when its containing scope is activated.

A non-interrupting event subprocess can be triggered multiple times. An interrupting event subprocess can be triggered only once.

When an interrupting event subprocess is triggered then **all active instances** of its containing scope are terminated, including instances of other non-interrupting event subprocesses.

If an event subprocess is triggered then its containing scope is not completed until the triggered instance is completed.

## Variables

Unlike a boundary event, an event subprocess is inside the scope. So, it can access and modify **all local variables** of its containing scope. This is not possible with a boundary event because a boundary event is outside of the scope.

Input mappings can be used to create new local variables in the scope of the event subprocess. These variables are only visible within the event subprocess.

By default, the local variables of the event subprocess are not propagated (i.e. they are removed with the scope). This behavior can be customized by defining output mappings at the event subprocess. The output mappings are applied on completing the event subprocess.

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>An event subprocess with an interrupting timer start event:

```xml
<bpmn:subProcess id="compensate-subprocess" triggeredByEvent="true">
  <bpmn:startEvent id="cancel-order" isInterrupting="true">
    <bpmn:timerEventDefinition>
      <bpmn:timeDuration>PT5M</bpmn:timeDuration>
    </bpmn:timerEventDefinition>
  ... other elements
</bpmn:subProcess>
```

  </p>
</details>

<details>
	<summary>Using the BPMN modeler</summary>
  <p>Adding an event subprocess with an interrupting timer start event:

![event-subprocess](/bpmn-workflows/event-subprocesses/zeebe-modeler-event-subprocess.gif)

  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of an event subprocess with an interrupting timer start event:

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>
		<tr>
				<td>EVENT_OCCURRED</td>
				<td>five-minutes</td>
				<td>START_EVENT</td>
		</tr>
		<tr>
				<td>ELEMENT_TERMINATING</td>
				<td>fetch-item</td>
				<td>SERVICE_TASK</td>
		</tr>
		<tr>
				<td>...</td>
				<td>...</td>
				<td>...</td>
		</tr>
		<tr>
				<td>ELEMENT_TERMINATED</td>
				<td>fetch-item</td>
				<td>SERVICE_TASK</td>
		</tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>compensate-subprocess</td>
        <td>SUB_PROCESS</td>
    </tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>compensate-subprocess</td>
        <td>SUB_PROCESS</td>
    </tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>five-minutes</td>
        <td>START_EVENT</td>
    </tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    </tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>order-cancelled</td>
        <td>END_EVENT</td>
    </tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>compensate-subprocess</td>
        <td>SUB_PROCESS</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>compensate-subprocess</td>
        <td>SUB_PROCESS</td>
    <tr>
		<tr>
				<td>ELEMENT_COMPLETING</td>
				<td>order-process</td>
				<td>PROCESS</td>
		</tr>
		<tr>
				<td>ELEMENT_COMPLETED</td>
				<td>order-process</td>
				<td>PROCESS</td>
		</tr>
</table>

  </p>
</details>

References:
* [Embedded Subprocess](/bpmn-workflows/embedded-subprocesses/embedded-subprocesses.html)
* [Variable Scopes](/reference/variables.html#variable-scopes)

