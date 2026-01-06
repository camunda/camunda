/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MoveTokenHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(MoveTokenHandler.class);
  private final FlowNodeInstanceReader flowNodeInstanceReader;

  public MoveTokenHandler(final FlowNodeInstanceReader flowNodeInstanceReader) {
    this.flowNodeInstanceReader = flowNodeInstanceReader;
  }

  public ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 moveToken(
      final ModifyProcessInstanceCommandStep1 currentStep,
      final Long processInstanceKey,
      final Modification modification) {

    final int newTokensCount = calculateNewTokensCount(modification, processInstanceKey);

    if (newTokensCount > 0) {
      final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep =
          activateNewNodes(currentStep, modification, newTokensCount);

      moveGlobalVariables(nextStep, modification);

      return cancelTokensOnOriginalNodes(nextStep, processInstanceKey, modification);
    } else {
      LOGGER.info(
          "Skipping MOVE_TOKEN processing for flowNode {} and process instance {} since newTokensCount is {}",
          modification.getFromFlowNodeId(),
          processInstanceKey,
          newTokensCount);
      return null;
    }
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 activateNewNodes(
      final ModifyProcessInstanceCommandStep1 currentStep,
      final Modification modification,
      final int newTokensCount) {
    final String toFlowNodeId = modification.getToFlowNodeId();
    // flowNodeId => List of variables (Map)
    //  flowNodeId => [ { "key": "value" }, {"key for another flowNode with the same id": "value"} ]
    final Map<String, List<Map<String, Object>>> variablesToMoveByFlownodeId =
        modification.variablesForAddToken() == null
            ? new HashMap<>()
            : modification.variablesForAddToken();

    // Create flowNodes with variables
    final Queue<Map<String, Object>> activatedNodeVariables =
        new LinkedList<>(variablesToMoveByFlownodeId.getOrDefault(toFlowNodeId, List.of()));

    LOGGER.debug(
        "Move [Add token to flowNodeId: {} with variables: {} ]",
        toFlowNodeId,
        activatedNodeVariables);

    if (newTokensCount < activatedNodeVariables.size()) {
      LOGGER.warn(
          "There are {} variables to move but only {} elements to activate, some variables might be lost",
          activatedNodeVariables.size(),
          newTokensCount);
    }

    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep = null;
    // Activate new nodes and move variables
    for (int i = 0; i < newTokensCount; i++) {
      if (nextStep != null) {
        nextStep.and();
      }
      if (modification.getAncestorElementInstanceKey() != null) {
        nextStep =
            currentStep.activateElement(toFlowNodeId, modification.getAncestorElementInstanceKey());
      } else {
        nextStep = currentStep.activateElement(toFlowNodeId);
      }
      if (activatedNodeVariables.peek() != null) {
        nextStep = nextStep.withVariables(activatedNodeVariables.poll(), toFlowNodeId);
      }
    }

    return nextStep;
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 moveGlobalVariables(
      final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep,
      final Modification modification) {
    final String toFlowNodeId = modification.getToFlowNodeId();

    final Map<String, List<Map<String, Object>>> variablesToMoveByFlownodeId =
        modification.variablesForAddToken() == null
            ? new HashMap<>()
            : modification.variablesForAddToken();

    // Move remaining variables within process instance
    for (final String flowNodeId : variablesToMoveByFlownodeId.keySet()) {
      if (!flowNodeId.equals(toFlowNodeId)) {
        final List<Map<String, Object>> flowNodeVars = variablesToMoveByFlownodeId.get(flowNodeId);
        for (final Map<String, Object> vars : flowNodeVars) {
          nextStep.withVariables(vars, flowNodeId);
        }
      }
    }

    return nextStep;
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      cancelTokensOnOriginalNodes(
          final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep,
          final Long processInstanceKey,
          final Modification modification) {
    final String fromFlowNodeId = modification.getFromFlowNodeId();
    final String fromFlowNodeInstanceKey = modification.getFromFlowNodeInstanceKey();
    final List<Long> flowNodeInstanceKeysToCancel;

    // Build the list of instances to cancel
    if (StringUtils.hasText(fromFlowNodeInstanceKey)) {
      final Long flowNodeInstanceKey = Long.parseLong(fromFlowNodeInstanceKey);
      flowNodeInstanceKeysToCancel = List.of(flowNodeInstanceKey);
    } else {
      flowNodeInstanceKeysToCancel =
          flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
              processInstanceKey, fromFlowNodeId, List.of(FlowNodeState.ACTIVE));
    }

    if (flowNodeInstanceKeysToCancel.isEmpty()) {
      throw new OperateRuntimeException(
          String.format(
              "Abort MOVE_TOKEN (CANCEL step): Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s",
              processInstanceKey, fromFlowNodeId));
    }

    LOGGER.debug(
        "Move [Cancel token from flowNodeInstanceKeys: {} ]", flowNodeInstanceKeysToCancel);
    for (final Long flowNodeInstanceKey : flowNodeInstanceKeysToCancel) {
      nextStep.and().terminateElement(flowNodeInstanceKey);
    }

    return nextStep;
  }

  private int calculateNewTokensCount(
      final Modification modification, final Long processInstanceKey) {
    Integer newTokensCount = modification.getNewTokensCount();

    if (newTokensCount == null) {
      if (modification.getFromFlowNodeInstanceKey() != null) {
        // If a flow node instance key was specified, assume that flow node is valid. Zeebe
        // will correctly fail attempts to migrate off an invalid flow node
        newTokensCount = 1;
      } else if (modification.getFromFlowNodeId() != null) {
        newTokensCount =
            flowNodeInstanceReader
                .getFlowNodeInstanceKeysByIdAndStates(
                    processInstanceKey,
                    modification.getFromFlowNodeId(),
                    List.of(FlowNodeState.ACTIVE))
                .size();
      } else {
        LOGGER.warn(
            "MOVE_TOKEN attempted with no flowNodeId, flowNodeInstanceKey, or newTokenCount specified");
        newTokensCount = 0;
      }
    }

    LOGGER.info("MOVE_TOKEN has a newTokensCount value of {}", newTokensCount);
    return newTokensCount;
  }
}
