# Message Correlation

Message correlation describes how a message is correlated to a workflow instance (i.e. to a message catch event).

In Zeebe, a message is not sent to a workflow instance directly. Instead, it is published with correlation information. When a workflow instance is waiting at a message catch event which specifies the same correlation information then the message is correlated to the workflow instance.

The correlation information contains the name of the message and the correlation key.

![Message Correlation](/reference/message-correlation.png)

For example:

An instance of the order workflow is created and wait at the message catch event until the money is collected. The message catch event specifies the message name `Money collected` and the correlation key `orderId`. The key is resolved with the workflow instance variable `orderId` to `order-123`.

A message is published by the payment service using one of the Zeebe clients. It has the name `Money collected` and the correlation key `order-123`. Since the correlation information matches, the message is correlated to the workflow instance. That means its payload is merged into the workflow instance payload and the message catch event is left.

> Note that a message can be correlated to multiple workflow instances if they share the same correlation information. But it can be correlated only once per workflow instance.

## Message Buffering

In Zeebe, messages can be buffered for a given time. Buffering can be useful in a situation when it is not guaranteed that the message catch event is entered before the message is published.

A message has a time-to-live (TTL) which specifies the time how long it is buffered. Within this time, the message can be correlated to a workflow instance.

When a workflow instance enters a message catch event then it polls the buffer for a proper message. If a proper message exists then it is correlated to the workflow instance. In case multiple messages match the correlation information then the first published message is correlated. The behavior of the buffer is similar to a queue.    

The buffering of a message is disabled when its TTL is set to zero. If the message can't be correlated to a workflow instance then it is discarded.

## Message Uniqueness

A message can contain a unique id to ensure that it is published only once (i.e. idempotent). The id can be any string, for example, a request id, a tracking number or the offset/position in a message queue.

When the message is published then it checks if a message with the same name, correlation key and id exists in the buffer. If yes then the message is rejected and not correlated.

> Note that the uniqueness check only looks into the buffer. If a message is discarded from the buffer then a message with the same name, correlation key and id can be published afterward.
