# Protocols

Zeebe defineses protocols for communication between clients and brokers. Zeebe supports two styles of interactions:

* Command Protocol: A client sends a command to a broker and expects a response. Example: Completing a job.
* Subscription Protocol: A broker streams data to a client. Example: Broker streams jobs to a client to work on.

Both protocols are binary on top of TCP/IP.

## Non-Blocking

Zeebe protocols are non-blocking by design, including commands. _Command_ and _Reply_ are always separate, independent messages. A client can send multiple commands using the same TCP connection before receiving a reply.

## Streaming

The subscription protocol works in streaming mode: A broker pushes out jobs and records to the subscribers in a non-blocking way. To keep latency low, the broker pushes out jobs and records to the clients as soon as they become available.

## Backpressure

The subscription protocol embeds a backpressure mechanism to prevent brokers from overwhelming clients with more jobs or records than they can handle. This mechanism is client-driven, i.e. clients submit a capacity of records they can safely receive at a time. A broker pushes records until this capacity is exhausted and the client replenishes the capacity whenever it completes processing of records.

The effect of backpressure is that the system automatically adjusts flow rates to the available resources. For example, a fast consumer running on a powerful machine can process jobs at a very high rate, while a slower consumer is automatically throttled. This works without additional user configuration.
