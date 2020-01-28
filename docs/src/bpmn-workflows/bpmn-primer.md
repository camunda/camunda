# BPMN Primer

Business Process Model And Notation 2.0 (BPMN) is an industry standard for workflow modeling and execution. A BPMN workflow is an XML document that has a visual representation. For example, here is a BPMN workflow:

![workflow](/bpmn-workflows/workflow.png)

<details>
  <summary>The corresponding XML</summary>
  <p>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Zeebe Modeler" exporterVersion="0.1.0">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Order Placed">
      <bpmn:outgoing>SequenceFlow_1bq1azi</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:sequenceFlow id="SequenceFlow_1bq1azi" sourceRef="StartEvent_1" targetRef="Task_1f47b9v" />
    <bpmn:sequenceFlow id="SequenceFlow_09hqjpg" sourceRef="Task_1f47b9v" targetRef="Task_1109y9g" />
    <bpmn:sequenceFlow id="SequenceFlow_1ea1mpb" sourceRef="Task_1109y9g" targetRef="Task_00moy91" />
    <bpmn:endEvent id="EndEvent_0a27csw" name="Order Delivered">
      <bpmn:incoming>SequenceFlow_0ojoaqz</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="SequenceFlow_0ojoaqz" sourceRef="Task_00moy91" targetRef="EndEvent_0a27csw" />
    <bpmn:serviceTask id="Task_1f47b9v" name="Collect Money">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="collect-money" retries="3" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_1bq1azi</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_09hqjpg</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="Task_1109y9g" name="Fetch Items">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="fetch-items" retries="3" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_09hqjpg</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_1ea1mpb</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="Task_00moy91" name="Ship Parcel">
      <bpmn:extensionElements>
        <zeebe:taskDefinition type="ship-parcel" retries="3" />
      </bpmn:extensionElements>
      <bpmn:incoming>SequenceFlow_1ea1mpb</bpmn:incoming>
      <bpmn:outgoing>SequenceFlow_0ojoaqz</bpmn:outgoing>
    </bpmn:serviceTask>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
        <dc:Bounds x="191" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="175" y="138" width="68" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1bq1azi_di" bpmnElement="SequenceFlow_1bq1azi">
        <di:waypoint xsi:type="dc:Point" x="227" y="120" />
        <di:waypoint xsi:type="dc:Point" x="280" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="253.5" y="99" width="0" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_09hqjpg_di" bpmnElement="SequenceFlow_09hqjpg">
        <di:waypoint xsi:type="dc:Point" x="380" y="120" />
        <di:waypoint xsi:type="dc:Point" x="440" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="410" y="99" width="0" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="SequenceFlow_1ea1mpb_di" bpmnElement="SequenceFlow_1ea1mpb">
        <di:waypoint xsi:type="dc:Point" x="540" y="120" />
        <di:waypoint xsi:type="dc:Point" x="596" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="568" y="99" width="0" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="EndEvent_0a27csw_di" bpmnElement="EndEvent_0a27csw">
        <dc:Bounds x="756" y="102" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="734" y="142" width="81" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_0ojoaqz_di" bpmnElement="SequenceFlow_0ojoaqz">
        <di:waypoint xsi:type="dc:Point" x="696" y="120" />
        <di:waypoint xsi:type="dc:Point" x="756" y="120" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="726" y="99" width="0" height="12" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_0lao700_di" bpmnElement="Task_1f47b9v">
        <dc:Bounds x="280" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_0eetpqx_di" bpmnElement="Task_1109y9g">
        <dc:Bounds x="440" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ServiceTask_09won99_di" bpmnElement="Task_00moy91">
        <dc:Bounds x="596" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```
  </p>
</details>


This duality makes BPMN very powerful. The XML document contains all the necessary information to be interpreted by workflow engines and modeling tools like Zeebe. At the same time, the visual representation contains just enough information to be quickly understood by humans, even when they are non-technical people. The BPMN model is source code and documentation in one artifact.

The following is an introduction to BPMN 2.0, its elements and their execution semantics. It tries to briefly provide an intuitive understanding of BPMN's power but does not cover the entire feature set. For more exhaustive BPMN resources, see the [reference links](#additional-resources) at the end of this section.

## Modeling BPMN Diagrams

The best tool for modeling BPMN diagrams for Zeebe is the **Zeebe Modeler**.

![overview](/bpmn-workflows/zeebe-modeler.gif)

* [Download page](https://github.com/zeebe-io/zeebe-modeler/releases)
* [Source code repository](https://github.com/zeebe-io/zeebe-modeler)

## BPMN Elements

### Sequence Flow: Controlling the Flow of Execution

A core concept of BPMN is a *sequence flow* that defines the order in which steps in the workflow happen. In BPMN's visual representation, a sequence flow is an arrow connecting two elements. The direction of the arrow indicates their order of execution.

<center>
<img src="/bpmn-workflows/sequenceflow.png" alt="workflow">
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
* [BPMN Book](https://www.amazon.com/dp/1086302095/)
