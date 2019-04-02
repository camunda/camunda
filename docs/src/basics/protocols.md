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

At the moment, Zeebe officially supports two gRPC clients: one in [Java](java-client/README.md), and one in [Golang](go-client/README.md).

If Zeebe does not provide an officially-supported client in your target language, you can read the official [Quick Start](https://grpc.io/docs/quickstart/) page to find out how
to create a very basic one.

You can find a list of existing clients in the [Awesome Zeebe repository](https://github.com/zeebe-io/awesome-zeebe#clients).
Additionally, a [blog post](https://zeebe.io/blog/2018/11/grpc-generating-a-zeebe-python-client/) was published with a short tutorial on how to write a new client from scratch in Python.
