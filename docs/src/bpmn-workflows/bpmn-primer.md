# BPMN Primer

Business Process Model And Notation 2.0 (BPMN) is an industry standard for workflow modeling and execution. A BPMN workflow is an XML document that has a visual representation. For example, here is a BPMN workflow:

![workflow](/bpmn-workflows/workflow.png)

This is the corresponding XML: <a href="/bpmn-workflows/workflow.bpmn" target="_blank">Click here</a>.

This duality makes BPMN very powerful. The XML document contains all the necessary information to be interpreted by workflow engines and modeling tools like Zeebe. At the same time, the visual representation contains just enough information to be quickly understood by humans, even when they are non-technical people. The BPMN model is source code and documentation in one artifact.

The following is an introduction to BPMN 2.0, its elements and their execution semantics. It tries to briefly provide an intuitive understanding of BPMN's power but does not cover the entire feature set. For more exhaustive BPMN resources, see the [reference links](#additional-resources) at the end of this section.

## Modeling BPMN Diagrams

The best tool for modeling BPMN diagrams for Zeebe is [Zeebe Modeler](https://github.com/zeebe-io/zeebe-modeler/releases).

## BPMN Elements

### Sequence Flow: Controlling the Flow of Execution

A core concept of BPMN is a *sequence flow* that defines the order in which steps in the workflow happen. In BPMN's visual representation, a sequence flow is an arrow connecting two elements. The direction of the arrow indicates their order of execution.

<center>
![workflow](/bpmn-workflows/sequenceflow.png)
</center>

You can think of workflow execution as tokens running through the workflow model. When a workflow is started, a token is spawned at the beginning of the model. It advances with every completed step. When the token reaches the end of the workflow, it is consumed and the workflow instance ends. Zeebe's task is to drive the token and to make sure that the job workers are invoked whenever necessary.

<center>
<video src="/bpmn-workflows/sequenceflow.mp4" autoplay muted loop height="200px"></video>
</center>

### Tasks: Units of Work

The basic elements of BPMN workflows are *tasks*, atomic units of work that are composed to create a meaningful result. Whenever a token reaches a task, the token stops and Zeebe creates a job and notifies a registered worker to perform work. When that handler signals completion, then the token continues on the outgoing sequence flow.

<center>
<video src="/bpmn-workflows/tasks.mp4" autoplay muted loop height="300px"></video>
</center>

Choosing the granularity of a task is up to the person modeling the workflow. For example, the activity of processing an order can be modeled as a single *Process Order* task, or as three individual tasks *Collect Money*, *Fetch Items*, *Ship Parcel*. If you use Zeebe to orchestrate microservices, one task can represent one microservice invocation.

See the [Tasks](/bpmn-workflows/tasks.html) section on which types of tasks are currently supported and how to use them.

### Gateways: Steering Flow

Gateways are elements that route tokens in more complex patterns than plain sequence flow.

BPMN's *exclusive gateway* chooses one sequence flow out of many based on data:

<center>
<video src="/bpmn-workflows/exclusive-gw.mp4" autoplay muted loop height="300px"></video>
</center>

BPMN's *parallel gateway* generates new tokens by activating multiple sequence flows in parallel:

<center>
<video src="/bpmn-workflows/parallel-gw.mp4" autoplay muted loop height="300px"></video>
</center>

See the [Gateways](/bpmn-workflows/gateways.html) section on which types of gateways are currently supported and how to use them.

### Events: Waiting for Something to Happen

*Events* in BPMN represent things that *happen*. A workflow can react to events (*catching* event) as well as emit events (*throwing* event). For example:

<center>
<video src="/bpmn-workflows/catch-event.mp4" autoplay muted loop height="200px"></video>
</center>

The circle with the envelope symbol is a catching message event. It makes the token continue as soon as a message is received. The XML representation of the workflow contains the criteria for which kind of message triggers continuation.

Events can be added to the workflow in various ways. Not only can they be used to make a token wait at a certain point, but also for interrupting a token's progress.

See the [Events](/bpmn-workflows/events.html) section on which types of events are currently supported and how to use them.

### Sub Processes: Grouping Elements

*Sub Processes* are element containers that allow defining common functionality. For example, we can attach an event to a sub process's border:

![payload](/bpmn-workflows/sub-process.gif)

When the event is triggered, the sub process is interrupted regardless which of its elements is currently active.

See the [Sub Processes](/bpmn-workflows/subprocesses.html) section on which types of sub processes are currently supported and how to use them.

## Additional Resources

* [BPMN Specification](http://www.bpmn.org/)
* [BPMN Tutorial](https://camunda.com/bpmn/)
* [Full BPMN Reference](https://camunda.com/bpmn/reference/)
* [BPMN Book](https://www.amazon.com/Real-Life-BPMN-3rd-introductions-CMMN-ebook/dp/B01NAL67J8)
