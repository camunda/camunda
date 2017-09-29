# Workflows

Workflows are a form of flow chart and allow you to orchestrate different loosely coupled components. In Zeebe, these components are called _Task Workers_. Task workers can be implemented in a variety of ways including inside a regular app, as microservices, \(lambda\) functions, command line tools, etc.

## Sequences

The simplest kind of workflow is just a sequence of steps. Each step represents an invocation of a task worker.

![workflow-sequence](/basics/workflow-sequence.png)

You can think of Zeebe as a kind of state machine which starts at a particular step, triggers the worker and then waits for it to complete its work. Once the work is completed, it continues to the next step. If the worker fails to complete the work, it remains at the current step, potentially retrying until it eventually succeeds.

## Data Flow

As Zeebe progresses from one step to the next in a workflow, it optionaly "carries over" payload \(data\). This concept is called data flow. Data flow makes it possible to share data between different steps in a workflow.

![data-flow](/basics/workflow-data-flow.png)

Physically data flow is a Json document which gets created when a workflow is initially started. It then gets passed to the first step which can read and modify it. As the first step completes, the resulting document is carried over and passed to the next step and so forth.

## Data Conditions

Some workflows do not always execute the same steps but need to pick and choose different steps based on data and conditions:

![data-conditions](/basics/workflow-conditions.png)

The diamond shape with the "X" in the middle is a special kind of step marking that the workflow decides to take one or the other path.

Conditions use Json path to select properties and values from the current payload document.

## Fork / Join Concurrency

> Coming soon

In many cases, it is also useful to perform multiple steps in parallel. This can be achieved with Fork / Join concurrency.

\[TODO: Image\]

The diamond with the "+" in the middle marks that the workflow forks or joins multiple concurrent branches.

## BPMN 2.0

Zeebe uses mainly BPMN 2.0 for representing workflows. BPMN is an industry standard which is widely supported by different vendors and implementations. Using BPMN ensures that workflows can be interchanged between Zeebe and other workflow systems.

## YAML Workflows

As an alternative to BPMN 2.0, Zeebe supports a YAML format for workflows. It offers an easy way to define plain workflows for technical users. Unlike BPMN, it has no visual representation and is not standardized.

## The Modeler

Zeebe provides a Modeler for BPMN. The modeler is a standalone desktop application based on the [https://bpmn.io](https://bpmn.io "bpmn.io") open source project.

The modeler can be downloaded from [github.com/zeebe-io/zeebe-modeler](https://github.com/zeebe-io/zeebe-modeler/releases).

## Deploying a Workflow

For Zeebe to know your workflow you must first "deploy" it. Workflows can be deployed using the corresponding APIs in the clients or using the command line tool:

```bash
$ zbctl deploy hello-workflow.bpmn
```

## Starting Workflow Instances

Once a workflow is deployed, it can be started. Again, this is possible using the corresponding APIs in the clients or using the command line tool

TODO: example
