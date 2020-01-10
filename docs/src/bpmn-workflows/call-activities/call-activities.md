# Call Activities

A call activity (aka reusable subprocess) allows to call/invoke another workflow as part of this workflow. It is similar to an [embedded subprocess](/bpmn-workflows/embedded-subprocesses/embedded-subprocesses.html) but the workflow is externalized (i.e. stored as separated BPMN) and can be invoked by different workflows.

![call-activity](/bpmn-workflows/call-activities/call-activities-example.png)

When a call activity is entered then a new workflow instance of the referenced workflow is created. The new workflow instance gets activated at the **none start event**. The workflow can have start events of other types but they are ignored.

When the created workflow instance is completed then the call activity is left and the outgoing sequence flow is taken.

## Defining the Called Workflow

A call activity must define the BPMN process id of the called workflow as `processId`. The `processId` defines the BPMN process id statically on the design time of the workflow.

The new instance of the defined workflow will be created of its **latest version** - at the point when the call activity is activated.

## Boundary Events

![call-activity-boundary-event](/bpmn-workflows/call-activities/call-activities-boundary-events.png)

Interrupting and non-interrupting boundary events can be attached to a call activity.

When an interrupting boundary event is triggered then the call activity **and** the created workflow instance are terminated. The variables of the created workflow instance are not propagated to the call activity.

When an non-interrupting boundary event is triggered then the created workflow instance is not affected. The activities at the outgoing path have no access to the variables of the created workflow instance since they are bounded to the other workflow instance.

## Variable Mappings

When the call activity is activated then **all variables** of the call activity scope are copied to the created workflow instance.

Input mappings can be used to create new local variables in the scope of the call activity. These variables are also copied to the created workflow instance.

By default, all variables of the created workflow instance are propagated to the call activity. This behavior can be customized by defining output mappings at the call activity. The output mappings are applied on completing the call activity.

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>A call activity with static process id:

```xml
<bpmn:callActivity id="task-A" name="A">
  <bpmn:extensionElements>
    <zeebe:calledElement processId="child-process-id" />
  </bpmn:extensionElements>
</bpmn:callActivity>
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding a call activity with static process id:

![call-activity](/bpmn-workflows/call-activities/bpmn-modeler-call-activity.gif)

  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of a call activity:

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>task-a</td>
        <td>CALL_ACTIVITY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>task-a</td>
        <td>CALL_ACTIVITY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>child-process-id</td>
        <td>PROCESS</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>child-process-id</td>
        <td>PROCESS</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>child-process-id</td>
        <td>PROCESS</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>task-a</td>
        <td>CALL_ACTIVITY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>task-a</td>
        <td>CALL_ACTIVITY</td>
    <tr>
</table>

The workflow instance records of the created workflow instance have a reference to its parent workflow instance (`parentWorkflowInstanceKey`) and the element instance of the call activity (`parentElementInstanceKey`).

  </p>
</details>

References:
* [Variable Scopes](/reference/variables.html#variable-scopes)
* [Variable Mappings](/reference/variables.html#inputoutput-variable-mappings)
