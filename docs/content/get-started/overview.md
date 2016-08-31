---

title: "Overview"
weight: 10

menu:
  main:
    identifier: "overview"
    parent: "get-started"

---


# Big Picture

A Camunda Tngp usage scenario consists of two major components: A central *broker* and a number of *clients*. The broker is a server-like application that distributes work that arises when a workflow is executed, such as tasks that need to be performed. It offers a TCP API for operations like deploying BPMN processes, starting workflow instances, and fetching tasks. Clients are custom Java applications that implement workflows by interacting with the broker's API. For ease of use, Camunda Tngp ships a set of client libraries (currently Java only) to interact with the broker's API.

# Programming Model

Whenever a client makes a request that continues workflow execution, a positive response has the following semantics:

* The request was successful.
* Workflow execution continues **eventually**. By the time the response is received, the workflow execution state may not have changed.