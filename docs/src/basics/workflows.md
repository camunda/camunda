# Workflows

Workflows are flow-chart-like blueprints that you can use to define the orchestration of loosely coupled *task workers*.

A task worker is your implementation of the business logic of a step in a workflow. It must embed a Zeebe client to connect to the broker but other than that there are no restrictions on its implementation. You can choose to write a worker as a microservice, as part of a classical three-tier application, as a \(lambda\) function, via command line tools, etc.

Running a workflow then requires two steps: Submitting the workflow to Zeebe and connecting the required task workers.

## Sequences

The simplest kind of workflow is an ordered sequence of steps. Each step represents an invocation of a task worker.

![workflow-sequence](/basics/workflow-sequence.png)

You can think of Zeebe's workflow orchestration as a state machine. It starts at a particular step, triggers the worker and then waits for the worker to complete its work. Once the work is completed, it continues to the next step. If the worker fails to complete the work, it remains at the current step, potentially retrying until it eventually succeeds.

## Data Flow

As Zeebe progresses from one step to the next in a workflow, it can move custom data in the form of a JSON document along. This data is called the *workflow payload* and is created whenever a workflow is started.

![data-flow](/basics/workflow-data-flow.png)

Each task worker can read the current payload and modify it when completing a task so that data can be shared between different steps in a workflow. A workflow model can contain simple, yet powerful payload transformation instructions to keep task workers decoupled from each other.

## Data-based Conditions

Some workflows do not always execute the same steps but need to choose different steps based on payload and conditions:

![data-conditions](/basics/workflow-conditions.png)

The diamond shape with the "X" in the middle is a special step marking that the workflow decides to take one or the other path.

Conditions use [JSON Path](http://goessner.net/articles/JsonPath/) to extract properties and values from the current payload document.

## Fork / Join Concurrency

> Coming soon

In many cases, it is also useful to perform multiple steps in parallel. This can be achieved with Fork / Join concurrency.

## BPMN 2.0

Zeebe uses [BPMN 2.0](http://www.bpmn.org/) for representing workflows. BPMN is an industry standard which is widely supported by different vendors and implementations. Using BPMN ensures that workflows can be interchanged between Zeebe and other workflow systems.

## YAML Workflows

In addition to BPMN 2.0, Zeebe supports a [YAML](http://yaml.org/) workflow format. It can be used to rapidly write simple workflows in text. Unlike BPMN, it has no visual representation and is not standardized. Zeebe transforms YAML to BPMN on submission.

## BPMN Modeler

Zeebe provides a BPMN modeling tool to create BPMN diagrams and maintain their technical properties. It is a desktop application based on the [bpmn.io](https://bpmn.io) open source project.

The modeler can be downloaded from [github.com/zeebe-io/zeebe-modeler](https://github.com/zeebe-io/zeebe-modeler/releases).
