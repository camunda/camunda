# Protocols

Zeebe clients connect to brokers via a stateless gateway. For the communication
between client and gateway [gRPC](https://grpc.io/) is used. The communication protocol is defined using
Protocol Buffers v3 ([proto3](https://developers.google.com/protocol-buffers/docs/proto3)), and you can find it in the
[Zeebe repository](https://github.com/zeebe-io/zeebe/tree/develop/gateway-protocol).

## What is gRPC?
gRPC was first developed by Google and is now an open-source project and part of the Cloud Native Computing Foundation.
If you’re new to gRPC, the [“What is gRPC”](https://grpc.io/docs/guides/index.html) page on the project website provides a good introduction to it.

## Why gRPC?
gRPC has many nice features that make it a good fit for Zeebe. It:

  - supports bi-directional streaming for opening a persistent connection and sending or receiving a stream of messages between client and server
  - uses the common http2 protocol by default
  - uses Protocol Buffers as an interface definition and data serialization mechanism–specifically, Zeebe uses proto3, which supports easy client generation in ten different programming languages

## Supported clients

At the moment, Zeebe officially supports two gRPC clients: one in [Java](/clients/java-client/), and one in [Golang](/clients/go-client/).

If Zeebe does not provide an officially-supported client in your target language, you can read the official [Quick Start](https://grpc.io/docs/quickstart/) page to find out how
to create a very basic one.

You can find a list of existing clients in the [Awesome Zeebe repository](https://github.com/zeebe-io/awesome-zeebe#clients).
Additionally, a [blog post](https://zeebe.io/blog/2018/11/grpc-generating-a-zeebe-python-client/) was published with a short tutorial on how to write a new client from scratch in Python.


## Handling back-pressure

When a broker receives a user request, it is written to the *event stream* first (see section [Internal Processing](/basics/internal-processing.html) for details), and processed later by the stream processor.
If the processing is slow or if there are many user requests in the stream, it might take too long for the processor to start processing the command.
If the broker keeps accepting new requests from the user, the back log increases and the processing latency can grow beyond an acceptable time.
To avoid such problems, Zeebe employs a back-pressure mechanism.
When the broker receives more requests than it can process with an acceptable latency, it rejects some requests.

The maximum rate of requests that can be processed by a broker depends on the processing capacity of the machine, the network latency, current load of the system and so on.
Hence, there is no fixed limit configured in Zeebe for the maximum rate of requests it accepts.
Instead, Zeebe uses an adaptive algorithm to dynamically determine the limit of the number of inflight requests (the requests that are accepted by the broker, but not yet processed).
The inflight request count is incremented when a request is accepted and decremented when a response is sent back to the client.
The broker rejects requests when the inflight request count reaches the limit.

When the broker rejects requests due to back-pressure, the clients can retry them with an appropriate retry strategy.
If the rejection rate is high, it indicates that the broker is constantly under high load.
In that case, it is recommended to reduce the request rate.
