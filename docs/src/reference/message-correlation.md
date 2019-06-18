# Message Correlation

You can start workflow instances with a message, by placing a message start event in the process diagram. In the case of a message start event, correlation is simple. Incoming messages are correlated with all workflows whose message start event message name matches the name of the incoming message, and instances of those workflows are started.

Incoming messages can also be correlated with specific already-running workflow instances. You can target running workflow instances with messages that provide more information and can advance the process flow (message catch events), interrupt the process flow (interrupting boundary message events), or spawn new sub-tasks in the process flow (non-interrupting boundary message events).

In Zeebe, a message is not sent to a workflow instance directly. Instead, it is buffered by the broker for the duration of its TTL and correlated with any workflows where there is a match. In addition to the message name, the message correlation key can be used by the message publisher to target specific running workflow instances. The broker uses the message name to correlate the message with message events in workflows, and the value of the message correlation key to target specific running instances.

Message catch and boundary events in process definitions are linked to message specifications that have a `name` (the message name) and a `correlationKey`. In the case of the message specification, the `correlationKey` is the _name of the variable_ to match against. The correlationKey in the message specification is a pointer to a variable in the workflow state.

![Message Correlation](/reference/message-correlation.png)

Messages are published by clients with the `name` and the _value to match_ as the `correlationKey`. The value of the correlationKey in a message is matched against the value of the variable named in the correlationKey of a message specification. So if the process definition has a message catch whose correlationKey is `orderId`, and a client publishes a message (with the correct name) with a correlationKey set to `0000-123`, then the broker looks for workflows listening for that message name whose `orderId` variable is set to `000-123`.

For example:

An instance of an order workflow has been created with the orderId variable set as `0000-123`, and is waiting at a message catch event until it receives a message to let it know that the money has been collected. The message catch event specifies the message name `Money collected` and the correlation key `orderId`.

A message is published by the payment service. It has the name `Money collected` and the message correlationKey is set as `0000-123`. The broker will correlate this message with any running workflows waiting on a message event with a message name `Money Collected`, whose orderId is `0000-123`. When the correlation information matches, the message is correlated to the workflow instance. That means that our order workflow instance will receive a copy of the message. The message payload is merged into the order workflow instance payload and the message catch event is left.

> Note that a message can be correlated to multiple workflow instances if they share the same correlation information. But it will be correlated only once per workflow instance.

## Message Buffering

In Zeebe, messages can be buffered for a given time. Buffering can be useful in a situation when it is not guaranteed that the message catch event is entered before the message is published.

A message has a time-to-live (TTL) which specifies the time how long it is buffered. Within this time, the message can be correlated to any number of workflow instances where there is a match.

When a workflow instance enters a message catch event, it polls the buffer for a matching message. If a matching message exists, it is correlated to the workflow instance. For a catch event, if multiple messages match the correlation information then the first published message is correlated.

In the case of boundary events, a process does not need to be waiting for the message. In the case of a boundary event, the arrival of a correlated message will either cause an interruption to the current token flow (_interrupting boundary event_), or spawn a new token (in the case of a _non-interrupting boundary event_).

Messages have a TTL, so boundary events can be triggered in processes that start after the arrival of a message, if the message TTL has not yet expired when the process starts.

The buffering of a message is disabled when its TTL is set to zero. In this case, if the message can't be correlated to a running workflow instance immediately when it arrives, it is discarded.

## Message Uniqueness

A message can be optionally guaranteed to be idempotent within the window of its TTL. If a message contains a unique id, this is combined with the name and correlationKey value and compared with other messages that are still in the buffer (those whose TTL has not expired). The id can be any string - for example, a request id, a tracking number or the offset/position in a message queue.

When an idempotent message is published, the broker checks the buffer if a message with the same name, correlation key and id has been received. If it has, then the message is rejected and not correlated. Messages are removed from the buffer when their TTL has expired. So messages are only idempotent within the window of their TTL.

> Note that the uniqueness check only looks into the buffer. If a message is discarded from the buffer then a message with the same name, correlation key and id can be published and correlated after a previous one has expired. If you need 100% guaranteed idempotency over a long period of time, set a long TTL on the message. If the volume of such messages is high, it will impact performance, so you should design your TTL around the expected lifetime of processes, and move the idempotency check outside the broker if it is extremely long.
