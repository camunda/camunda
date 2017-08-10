# Why Zeebe?

Zeebe is an orchestration engine for distributed microservices. It allows users to define orchestration flows visually using BPMN. Zeebe ensures that once started, flows are always carried out fully, retrying steps in case of failures. Along the way, Zeebe maintains a complete audit log so that the progress of flows can be monitored and tracked. Zeebe is a big data system and scales seamlessly with growing transaction volumes.

## Publish-Subscribe

Zeebe applies publish-subscribe as an interaction model for orchestration. A service capable of performing a certain task or step in a workflow subscribes to this task and is notified via a message when work is available. Publish-subscribe gives a lot of control to the service: the service decides to which tasks to subscribe to, when to subscribe and can even control processing rates. These properties make the overall system highly resilient, scalable and _reactive_.

## Fault Tolerance

Zeebe replicates data across multiple machines to ensure availability and prevent data loss. If a machine fails or gets otherwise disconnected from the cluster, another machine which has a copy of the same data automatically takes over, ensuring that the system as a whole remains available without requiring manual action.

## Devops, SRE and the Automation of Operations

Zeebe is designed with modern operation practices in mind. Firstly, it does not require a database or any other external system to function. It is completely self-contained and self sufficient. Secondly, since all nodes in the cluster are equal, it is comparatively simple to scale. This makes it function well together with modern datacenter and cloud management systems such as [Docker](https://www.docker.com/), [Kubernetes](https://kubernetes.io/) or [DC/OS](https://dcos.io/). Moreover, the CLI (Command Line Interface) allows you to script and automate management and operations tasks.

## Simple & Lightweight

Most existing workflow systems provide many more features than Zeebe. While having many features available to you is generally positive, they also come at a cost: higher complexity making the system challenging to understand and use while degrading performance. Zeebe focuses 100% on providing a small, robust and scalable solution for orchestration workflows. Rather than covering a broad spectrum of features, its goal is to excel within the scope it covers. In addition, it composes well with other systems. For example: Zeebe, provides a simple event stream API which makes it easy to stream all internal data into another system like elastic search for indexing and querying.

## Zeebe may not be right for you

First, Zeebe is currently very early stage, the initial feature has not been fully developed yet and it is not ready for production. See the [Roadmap](https://github.com/zeebe-io/zeebe/blob/master/ROADMAP.md) for more details.

Second, your applications may not need the kind of scalability and fault tolerance provided by Zeebe. Or, you may require a large set of features around BPM (Business Process Management) which Zeebe does not offer.

In such scenarios, a traditional workflow system like [Camunda BPM](https://camunda.org) is a much better choice.
