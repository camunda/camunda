# Receive Tasks

Receive tasks are tasks which references a message. They can be used to wait until a proper message is received.

## Messages

A message can be referenced by one or more receive tasks. It holds the information which is used for the message correlation. The required attributes are

* the name of the message
* the correlation key

The correlation key is specified as a [variable expression](reference/variables.html#access-variables). It is evaluated when the receive task is entered and extracts the value from the workflow instance variables. The variable value must be either a string or a number. If the correlation key can't be resolved or it is neither a string nor a number then an incident is created.

XML representation:

```xml
<bpmn:message id="Message_1iz5qtq" name="Money collected">
   <bpmn:extensionElements>
     <zeebe:subscription correlationKey="orderId" />
   </bpmn:extensionElements>
</bpmn:message>
```

## Receive Tasks

![Receive Tasks](/bpmn-workflows/receive-tasks.png)

When a token arrives at the receive task, it will wait there until a proper message is correlated. The correlation to the event is based on the name of the message and the correlation key. The task is left when a message is correlated.

XML representation:

```xml
<bpmn:receiveTask id="money-collected" name="Money collected" messageRef="Message_1iz5qtq">
</bpmn:receiveTask>
```

BPMN Modeler: [Click Here](/bpmn-modeler/tasks.html#receive-task)

> [Message intermediate catch events](/bpmn-workflows/message-events.html) are an alternative to receive tasks which can be used on an event-based gateway.

## Variable Mappings

By default, all message variables are merged into the workflow instance. This behavior can be customized by defining an output mapping at the receive task.

XML representation:

```xml
<bpmn:receiveTask id="money-collected" name="Money collected" messageRef="Message_1iz5qtq">
	<bpmn:extensionElements>
    <zeebe:ioMapping>
      <zeebe:output source="price" target="totalPrice"/>
     </zeebe:ioMapping>
  </bpmn:extensionElements>
</bpmn:receiveTask>
```

## Additional Resources

* [Message Correlation](reference/message-correlation.html)
* [Variable Mappings](reference/variables.html#inputoutput-variable-mappings)
* [Incidents](/reference/incidents.html)
