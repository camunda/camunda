# Workflows

Workflows are flow-chart-like blueprints that define the orchestration of *tasks*. Every task represents a piece of business logic such that their ordered execution produces a meaningful result.

A *job worker* is your implementation of the business logic of a task. It must embed a Zeebe client library to connect to the broker but other than that there are no restrictions on its implementation. You can choose to write a worker as a microservice, as part of a classical three-tier application, as a \(lambda\) function, via command line tools, etc.

Running a workflow then requires two steps: Submitting the workflow to Zeebe and connecting the required workers.

## Sequences

The simplest kind of workflow is an ordered sequence of tasks. Whenever workflow execution reaches a task, Zeebe creates a job and triggers the invocation of a job worker.

![workflow-sequence](/basics/workflow-sequence.png)

You can think of Zeebe's workflow orchestration as a state machine. Orchestration reaches a task, triggers the worker and then waits for the worker to complete its work. Once the work is completed, the flow continues with the next step. If the worker fails to complete the work, the workflow remains at the current step, potentially retrying the job until it eventually succeeds.

## Data Flow

As Zeebe progresses from one task to the next in a workflow, it can move custom data in the form of a JSON document along. This data is called the *workflow payload* and is created whenever a workflow is started.

![data-flow](/basics/workflow-data-flow.png)

Every job worker can read the current payload and modify it when completing a job so that data can be shared between different tasks in a workflow. A workflow model can contain simple, yet powerful payload transformation instructions to keep workers decoupled from each other.

## Data-based Conditions

Some workflows do not always execute the same tasks but need to choose different tasks based on payload and conditions:

![data-conditions](/basics/workflow-conditions.png)

The diamond shape with the "X" in the middle is a special step marking that the workflow decides to take one or the other path.

Conditions use [JSON Path](reference/json-conditions.html) to extract properties and values from the current payload document.

## Events

Events represent things that happen. A workflow can react to events (catching event) as well as emit events (throwing event). For example:

![workflow](/basics/workflow-events.png)

There are different types of events like message or timer.

## Fork / Join Concurrency

In many cases, it is also useful to perform multiple tasks in parallel. This can be achieved with Fork / Join concurrency:

![data-conditions](/basics/workflow-parallel-gw.png)

The diamond shape with the "+" marker means that all outgoing paths are activated and all incoming paths are merged.

Concurrency can also be based on data, meaning that a task is performed for every data item:

![data-conditions](/basics/workflow-parallel-mi.png)


## BPMN 2.0

Zeebe uses [BPMN 2.0](http://www.bpmn.org/) for representing workflows. BPMN is an industry standard which is widely supported by different vendors and implementations. Using BPMN ensures that workflows can be interchanged between Zeebe and other workflow systems.

## YAML Workflows

In addition to BPMN 2.0, Zeebe supports a [YAML](http://yaml.org/) workflow format. It can be used to rapidly write simple workflows in text. Unlike BPMN, it has no visual representation and is not standardized. Zeebe transforms YAML to BPMN on submission.

## BPMN Modeler

Zeebe provides a BPMN modeling tool to create BPMN diagrams and maintain their technical properties. It is a desktop application based on the [bpmn.io](https://bpmn.io) open source project.

The modeler can be downloaded from [github.com/zeebe-io/zeebe-modeler](https://github.com/zeebe-io/zeebe-modeler/releases).
