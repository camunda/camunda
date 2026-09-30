/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BpmnElementsWithDeploymentBinding implements DeploymentResourceContext {

  private final List<ZeebeCalledElement> calledElements = new ArrayList<>();
  private final List<ZeebeCalledDecision> calledDecisions = new ArrayList<>();
  private final List<ZeebeFormDefinition> formDefinitions = new ArrayList<>();

  public void addFromProcess(final Process process) {
    process
        .getFlowElements()
        .forEach(
            element -> {
              switch (element) {
                case final CallActivity callActivity -> handleCallActivity(callActivity);
                case final BusinessRuleTask task -> handleBusinessRuleTask(task);
                case final UserTask task -> handleUserTask(task);
                default -> {}
              }
            });
  }

  public List<ZeebeCalledElement> getCalledElements() {
    return calledElements;
  }

  public List<ZeebeCalledDecision> getCalledDecisions() {
    return calledDecisions;
  }

  public List<ZeebeFormDefinition> getFormDefinitions() {
    return formDefinitions;
  }

  private void handleCallActivity(final CallActivity callActivity) {
    Optional.ofNullable(callActivity.getSingleExtensionElement(ZeebeCalledElement.class))
        .filter(
            calledElement ->
                calledElement.getBindingType() == ZeebeBindingType.deployment
                    && isNotExpression(calledElement.getProcessId()))
        .ifPresent(calledElements::add);
  }

  private void handleBusinessRuleTask(final BusinessRuleTask task) {
    Optional.ofNullable(task.getSingleExtensionElement(ZeebeCalledDecision.class))
        .filter(
            calledDecision ->
                calledDecision.getBindingType() == ZeebeBindingType.deployment
                    && isNotExpression(calledDecision.getDecisionId()))
        .ifPresent(calledDecisions::add);
  }

  private void handleUserTask(final UserTask task) {
    Optional.ofNullable(task.getSingleExtensionElement(ZeebeFormDefinition.class))
        .filter(
            formDefinition ->
                formDefinition.getBindingType() == ZeebeBindingType.deployment
                    && isNotExpression(formDefinition.getFormId()))
        .ifPresent(formDefinitions::add);
  }

  private boolean isNotExpression(final String s) {
    return !s.startsWith("=");
  }
}
