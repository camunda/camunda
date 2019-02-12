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

## Message Start Events

A message start event allows creating a workflow instance by publishing a named message.
A workflow can have more than one message start event, each with a unique message name.
We can choose the right start event from a set of start events using the message name.

When deploying a workflow, the following conditions apply:
* The message name must be unique across all start events in the workflow definition.
* When a new version of the workflow is deployed, subscriptions to the message start events of the old version will be canceled. Thus instances of the old version cannot be created by publishing messages. This is true even if the new version has different start events.
* Currently, a workflow that has message start events cannot have a none start event.

The following behavior applies to published messages:
* A message is correlated to a message start event if the message name matches. The correlation key of the message is ignored.
* A message is not correlated to a message start event if it was published before the subscription was created, i.e. before the workflow was deployed. This is because the message could have been already correlated to the previous version of the workflow.

XML representation

```xml
<bpmn:message id="newOrder" name="New order">
</bpmn:message>

<bpmn:startEvent id="messageStart">
  <bpmn:messageEventDefinition messageRef="newOrder" />
</bpmn:startEvent>
```

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
