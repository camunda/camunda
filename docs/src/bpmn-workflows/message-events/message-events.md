# Message Events

Message events are events which reference a message. They are used to wait until a proper message is received.

![workflow](/bpmn-workflows/message-events/message-events.png)

At the moment, messages can be published only externally by using one of the Zeebe clients.

## Message Start Events

A workflow can have one or more message start events (besides other types of start events). Each of the message events must have a unique message name.

When a workflow is deployed then it creates a message subscription for each message start event. Message subscriptions of the previous version of the workflow (based on the BPMN process id) are closed.

When the message subscription is created then a message can be correlated to the start event if the message name matches. On correlating the message, a new workflow instance is created and the corresponding message start event is activated.

Messages are **not** correlated if they were published before the workflow was deployed. Or, if a new version of the workflow is deployed which doesn't have a proper start event. 

The `correlationKey` of a published message can be used to control the workflow instance creation. If an instance of this workflow is active (independently from its version) and it was triggered by a message with the same `correlationKey` then the message is **not** correlated and no new instance is created. When the active workflow instance is ended (completed or terminated) and a message with the same `correlationKey` and a matching message name is buffered (i.e. TTL > 0) then this message is correlated and a new instance of the latest version of the workflow is created.

If the `correlationKey` of a message is empty then it will always create a new workflow instance and does not check if an instance is already active.

## Intermediate Message Catch Events

When an intermediate message catch event is entered then a corresponding message subscription is created. The workflow instance stops at this point and waits until the message is correlated. When a message is correlated, the catch event gets completed and the workflow instance continues.

> An alternative to intermediate message catch events are [receive tasks](/bpmn-workflows/receive-tasks/receive-tasks.html) which behaves the same but can be used together with boundary events.

## Message Boundary Events

An activity can have one or more message boundary events. Each of the message events must have a unique message name.

When the activity is entered then it creates a corresponding message subscription for each boundary message event. If a non-interrupting boundary event is triggered then the activity is not terminated and multiple messages can be correlated. 

## Messages

A message can be referenced by one or more message events. It **must** define the name of the message (e.g. `Money collected`) and the `correlationKey` variable (e.g. `orderId`), except it is only referenced by message start events.

The `correlationKey` variable must reference a variable of the workflow instance that holds the correlation key of the message. It is read from the workflow instance on activating the message event and must be either a `string` or a `number`.

In order to correlate a message to the message event, the message is published with the defined name (e.g. `Money collected`) and the **value** of the `correlationKey` variable. For example, if the workflow instance has a variable `orderId` with value `"order-123"` then the message must be published with the correlation key `"order-123"`.

## Variable Mappings

By default, all message variables are merged into the workflow instance. This behavior can be customized by defining an output mapping at the message catch event.

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>A message start event with message definition:

```xml
<bpmn:message id="Message_0z0aft4" name="order-placed" />

<bpmn:startEvent id="order-placed" name="Order placed">
  <bpmn:messageEventDefinition messageRef="Message_0z0aft4" />
</bpmn:startEvent>
``` 
  
An intermediate message catch event with message definition:

```xml
<bpmn:message id="Message_1iz5qtq" name="money-collected">
  <bpmn:extensionElements>
    <zeebe:subscription correlationKey="orderId" />
  </bpmn:extensionElements>
</bpmn:message>

<bpmn:intermediateCatchEvent id="money-collected" name="Money collected" >
  <bpmn:messageEventDefinition messageRef="Message_1iz5qtq" />
</bpmn:intermediateCatchEvent>
```

A boundary message event:
```xml
<bpmn:boundaryEvent id="order-canceled" name="Order Canceled" 
  attachedToRef="collect-money">
  <bpmn:messageEventDefinition messageRef="Message_1iz5qtq" />
</bpmn:boundaryEvent>
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding an intermediate message catch event:

![message-event](/bpmn-workflows/message-events/message-event.gif) 
  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of a message start event: 

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>   
    <tr>
        <td>EVENT_OCCURRED</td>
        <td>order-placed</td>
        <td>START_EVENT</td>
    <tr> 
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>order-placed</td>
        <td>START_EVENT</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>order-placed</td>
        <td>START_EVENT</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>order-placed</td>
        <td>START_EVENT</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>order-placed</td>
        <td>START_EVENT</td>
    <tr>
</table>

Workflow instance records of an intermediate message catch event: 

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>    
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>order-delivered</td>
        <td>INTERMEDIATE_CATCH_EVENT</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>order-delivered</td>
        <td>INTERMEDIATE_CATCH_EVENT</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>EVENT_OCCURRED</td>
        <td>money-collected</td>
        <td>INTERMEDIATE_CATCH_EVENT</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>money-collected</td>
        <td>INTERMEDIATE_CATCH_EVENT</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>money-collected</td>
        <td>INTERMEDIATE_CATCH_EVENT</td>
    <tr>
</table>

  </p>
</details>

References:
* [Message Correlation](/reference/message-correlation.html)
* [Variable Mappings](/reference/variables.html#inputoutput-variable-mappings)
* [Incidents](/reference/incidents.html)
