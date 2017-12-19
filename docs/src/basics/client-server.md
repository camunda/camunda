# Client / Server

Zeebe uses a client / server architecture. There are two main components: *Broker* and *Client*.

![client-server](/basics/client-server.png)

## Broker

A Zeebe broker (server) has three main responsibilities:

1. Storing and managing workflow data

2. Distributing work items to clients

3. Exposing a workflow event stream to clients via publish-subscribe

A Zeebe setup typically consists of more than one broker. Adding brokers scales storage and computing resources. In addition, it provides fault tolerance since Zeebe keeps multiple copies of its data on different brokers.

Brokers form a peer-to-peer network in which there is no single point of failure. This is possible because all brokers perform the same kind of tasks and the responsibiltiies of an unavailable broker are transparently reassigned in the network.

## Client

Clients are libraries which you embed into your application to connect to the broker.

Clients connect to the broker using Zeebe's binary protocols. The protocols are programming-language-agnostic, which makes it possible to write clients in different programming languages. There is a number of clients readily available.
