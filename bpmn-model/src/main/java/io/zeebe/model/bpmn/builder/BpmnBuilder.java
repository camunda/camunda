/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.impl.instance.*;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.impl.transformation.BpmnTransformer;
import io.zeebe.model.bpmn.impl.validation.BpmnValidator;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class BpmnBuilder {
  private final BpmnTransformer transformer;
  private final BpmnValidator validator;

  private final AtomicLong nextId = new AtomicLong();

  private final List<FlowNodeImpl> flowNodes = new ArrayList<>();

  private ProcessImpl process;
  private FlowNodeImpl sourceNode;
  private SequenceFlowImpl sequenceFlow;
  private ExclusiveGatewayImpl exclusiveGateway;

  public BpmnBuilder(BpmnTransformer transformer, BpmnValidator validator) {
    this.transformer = transformer;
    this.validator = validator;
  }

  public BpmnBuilder wrap(String bpmnProcessId) {
    nextId.set(1L);

    this.process = new ProcessImpl();
    process.setId(bpmnProcessId);
    process.setExecutable(true);

    sourceNode = null;
    sequenceFlow = null;
    exclusiveGateway = null;
    flowNodes.clear();

    return this;
  }

  private String generateId(String prefix) {
    return prefix + "-" + nextId.getAndIncrement();
  }

  private void addFlowNode(FlowNodeImpl flowNode) {
    flowNodes.add(flowNode);
  }

  private void connectToSequenceFlow(final FlowNodeImpl targetNode) {
    if (sequenceFlow == null) {
      sequenceFlow();
    }

    sequenceFlow.setTargetRef(targetNode.getId());
    targetNode.getIncoming().add(sequenceFlow);

    sequenceFlow = null;
    sourceNode = targetNode;
  }

  public BpmnBuilder startEvent() {
    return startEvent(generateId("start-event"));
  }

  public BpmnBuilder startEvent(String id) {
    final StartEventImpl startEvent = new StartEventImpl();
    startEvent.setId(id);

    process.getStartEvents().add(startEvent);
    addFlowNode(startEvent);

    sourceNode = startEvent;

    return this;
  }

  public BpmnBuilder sequenceFlow() {
    return sequenceFlow(generateId("sequence-flow"));
  }

  public BpmnBuilder sequenceFlow(Consumer<BpmnSequenceFlowBuilder> builder) {
    return sequenceFlow(generateId("sequence-flow"), builder);
  }

  public BpmnBuilder sequenceFlow(String id) {
    return sequenceFlow(id, c -> c.done());
  }

  public BpmnBuilder sequenceFlow(String id, Consumer<BpmnSequenceFlowBuilder> builder) {
    final SequenceFlowImpl sequenceFlow = new SequenceFlowImpl();
    sequenceFlow.setId(id);

    sequenceFlow.setSourceRef(sourceNode.getId());
    sourceNode.getOutgoing().add(sequenceFlow);

    process.getSequenceFlows().add(sequenceFlow);

    final BpmnSequenceFlowBuilder sequenceFlowBuilder =
        new BpmnSequenceFlowBuilder(this, sequenceFlow, sourceNode);
    builder.accept(sequenceFlowBuilder);
    sequenceFlowBuilder.done();

    this.sequenceFlow = sequenceFlow;

    return this;
  }

  public BpmnBuilder endEvent() {
    return endEvent(generateId("end-event"));
  }

  public BpmnBuilder endEvent(String id) {
    final EndEventImpl endEvent = new EndEventImpl();
    endEvent.setId(id);

    connectToSequenceFlow(endEvent);

    process.getEndEvents().add(endEvent);
    addFlowNode(endEvent);

    return endCurrentFlow();
  }

  public BpmnServiceTaskBuilder serviceTask() {
    return serviceTask(generateId("service-task"));
  }

  public BpmnServiceTaskBuilder serviceTask(String id) {
    final ServiceTaskImpl serviceTask = new ServiceTaskImpl();
    serviceTask.setId(id);

    connectToSequenceFlow(serviceTask);

    process.getServiceTasks().add(serviceTask);
    addFlowNode(serviceTask);

    return new BpmnServiceTaskBuilder(this, serviceTask);
  }

  public BpmnBuilder serviceTask(String id, Consumer<BpmnServiceTaskBuilder> builder) {
    final BpmnServiceTaskBuilder serviceTaskBuilder = serviceTask(id);
    builder.accept(serviceTaskBuilder);
    return serviceTaskBuilder.done();
  }

  public BpmnBuilder exclusiveGateway() {
    return exclusiveGateway(generateId("exclusive-gateway"));
  }

  public BpmnBuilder exclusiveGateway(String id) {
    final ExclusiveGatewayImpl exclusiveGateway = new ExclusiveGatewayImpl();
    exclusiveGateway.setId(id);

    connectToSequenceFlow(exclusiveGateway);

    process.getExclusiveGateways().add(exclusiveGateway);
    addFlowNode(exclusiveGateway);

    this.exclusiveGateway = exclusiveGateway;

    return this;
  }

  private FlowNodeImpl getFlowNode(String activityId) {
    return flowNodes
        .stream()
        .filter(e -> e.getId().equals(activityId))
        .findFirst()
        .orElseThrow(() -> new RuntimeException("No activity found with id: " + activityId));
  }

  public BpmnBuilder continueAt(String activityId) {
    this.sourceNode = getFlowNode(activityId);

    return this;
  }

  public BpmnBuilder joinWith(String activityId) {
    final FlowNodeImpl flowNode = getFlowNode(activityId);

    connectToSequenceFlow(flowNode);

    this.sourceNode = flowNode;

    return this;
  }

  private BpmnBuilder endCurrentFlow() {
    if (exclusiveGateway != null) {
      continueAt(exclusiveGateway.getId());
    }

    return this;
  }

  public WorkflowDefinition done() {
    final DefinitionsImpl definitionsImpl = new DefinitionsImpl();
    definitionsImpl.getProcesses().add(process);

    validator.validate(definitionsImpl);

    return transformer.transform(definitionsImpl);
  }
}
