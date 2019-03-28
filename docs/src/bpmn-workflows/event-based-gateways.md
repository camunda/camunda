# Event-Based Gateway

![workflow](/bpmn-workflows/event-based-gateway.png)

An event-based gateway allows you to make a decision based on events. Each outgoing sequence flow of the gateway needs to be connected to an intermediate catch event.

When a token reaches an event-based gateway then it waits there until the first event is triggered. It takes the outgoing sequence flow of this event and continues. No other event of the gateway can be triggered afterward.

## Constraints

* the gateway has at least two outgoing sequence flows
* the gateway has only outgoing sequence flows to intermediate catch events of type:
  * [timer](/bpmn-workflows/timer-events.html)
  * [message](/bpmn-workflows/message-events.html)

## XML Representation

```xml
<bpmn:eventBasedGateway id="gateway" />

<bpmn:sequenceFlow id="s1" sourceRef="gateway" targetRef="payment-details-updated" />

<bpmn:intermediateCatchEvent id="payment-details-updated" name="Payment Details Updated">
  <bpmn:messageEventDefinition messageRef="message-payment-details-updated" />
</bpmn:intermediateCatchEvent>

<bpmn:sequenceFlow id="s2" sourceRef="gateway" targetRef="wait-one-hour" />

<bpmn:intermediateCatchEvent id="wait-one-hour" name="1 hour">
  <bpmn:timerEventDefinition>
    <bpmn:timeDuration>PT1H</bpmn:timeDuration>
  </bpmn:timerEventDefinition>
</bpmn:intermediateCatchEvent>
```

BPMN Modeler: [Click Here](/bpmn-modeler/gateways.html#event-based-gateway)
