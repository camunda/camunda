# Client / Server

Zeebe uses a client / server architecture. There are two main components: the broker and the client.

\[TODO: image\]

## The Broker

The broker aka the "server" has two main responsibilities:

1. Storing and managing data

2. Distributing work to clients and allowing them to subscribe to events

Usually, users run more than a single broker. Zeebe being a distributed system, brokers from a peer-to-peer network. In this network, all nodes are equal and there are no special "master" nodes. Also, there is no single point of failure.

Running a network of brokers allows you to scale both the storage and compute resources. In addition, it provides fault tolerance since Zeebe will create multiple copies of its data on different machines.

## The Client

Clients are libraries which you embed into your application to connect to the broker.

Clients connect to the broker using Zeebe's binary APIs and protocols. The protocols are programming language agnostic which makes it possible to write clients in different programming languages.
