/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBusinessRuleTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEventBasedGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableExclusiveGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableInclusiveGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableIntermediateThrowEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableReceiveTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableScriptTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.DataObject;
import io.camunda.zeebe.model.bpmn.instance.DataObjectReference;
import io.camunda.zeebe.model.bpmn.instance.DataStoreReference;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.EventBasedGateway;
import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.InclusiveGateway;
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
import io.camunda.zeebe.model.bpmn.instance.Task;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class FlowElementInstantiationTransformer
    implements ModelElementTransformer<FlowElement> {

  private static final Map<Class<?>, Function<String, AbstractFlowElement>> ELEMENT_FACTORIES;
  private static final Set<Class<?>> NON_EXECUTABLE_ELEMENT_TYPES = new HashSet<>();
  private static final UnsafeBuffer EMPTY_BUFFER = new UnsafeBuffer();

  static {
    ELEMENT_FACTORIES = new HashMap<>();

    ELEMENT_FACTORIES.put(Activity.class, ExecutableActivity::new);
    ELEMENT_FACTORIES.put(AdHocSubProcess.class, ExecutableAdHocSubProcess::new);
    ELEMENT_FACTORIES.put(BusinessRuleTask.class, ExecutableBusinessRuleTask::new);
    ELEMENT_FACTORIES.put(BoundaryEvent.class, ExecutableBoundaryEvent::new);
    ELEMENT_FACTORIES.put(CallActivity.class, ExecutableCallActivity::new);
    ELEMENT_FACTORIES.put(EndEvent.class, ExecutableEndEvent::new);
    ELEMENT_FACTORIES.put(EventBasedGateway.class, ExecutableEventBasedGateway::new);
    ELEMENT_FACTORIES.put(ExclusiveGateway.class, ExecutableExclusiveGateway::new);
    ELEMENT_FACTORIES.put(InclusiveGateway.class, ExecutableInclusiveGateway::new);
    ELEMENT_FACTORIES.put(IntermediateCatchEvent.class, ExecutableCatchEventElement::new);
    ELEMENT_FACTORIES.put(IntermediateThrowEvent.class, ExecutableIntermediateThrowEvent::new);
    ELEMENT_FACTORIES.put(ManualTask.class, ExecutableActivity::new);
    ELEMENT_FACTORIES.put(Task.class, ExecutableActivity::new);
    ELEMENT_FACTORIES.put(ParallelGateway.class, ExecutableFlowNode::new);
    ELEMENT_FACTORIES.put(ReceiveTask.class, ExecutableReceiveTask::new);
    ELEMENT_FACTORIES.put(ScriptTask.class, ExecutableScriptTask::new);
    ELEMENT_FACTORIES.put(SendTask.class, ExecutableJobWorkerTask::new);
    ELEMENT_FACTORIES.put(SequenceFlow.class, ExecutableSequenceFlow::new);
    ELEMENT_FACTORIES.put(ServiceTask.class, ExecutableJobWorkerTask::new);
    ELEMENT_FACTORIES.put(StartEvent.class, ExecutableStartEvent::new);
    ELEMENT_FACTORIES.put(SubProcess.class, ExecutableFlowElementContainer::new);
    ELEMENT_FACTORIES.put(UserTask.class, ExecutableUserTask::new);

    NON_EXECUTABLE_ELEMENT_TYPES.add(DataObject.class);
    NON_EXECUTABLE_ELEMENT_TYPES.add(DataObjectReference.class);
    NON_EXECUTABLE_ELEMENT_TYPES.add(DataStoreReference.class);
  }

  @Override
  public Class<FlowElement> getType() {
    return FlowElement.class;
  }

  @Override
  public void transform(final FlowElement element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final Class<?> elementType = element.getElementType().getInstanceType();

    if (!NON_EXECUTABLE_ELEMENT_TYPES.contains(elementType)) {

      final Function<String, AbstractFlowElement> elementFactory =
          ELEMENT_FACTORIES.get(elementType);
      if (elementFactory == null) {
        throw new IllegalStateException("no transformer found for element type: " + elementType);
      }

      final AbstractFlowElement executableElement = elementFactory.apply(element.getId());
      setElementName(element, executableElement);
      setElementDocumentation(element, executableElement);
      setElementProperties(element, executableElement);

      executableElement.setElementType(
          BpmnElementType.bpmnElementTypeFor(element.getElementType().getTypeName()));

      process.addFlowElement(executableElement);
    }
  }

  private void setElementName(
      final FlowElement element, final AbstractFlowElement executableElement) {
    final DirectBuffer elementName =
        Optional.ofNullable(element.getName()).map(BufferUtil::wrapString).orElse(EMPTY_BUFFER);
    executableElement.setName(elementName);
  }

  private void setElementDocumentation(
      final FlowElement element, final AbstractFlowElement executableElement) {
    final DirectBuffer elementDocumentation =
        Optional.ofNullable(element.getDocumentations())
            .flatMap(documentations -> documentations.stream().findFirst())
            .flatMap(documentation -> Optional.ofNullable(documentation.getTextContent()))
            .map(BufferUtil::wrapString)
            .orElse(EMPTY_BUFFER);
    executableElement.setDocumentation(elementDocumentation);
  }

  private void setElementProperties(
      final FlowElement element, final AbstractFlowElement executableElement) {
    Optional.ofNullable(element.getSingleExtensionElement(ZeebeProperties.class))
        .map(ZeebeProperties::getProperties)
        .filter(zeebeProperties -> !zeebeProperties.isEmpty())
        .map(
            properties ->
                properties.stream()
                    .filter(
                        zeebeProperty ->
                            zeebeProperty.getName() != null && !zeebeProperty.getName().isEmpty())
                    .collect(
                        HashMap<String, String>::new,
                        (map, zeebeProperty) ->
                            map.put(zeebeProperty.getName(), zeebeProperty.getValue()),
                        HashMap::putAll))
        .ifPresent(executableElement::setProperties);
  }
}
