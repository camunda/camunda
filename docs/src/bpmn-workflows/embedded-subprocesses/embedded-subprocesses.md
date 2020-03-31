# Embedded Subprocess

An embedded subprocess allows to group elements of the workflow.

![embedded-subprocess](/bpmn-workflows/embedded-subprocesses/embedded-subprocess.png)

An embedded subprocess must have exactly one none start event. Other start events are not allowed.  

When an embedded subprocess is entered then the start event gets activated. The subprocess stays active as long as one containing element is active. When the last element is completed then the subprocess gets completed and the outgoing sequence flow is taken.

Embedded subprocesses are often used together with **boundary events**. One or more boundary events can be attached to an subprocess. When an interrupting boundary event is triggered then the whole subprocess including all active elements gets terminated. 

## Variable Mappings

Input mappings can be used to create new local variables in the scope of the subprocess. These variables are only visible within the subprocess.

By default, the local variables of the subprocess are not propagated (i.e. they are removed with the scope). This behavior can be customized by defining output mappings at the subprocess. The output mappings are applied on completing the subprocess.

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>An embedded subprocess with a start event:

```xml
<bpmn:subProcess id="process-order" name="Process Order">
  <bpmn:startEvent id="order-placed" />
  ... more contained elements ...
</bpmn:subProcess>
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding an embedded subprocess:

![event-based-gateway](/bpmn-workflows/embedded-subprocesses/embedded-subprocess.gif) 
  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of an embedded subprocess: 

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>    
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>process-order</td>
        <td>SUB_PROCESS</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>process-order</td>
        <td>SUB_PROCESS</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>order-placed</td>
        <td>START_EVENT</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>items-fetched</td>
        <td>END_EVENT</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>process-order</td>
        <td>SUB_PROCESS</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>process-order</td>
        <td>SUB_PROCESS</td>
    <tr>
</table>

  </p>
</details>

References:
* [Variable Mappings](/reference/variables.html#inputoutput-variable-mappings)
