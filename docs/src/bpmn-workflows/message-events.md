# Message Events

Message events are events which reference a message. They can be used to wait until a proper message is received.

> Currently, messages can be published only externally using one of the Zeebe clients.

## Messages

A message can be referenced by one or more message events. It holds the information which is used for the message correlation. The required attributes are

* the name of the message
* the correlation key

The correlation key is specified as [variable expression](reference/variables.html#access-variables). It is evaluated when the message event is entered and extracts the value from the workflow instance variables. The value must be either a string or a number. If the correlation key can't be resolved or it is neither a string nor a number then an incident is created.

XML representation:

```xml
<bpmn:message id="Message_1iz5qtq" name="Money collected">
   <bpmn:extensionElements>
     <zeebe:subscription correlationKey="orderId" />
   </bpmn:extensionElements>
</bpmn:message>
```

## Intermediate Message Catch Events

![workflow](/bpmn-workflows/message-catch-event-example.png)

When a token arrives at the message intermediate catch event, it will wait there until a proper message is correlated. The correlation to the event is based on the name of the message and the correlation key. The event is left when a message is correlated.

XML representation:

```xml
<bpmn:intermediateCatchEvent id="money-collected">
  <bpmn:messageEventDefinition messageRef="Message_1iz5qtq" />
</bpmn:intermediateCatchEvent>
```

BPMN Modeler: [Click Here](/bpmn-modeler/events.html#intermediate-message-catch-event)

> [Receive tasks](/bpmn-workflows/receive-tasks.html) are an alternative to message intermediate catch events where boundary events can be attached to.

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

## Message Boundary Events

When attached to the boundary of an activity, a message catch event behaves in two ways:

If it is non-interrupting, it will spawn a new token which will take the outgoing sequence flow. It will *not* terminate the activity that it's attached to. If it is interrupting, it *will* terminate the activity before spawning the token.

## Variable Mappings

By default, all message variables are merged into the workflow instance. This behavior can be customized by defining an output mapping at the message catch event.

XML representation:

```xml
<bpmn:intermediateCatchEvent id="money-collected">
  <bpmn:extensionElements>
    <zeebe:ioMapping>
      <zeebe:output source="price" target="totalPrice"/>
     </zeebe:ioMapping>
  </bpmn:extensionElements>
</bpmn:intermediateCatchEvent>
```

## Additional Resources

* [Message Correlation](reference/message-correlation.html)
* [Variable Mappings](reference/variables.html#inputoutput-variable-mappings)
* [Incidents](/reference/incidents.html)
