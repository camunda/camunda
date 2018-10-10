# Message Events

Message events are events which reference a message. They can be used to wait until a proper message is received. 

> Currently, messages can be published only externally using one of the Zeebe clients.

## Messages

A message can be referenced by one or more message events. It holds the information which is used for the message correlation. The required attributes are

* the name of the message
* the correlation key

The correlation key is specified as [JSON Path](reference/json-conditions.html) expression. It is evaluated when the message event is entered and extracts the value from the workflow instance payload. The value must be either a string or a number.

XML representation:

```xml
<bpmn:message id="Message_1iz5qtq" name="Money collected">
   <bpmn:extensionElements>
     <zeebe:subscription correlationKey="$.orderId" />
   </bpmn:extensionElements>
</bpmn:message>
```

## Message Intermediate Catch Events

![workflow](/bpmn-workflows/message-catch-event-example.png)

When a token arrives at the message intermediate catch event, it will wait there until a proper message is correlated. The correlation to the event is based on the name of the message and the correlation key. When a message is correlated then its payload is merged into the workflow instance payload and the event is left.

Read more about [message correlation](reference/message-correlation.html).

XML representation:

```xml
<bpmn:intermediateCatchEvent id="IntermediateThrowEvent_090vvrf" name="Money collected">
  <bpmn:messageEventDefinition messageRef="Message_1iz5qtq" />
</bpmn:intermediateCatchEvent>
```

> [Receive tasks](workflow/receive-tasks.html) are an alternative to message intermediate catch events.
