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

In addition to the standard BPMN attributes, Camunda Tngp requires the BPMN XML to contain additional extension elements. The namespace for these is `http://camunda.org/schema/tngp/1.0`, in the following aliased as `tngp`.

## Service Tasks

Extension element `tngp:taskDefinition`:

* Attribute `type`: The type of task that identifies *which* task must be performed.
* Attribute `retries`: The default number of retries that should be performed.

### Example

<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<definitions xmlns:camunda="http://camunda.org/schema/1.0/bpmn" xmlns:tngp="http://camunda.org/schema/tngp/1.0" id="definitions_c9d638f5-ed4f-4335-ad69-75d68f11eafd" targetNamespace="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="process" isExecutable="true">
    ...
    <serviceTask id="task">
      <extensionElements>
        <tngp:taskDefiniton retries="3" type="foo"/>
      </extensionElements>
    </serviceTask>
    ...
  </process>
</definitions>
