# Parallel Gateway

A parallel gateway (aka AND-gateway) allows to split the flow into concurrent paths.

![workflow](/bpmn-workflows/parallel-gateways/parallel-gateways.png)

When a parallel gateway with multiple outgoing sequence flows is entered then all flows are taken. The paths are executed **concurrently** and independently. 

The concurrent paths can be **joined** using a parallel gateway with multiple incoming sequence flows. The workflow instance waits at the parallel gateway until each incoming sequence is taken.

> Note the outgoing paths of the parallel gateway are executed concurrently - and not parallel in the sense of parallel threads. All records of a workflow instance are written to the same partition (single stream processor).  

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>A parallel gateway with two outgoing sequence flows:

```xml
<bpmn:parallelGateway id="split" />

<bpmn:sequenceFlow id="to-ship-parcel" sourceRef="split" 
  targetRef="shipParcel" />

<bpmn:sequenceFlow id="to-process-payment" sourceRef="split" 
  targetRef="processPayment" />
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding a parallel gateway with two outgoing sequence flows:

![parallel-gateway](/bpmn-workflows/parallel-gateways/parallel-gateway.gif) 
  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of a parallel gateway: 

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>    
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>split</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>split</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>split</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>split</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>to-ship-parcel</td>
        <td>SEQUENCE_FLOW</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>to-process-payment</td>
        <td>SEQUENCE_FLOW</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>to-join-1</td>
        <td>SEQUENCE_FLOW</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>SEQUENCE_FLOW_TAKEN</td>
        <td>to-join-2</td>
        <td>SEQUENCE_FLOW</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>join</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>join</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>join</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>join</td>
        <td>PARALLEL_GATEWAY</td>
    <tr>    
</table>

  </p>
</details>

