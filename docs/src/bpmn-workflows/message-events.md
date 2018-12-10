# Message Events

Message events are events which reference a message. They can be used to wait until a proper message is received. 

> Currently, messages can be published only externally using one of the Zeebe clients.

## Messages

A message can be referenced by one or more message events. It holds the information which is used for the message correlation. The required attributes are

* the name of the message
* the correlation key

The correlation key is specified as [JSON Path](reference/json-conditions.html) expression. It is evaluated when the message event is entered and extracts the value from the workflow instance payload. The value must be either a string or a number. If the correlation key can't be resolved or it is neither a string nor a number then an incident is created.

XML representation:

```xml
<bpmn:message id="Message_1iz5qtq" name="Money collected">
   <bpmn:extensionElements>
     <zeebe:subscription correlationKey="$.orderId" />
   </bpmn:extensionElements>
</bpmn:message>
```

## Intermediate Message Catch Events

![workflow](/bpmn-workflows/message-catch-event-example.png)

When a token arrives at the message intermediate catch event, it will wait there until a proper message is correlated. The correlation to the event is based on the name of the message and the correlation key. When a message is correlated then its payload is merged into the workflow instance payload and the event is left.

XML representation:

```xml
<bpmn:intermediateCatchEvent id="money-collected">
  <bpmn:messageEventDefinition messageRef="Message_1iz5qtq" />
</bpmn:intermediateCatchEvent>
```

> [Receive tasks](/bpmn-workflows/receive-tasks.html) are an alternative to message intermediate catch events.

### Payload Mapping

By default, the complete message payload is merged into the workflow instance payload. This behavior can be customized by defining an output mapping at the intermediate catch event. 

XML representation:

```xml
<bpmn:intermediateCatchEvent id="money-collected">
  <bpmn:extensionElements>
    <zeebe:ioMapping>
      <zeebe:output source="$.price" target="$.totalPrice"/>
     </zeebe:ioMapping>
  </bpmn:extensionElements>
</bpmn:intermediateCatchEvent>
```

### Boundary Events

When attached to the boundary of an activity, a message catch event behaves in two ways:

If it is non-interrupting, it will spawn a new token which will take the outgoing sequence flow, applying any defined output mapping with the message payload. It will *not* terminate the activity that it's attached to.
If it is interrupting, it *will* terminate the activity before spawning the token.

## Additional Resources

* [Message Correlation](reference/message-correlation.html)
* [JSON Path](reference/json-conditions.html)
* [Input/Output Mappings](/bpmn-workflows/data-flow.html#inputoutput-mappings)
* [Incidents](/reference/incidents.html)
