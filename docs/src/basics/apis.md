# APIs and Protocols

Zeebe provides a set of APIs and Protocols which allow the clients to interact with the brokers. Zeebe supports two different styles of interactions:

* Command APIs: the client sends a particular command to the broker and expects a response. Example: completing a task.
* Subscription APIs: the broker streams data to the clients. Example: worker streams tasks to the clients to work on.

All interactions are done using a binary protocol over TCP/IP.

## Non-Blocking

Zeebe protocols are non-blocking, including Command APIs. _Command_ and _Reply_ are always separate, independent messages. A client can send multiple commands using the same TCP connection before receiving a reply. For correlation, the reply to a specific command contains the same request-id as the command.

## Streaming

The subscription protocols work in streaming mode: the broker pushes out tasks and events to the subscribers in a non-blocking way. To keep latency low, the broker pushes out tasks and events to the clients as fast as they become available.

## Backpressure

The subscription protocols embedd a backpressure protocol to prevent the broker from overwhelming the clients with more tasks or evens then they can handle. The backpressure protocol is credit based.

The effect of backpressure is that the system automatically adjusts flow rates to the available resources. For example, a fast consumer running on a powerful machine can process tasks at a very high rate, while a slower consumer is automatically throtteled. This works without user configuration.

