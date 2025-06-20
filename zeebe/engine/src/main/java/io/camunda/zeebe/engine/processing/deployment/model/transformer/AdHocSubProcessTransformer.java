/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableAdHocSubProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskDefinitionTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskHeadersTransformer;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.CompletionCondition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHoc;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AdHocSubProcessTransformer implements ModelElementTransformer<AdHocSubProcess> {

  private final TaskDefinitionTransformer taskDefinitionTransformer =
      new TaskDefinitionTransformer();
  private final TaskHeadersTransformer taskHeadersTransformer = new TaskHeadersTransformer();

  @Override
  public Class<AdHocSubProcess> getType() {
    return AdHocSubProcess.class;
  }

  @Override
  public void transform(final AdHocSubProcess element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final var executableAdHocSubProcess =
        process.getElementById(element.getId(), ExecutableAdHocSubProcess.class);

    final var expressionLanguage = context.getExpressionLanguage();
    setActiveElementsCollection(executableAdHocSubProcess, element, expressionLanguage);

    Optional.ofNullable(element.getCompletionCondition())
        .map(CompletionCondition::getTextContent)
        .filter(e -> !e.isBlank())
        .map(context.getExpressionLanguage()::parseExpression)
        .ifPresent(executableAdHocSubProcess::setCompletionCondition);

    executableAdHocSubProcess.setCancelRemainingInstances(element.isCancelRemainingInstances());

    final Collection<AbstractFlowElement> childElements =
        executableAdHocSubProcess.getChildElements();
    setAdHocActivities(executableAdHocSubProcess, childElements);

    setOutputCollection(executableAdHocSubProcess, element, context);

    setImplementationType(executableAdHocSubProcess, element);

    setJobWorkerProperties(executableAdHocSubProcess, element, context);

    setInnerInstance(executableAdHocSubProcess, childElements, process);

    setAdHocActivitiesDescription(executableAdHocSubProcess);
  }

  private static void setActiveElementsCollection(
      final ExecutableAdHocSubProcess executableAdHocSubProcess,
      final AdHocSubProcess element,
      final ExpressionLanguage expressionLanguage) {
    Optional.ofNullable(element.getSingleExtensionElement(ZeebeAdHoc.class))
        .flatMap(
            extensionElement -> Optional.ofNullable(extensionElement.getActiveElementsCollection()))
        .filter(expression -> !expression.isBlank())
        .map(expressionLanguage::parseExpression)
        .ifPresent(executableAdHocSubProcess::setActiveElementsCollection);
  }

  private static void setAdHocActivities(
      final ExecutableAdHocSubProcess executableAdHocSubProcess,
      final Collection<AbstractFlowElement> childElements) {
    childElements.stream()
        .filter(ExecutableFlowNode.class::isInstance)
        .map(ExecutableFlowNode.class::cast)
        .filter(e -> isAdHocActivity(e.getElementType(), e.getEventType()))
        .filter(flowElement -> flowElement.getIncoming().isEmpty())
        .forEach(executableAdHocSubProcess::addAdHocActivity);
  }

  /**
   * Returns true if the given element type and event type represent an element that can be
   * activated as part of the ad-hoc sub-process, otherwise false.
   */
  @SuppressWarnings("DuplicateBranchesInSwitch")
  private static boolean isAdHocActivity(
      final BpmnElementType elementType, final BpmnEventType eventType) {
    return switch (elementType) {
      case UNSPECIFIED -> false;
      case SEQUENCE_FLOW -> false;
      case PROCESS, EVENT_SUB_PROCESS -> false;
      case AD_HOC_SUB_PROCESS_INNER_INSTANCE -> false;
      case START_EVENT, BOUNDARY_EVENT, END_EVENT -> false;
      case SUB_PROCESS, AD_HOC_SUB_PROCESS -> true;
      case TASK, MANUAL_TASK, SERVICE_TASK, USER_TASK, SCRIPT_TASK, BUSINESS_RULE_TASK -> true;
      case SEND_TASK, RECEIVE_TASK -> true;
      case EXCLUSIVE_GATEWAY, INCLUSIVE_GATEWAY, PARALLEL_GATEWAY, EVENT_BASED_GATEWAY -> true;
      case MULTI_INSTANCE_BODY -> true;
      case CALL_ACTIVITY -> true;
      case INTERMEDIATE_THROW_EVENT -> true;
      case INTERMEDIATE_CATCH_EVENT -> true;
    };
  }

  private static void setImplementationType(
      final ExecutableAdHocSubProcess executableAdHocSubProcess, final AdHocSubProcess element) {

    final ZeebeAdHocImplementationType implementationType =
        Optional.ofNullable(element.getSingleExtensionElement(ZeebeAdHoc.class))
            .map(ZeebeAdHoc::getImplementationType)
            .orElse(ZeebeAdHocImplementationType.BPMN);

    executableAdHocSubProcess.setImplementationType(implementationType);
  }

  private void setJobWorkerProperties(
      final ExecutableAdHocSubProcess executableAdHocSubProcess,
      final AdHocSubProcess element,
      final TransformContext context) {

    final var taskDefinition = element.getSingleExtensionElement(ZeebeTaskDefinition.class);
    taskDefinitionTransformer.transform(executableAdHocSubProcess, context, taskDefinition);

    final var taskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);
    taskHeadersTransformer.transform(executableAdHocSubProcess, taskHeaders, element);
  }

  private static void setInnerInstance(
      final ExecutableAdHocSubProcess executableAdHocSubProcess,
      final Collection<AbstractFlowElement> childElements,
      final ExecutableProcess process) {

    final ExecutableFlowElementContainer adHocSubProcessInnerInstance =
        new ExecutableFlowElementContainer(executableAdHocSubProcess.getInnerInstanceId());
    adHocSubProcessInnerInstance.setElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE);
    adHocSubProcessInnerInstance.setFlowScope(executableAdHocSubProcess);

    childElements.forEach(
        childElement -> {
          childElement.setFlowScope(adHocSubProcessInnerInstance);
          adHocSubProcessInnerInstance.addChildElement(childElement);
        });

    executableAdHocSubProcess.getChildElements().clear();
    executableAdHocSubProcess.addChildElement(adHocSubProcessInnerInstance);

    process.addFlowElement(adHocSubProcessInnerInstance);
  }

  private static void setAdHocActivitiesDescription(
      final ExecutableAdHocSubProcess executableAdHocSubProcess) {
    final List<Map<String, String>> adHocActivityDescription =
        executableAdHocSubProcess.getAdHocActivitiesById().values().stream()
            .map(
                flowNode -> {
                  final String elementId = BufferUtil.bufferAsString(flowNode.getId());
                  final String elementName = BufferUtil.bufferAsString(flowNode.getName());
                  final String documentation =
                      BufferUtil.bufferAsString(flowNode.getDocumentation());

                  return Map.ofEntries(
                      Map.entry("elementId", elementId),
                      Map.entry("elementName", elementName),
                      Map.entry("documentation", documentation));
                })
            .toList();

    final byte[] msgPack = MsgPackConverter.convertToMsgPack(adHocActivityDescription);
    executableAdHocSubProcess.setAdHocActivitiesDescription(BufferUtil.wrapArray(msgPack));
  }

  private void setOutputCollection(
      final ExecutableAdHocSubProcess executableAdHocSubProcess,
      final AdHocSubProcess element,
      final TransformContext context) {
    Optional.ofNullable(element.getSingleExtensionElement(ZeebeAdHoc.class))
        .map(ZeebeAdHoc::getOutputCollection)
        .filter(e -> !e.isEmpty())
        .map(BufferUtil::wrapString)
        .ifPresent(executableAdHocSubProcess::setOutputCollection);

    Optional.ofNullable(element.getSingleExtensionElement(ZeebeAdHoc.class))
        .map(ZeebeAdHoc::getOutputElement)
        .filter(e -> !e.isEmpty())
        .map(e -> context.getExpressionLanguage().parseExpression(e))
        .ifPresent(executableAdHocSubProcess::setOutputElement);
  }
}
