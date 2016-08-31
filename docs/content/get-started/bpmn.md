---

title: "BPMN"
weight: 30

menu:
  main:
    identifier: "bpmn"
    parent: "get-started"

---


# Supported Elements

* None Start Event
* None End Event
* Service Task

# BPMN Extensions

In addition to the standard BPMN attributes, Camunda Tngp requires the BPMN XML to contain additional attributes.

## Service Tasks

* `camunda:type`: The type of task that identifies *which* task must be performed.
* `camunda:taskQueueId`: The id of the task queue the tasks are added to. The value must correspond to the `id` value of a `[task-queue]` element in the broker configuration.