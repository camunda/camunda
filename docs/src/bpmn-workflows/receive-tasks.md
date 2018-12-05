# Receive Tasks

Receive tasks are tasks which references a message. They can be used to wait until a proper message is received. 

## Messages

A message can be referenced by one or more receive tasks. It holds the information which is used for the message correlation. The required attributes are

* the name of the message
* the correlation key

The correlation key is specified as [JSON Path](reference/json-conditions.html) expression. It is evaluated when the receive task is entered and extracts the value from the workflow instance payload. The value must be either a string or a number.

XML representation:

```xml
<bpmn:message id="Message_1iz5qtq" name="Money collected">
   <bpmn:extensionElements>
     <zeebe:subscription correlationKey="$.orderId" />
   </bpmn:extensionElements>
</bpmn:message>
```

## Receive Tasks

![Receive Tasks](/bpmn-workflows/receive-tasks.png)

When a token arrives at the receive task, it will wait there until a proper message is correlated. The correlation to the event is based on the name of the message and the correlation key. When a message is correlated then its payload is merged into the workflow instance payload and the task is left.

Read more about [message correlation](reference/message-correlation.html).

XML representation:

```xml
<bpmn:receiveTask id="money-collected" name="Money collected" messageRef="Message_1iz5qtq">
</bpmn:receiveTask>
```

> [Message intermediate catch events](/bpmn-workflows/message-events.html) are an alternative to receive tasks.

## Payload Mapping

By default, the complete message payload is merged into the workflow instance payload. This behavior can be customized by defining an output mapping at the receive task. See the [Input/Output Mappings](/bpmn-workflows/data-flow.html#inputoutput-mappings) section for details on this concept.

XML representation:

```xml
<bpmn:receiveTask id="money-collected" name="Money collected" messageRef="Message_1iz5qtq">
	<bpmn:extensionElements>
    <zeebe:ioMapping>
      <zeebe:output source="$.price" target="$.totalPrice"/>
     </zeebe:ioMapping>
  </bpmn:extensionElements>
</bpmn:receiveTask>
```
