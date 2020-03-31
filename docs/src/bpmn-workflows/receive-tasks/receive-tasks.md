# Receive Tasks

Receive tasks are tasks which references a message. They are used to wait until a proper message is received.

![Receive Tasks](/bpmn-workflows/receive-tasks/receive-tasks.png)

When a receive task is entered then a corresponding message subscription is created. The workflow instance stops at this point and waits until the message is correlated.
 
A message can published using one of the Zeebe clients. When the message is correlated, the receive task gets completed and the workflow instance continues. 

> An alternative to receive tasks are [message intermediate catch events](/bpmn-workflows/message-events/message-events.html) which behaves the same but can be used together with event-based gateways.

## Messages

A message can be referenced by one or more receive tasks. It **must** define the name of the message (e.g. `Money collected`) and the `correlationKey` variable (e.g. `orderId`).

The `correlationKey` variable must reference a variable of the workflow instance that holds the correlation key of the message. It is read from the workflow instance on activating the receive task and must be either a `string` or a `number`.

In order to correlate a message to the receive task, the message is published with the defined name (e.g. `Money collected`) and the **value** of the `correlationKey` variable. For example, if the workflow instance has a variable `orderId` with value `"order-123"` then the message must be published with the correlation key `"order-123"`.

## Variable Mappings

By default, all message variables are merged into the workflow instance. This behavior can be customized by defining an output mapping at the receive task.

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>A receive task with message definition:

```xml
<bpmn:message id="Message_1iz5qtq" name="Money collected">
   <bpmn:extensionElements>
     <zeebe:subscription correlationKey="orderId" />
   </bpmn:extensionElements>
</bpmn:message>

<bpmn:receiveTask id="money-collected" name="Money collected" 
  messageRef="Message_1iz5qtq">
</bpmn:receiveTask>
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding a receive task with message:

![receive-task](/bpmn-workflows/receive-tasks/receive-task.gif) 
  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of a receive task: 

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>    
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>money-collected</td>
        <td>RECEIVE_TASK</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>money-collected</td>
        <td>RECEIVE_TASK</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>EVENT_OCCURRED</td>
        <td>money-collected</td>
        <td>RECEIVE_TASK</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>money-collected</td>
        <td>RECEIVE_TASK</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>money-collected</td>
        <td>RECEIVE_TASK</td>
    <tr>
</table>

  </p>
</details>

References:
* [Message Correlation](/reference/message-correlation.html)
* [Variable Mappings](/reference/variables.html#inputoutput-variable-mappings)
* [Incidents](/reference/incidents.html)
