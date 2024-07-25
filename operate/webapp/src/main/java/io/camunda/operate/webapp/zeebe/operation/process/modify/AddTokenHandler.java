/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AddTokenHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(AddTokenHandler.class);

  public ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 addToken(
      final ModifyProcessInstanceCommandStep1 currentStep, final Modification modification) {
    // 0. Prepare
    final String flowNodeId = modification.getToFlowNodeId();
    final Map<String, List<Map<String, Object>>> flowNodeId2variables =
        modification.variablesForAddToken();
    LOGGER.debug("Add token to flowNodeId {} with variables: {}", flowNodeId, flowNodeId2variables);
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep;
    // 1. Activate
    if (modification.getAncestorElementInstanceKey() != null) {
      nextStep =
          currentStep.activateElement(flowNodeId, modification.getAncestorElementInstanceKey());
    } else {
      nextStep = currentStep.activateElement(flowNodeId);
    }
    // 2. Add variables
    if (flowNodeId2variables != null) {
      for (final String scopeId : flowNodeId2variables.keySet()) {
        final List<Map<String, Object>> variablesForFlowNode = flowNodeId2variables.get(scopeId);
        for (final Map<String, Object> vars : variablesForFlowNode) {
          nextStep = nextStep.withVariables(vars, scopeId);
        }
      }
    }
    return nextStep;
  }
}
