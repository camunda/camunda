# Event-Based Gateway

An event-based gateway allows to make a decision based on events. 

![workflow](/bpmn-workflows/event-based-gateways/event-based-gateway.png)

An event-based gateway must have at least two outgoing sequence flows. Each sequence flow must to be connected to an intermediate catch event of type **timer or message**.

When an event-based gateway is entered then the workflow instance waits at the gateway until one of the events is triggered. When the first event is triggered then the outgoing sequence flow of this event is taken. No other events of the gateway can be triggered afterward.

## Additional Resources

<details>
  <summary>XML representation</summary>
  <p>An event-based gateway with two outgoing sequence flows:

```xml
<bpmn:eventBasedGateway id="gateway" />

<bpmn:sequenceFlow id="s1" sourceRef="gateway" targetRef="payment-details-updated" />

<bpmn:intermediateCatchEvent id="payment-details-updated" 
  name="Payment Details Updated">
  <bpmn:messageEventDefinition messageRef="message-payment-details-updated" />
</bpmn:intermediateCatchEvent>

<bpmn:sequenceFlow id="s2" sourceRef="gateway" targetRef="wait-one-hour" />

<bpmn:intermediateCatchEvent id="wait-one-hour" name="1 hour">
  <bpmn:timerEventDefinition>
    <bpmn:timeDuration>PT1H</bpmn:timeDuration>
  </bpmn:timerEventDefinition>
</bpmn:intermediateCatchEvent>
```

  </p>
</details>

<details>
  <summary>Using the BPMN modeler</summary>
  <p>Adding an event-based gateway with two outgoing sequence flows:

![event-based-gateway](/bpmn-workflows/event-based-gateways/event-based-gateway.gif) 
  </p>
</details>

<details>
  <summary>Workflow Lifecycle</summary>
  <p>Workflow instance records of an event-based gateway: 

<table>
    <tr>
        <th>Intent</th>
        <th>Element Id</th>
        <th>Element Type</th>
    </tr>    
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>gateway</td>
        <td>EVENT_BASED_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATED</td>
        <td>gateway</td>
        <td>EVENT_BASED_GATEWAY</td>
    <tr>
    <tr>
        <td>...</td>
        <td>...</td>
        <td>...</td>
    <tr>
    <tr>
        <td>EVENT_OCCURRED</td>
        <td>gateway</td>
        <td>EVENT_BASED_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETING</td>
        <td>gateway</td>
        <td>EVENT_BASED_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_COMPLETED</td>
        <td>gateway</td>
        <td>EVENT_BASED_GATEWAY</td>
    <tr>
    <tr>
        <td>ELEMENT_ACTIVATING</td>
        <td>payment-details-updated</td>
        <td>INTERMEDIATE_CATCH_EVENT</td>
    <tr>
</table>

  </p>
</details>

References:
* [Timer Events](/bpmn-workflows/timer-events/timer-events.html)
* [Message Events](/bpmn-workflows/message-events/message-events.html)
