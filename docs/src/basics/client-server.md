# Architecture

There are four main components in Zeebe's architecture: the client, the gateway, the broker, and the exporter.  

![zeebe-architecture](/basics/zeebe-architecture.png)

## Client

Clients are libraries that you embed in an application (e.g. a microservice that executes your business logic) to connect to a Zeebe cluster. Clients have two primary uses:
* Carrying out business logic (starting workflow instances, publishing messages, working on tasks)
* Handling operational issues (updating workflow instance variables, resolving incidents)

More about Zeebe clients:
* Clients connect to the Zeebe gateway via [gRPC](https://grpc.io), which uses http/2-based transport. To learn more about gRPC in Zeebe, check out the [gRPC section of the docs](/grpc/README.html).
* The Zeebe project includes officially-supported Java and Go clients, and gRPC makes it possible to generate clients in a range of different programming languages. [Community clients](https://awesome.zeebe.io) have been created in other languages, including C#, Ruby, and JavaScript.
* Client applications can be scaled up and down completely separately from Zeebe--the Zeebe brokers do not execute any business logic.

## Gateway

The gateway, which proxies requests to brokers, serves as a single entry point to a Zeebe cluster. 

The gateway is stateless and sessionless, and gateways can be added as necessary for load balancing and high availability.

## Broker

The Zeebe broker is the distributed workflow engine that keeps state of active workflow instances.

Brokers can be partitioned for horizontal scalability and replicated for fault tolerance. A Zeebe deployment will often consist of more than one broker.

It's important to note that no application business logic lives in the broker. Its only responsibilities are:

1. Storing and managing the state of active workflow instances

2. Distributing work items to clients

Brokers form a peer-to-peer network in which there is no single point of failure. This is possible because all brokers perform the same kind of tasks and the responsibilities of an unavailable broker are transparently reassigned in the network.

## Exporter

The exporter system provides an event stream of state changes within Zeebe. This data has many potential uses, including but not limited to:

* Monitoring the current state of running workflow instances

* Analysis of historic workflow data for auditing, business intelligence, etc

* Tracking [incidents](/reference/incidents.html) created by Zeebe

The exporter includes a simple API that you can use to stream data into a storage system of your choice. Zeebe includes an out-of-the-box [Elasticsearch exporter](https://github.com/zeebe-io/zeebe/tree/master/exporters/elasticsearch-exporter), and other [community-contributed exporters](https://awesome.zeebe.io) are also available.
