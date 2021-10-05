/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEventBasedGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableExclusiveGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableIntermediateThrowEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableReceiveTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.EventBasedGateway;
import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.camunda.zeebe.model.bpmn.instance.ManualTask;
import io.camunda.zeebe.model.bpmn.instance.ParallelGateway;
import io.camunda.zeebe.model.bpmn.instance.ReceiveTask;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class FlowElementInstantiationTransformer
    implements ModelElementTransformer<FlowElement> {

  private static final Map<Class<?>, Function<String, AbstractFlowElement>> ELEMENT_FACTORIES;

  static {
    ELEMENT_FACTORIES = new HashMap<>();

    ELEMENT_FACTORIES.put(Activity.class, ExecutableActivity::new);
    ELEMENT_FACTORIES.put(BusinessRuleTask.class, ExecutableJobWorkerTask::new);
    ELEMENT_FACTORIES.put(BoundaryEvent.class, ExecutableBoundaryEvent::new);
    ELEMENT_FACTORIES.put(CallActivity.class, ExecutableCallActivity::new);
    ELEMENT_FACTORIES.put(EndEvent.class, ExecutableEndEvent::new);
    ELEMENT_FACTORIES.put(EventBasedGateway.class, ExecutableEventBasedGateway::new);
    ELEMENT_FACTORIES.put(ExclusiveGateway.class, ExecutableExclusiveGateway::new);
    ELEMENT_FACTORIES.put(IntermediateCatchEvent.class, ExecutableCatchEventElement::new);
    ELEMENT_FACTORIES.put(IntermediateThrowEvent.class, ExecutableIntermediateThrowEvent::new);
    ELEMENT_FACTORIES.put(ManualTask.class, ExecutableActivity::new);
    ELEMENT_FACTORIES.put(ParallelGateway.class, ExecutableFlowNode::new);
    ELEMENT_FACTORIES.put(ReceiveTask.class, ExecutableReceiveTask::new);
    ELEMENT_FACTORIES.put(ScriptTask.class, ExecutableJobWorkerTask::new);
    ELEMENT_FACTORIES.put(SendTask.class, ExecutableJobWorkerTask::new);
    ELEMENT_FACTORIES.put(SequenceFlow.class, ExecutableSequenceFlow::new);
    ELEMENT_FACTORIES.put(ServiceTask.class, ExecutableJobWorkerTask::new);
    ELEMENT_FACTORIES.put(StartEvent.class, ExecutableStartEvent::new);
    ELEMENT_FACTORIES.put(SubProcess.class, ExecutableFlowElementContainer::new);
    ELEMENT_FACTORIES.put(UserTask.class, ExecutableJobWorkerTask::new);
  }

  @Override
  public Class<FlowElement> getType() {
    return FlowElement.class;
  }

  @Override
  public void transform(final FlowElement element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final Class<?> elementType = element.getElementType().getInstanceType();

    final Function<String, AbstractFlowElement> elementFactory = ELEMENT_FACTORIES.get(elementType);
    if (elementFactory == null) {
      throw new IllegalStateException("no transformer found for element type: " + elementType);
    }

    final AbstractFlowElement executableElement = elementFactory.apply(element.getId());

    executableElement.setElementType(
        BpmnElementType.bpmnElementTypeFor(element.getElementType().getTypeName()));

    process.addFlowElement(executableElement);
  }
}
