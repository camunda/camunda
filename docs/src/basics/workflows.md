# Workflows

Workflows are a form of flow chart and allow you to orchestrate different loosely coupled components. In Zeebe, these components are called _Task Workers_. Task workers can be implemented in a variety of ways including inside a regular app, as microservices, \(lambda\) functions, command line tools, etc.

## Sequences

The simplest kind of workflow is just a sequence of steps. Each step represents an invocation of a task worker.

\[TODO: Image\]

You can think of Zeebe as a kind of state machine which starts at a particular step, triggers the worker and then waits for it to complete its work. Once the work is completed, it continues to the next step. If the worker fails to complete the work, it remains at the current step, potentially retrying until it eventually succeeds.

## Data Flow

As Zeebe progresses from one step to the next in a workflow, it optionaly "carries over" payload \(data\). This concept is called data flow. Data flow makes it possible to share data between different steps in a workflow.

\[TODO: Image\]

Physically data flow is a Json document which gets created when a workflow is initially started. It then gets passed to the first step which can read and modify it. As the first step completes, the resulting document is carried over and passed to the next step and so forth.

## Data Conditions

Some workflows do not aways execute the same steps but need to pick and choose different steps based on data and conditions:

\[TODO: Image\]

The diamond shape with the "X" in the middle is a special kind of step marking that the workflow decides to take one or the other path.

Conditions use Json path to select properties and values from the current payload document.

## Fork / Join Concurrency

In many cases, it is also useful to perform multiple steps in parallel. This can be achieved with Fork / Join concurrency.

\[TODO: Image\]

The diamond with the "+" in the middle marks that the workflow forks or joins multiple concurrent branches.

## BPMN 2.0

Zeebe uses BPMN 2.0 for representing workflows. BPMN is an industry standard which is widely supported by different vendors and implementations. Using BPMN ensures that workflows can be interchanged between Zeebe and other workflow systems.

## The Modeler

Zeebe provides a Modeler for BPMN. The modeler is a standalone desktop application based on the [https://bpmn.io](https://bpmn.io "bpmn.io") open source project.

The modeler can be downloaded by clicking here.

## Deploying a Workflow

For Zeebe to know your workflow you must first "deploy" it. Workflows can be deployed using the corresponding APIs in the clients or using the command line tool:

```bash
$ Zeebe deploy hello-workflow.bpmn
```

## Starting Workflow Instances

Once a workflow is deployed, it can be started. Again, this is possible using the corresponding APIs in the clients or using the command line tool:

```bash
$ Zeebe start --bpmn-process-id=hello --payload='{ "greeting": "hello" }'
```
