/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
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
  private final OperateProperties operateProperties;

  public MoveTokenHandler(
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final OperateProperties operateProperties) {
    this.flowNodeInstanceReader = flowNodeInstanceReader;
    this.operateProperties = operateProperties;
  }

  public ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 moveToken(
      final ModifyProcessInstanceCommandStep1 currentStep,
      final Long processInstanceKey,
      final Modification modification) {

    final Integer specificTokensCount = modification.getNewTokensCount();
    if (specificTokensCount != null && specificTokensCount <= 0) {
      LOGGER.debug(
          "Skipping MOVE_TOKEN processing for flowNode {} and process instance {} since newTokensCount is {}",
          modification.getFromFlowNodeId(),
          processInstanceKey,
          specificTokensCount);
      return null;
    }

    final List<Long> flowNodeInstanceKeysToCancel =
        resolveFlowNodeInstanceKeysToCancel(processInstanceKey, modification);

    final int newTokensCount = resolveNewTokensCount(modification, flowNodeInstanceKeysToCancel);

    if (newTokensCount > 0) {
      // The number of tokens activated must equal the number cancelled so that the process
      // instance token count stays consistent when the max modification limit kicks in.
      final List<Long> keysToCancel =
          flowNodeInstanceKeysToCancel.subList(
              0, Math.min(newTokensCount, flowNodeInstanceKeysToCancel.size()));

      if (keysToCancel.isEmpty()) {
        throw new OperateRuntimeException(
            String.format(
                "Abort MOVE_TOKEN (CANCEL step): Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s",
                processInstanceKey, modification.getFromFlowNodeId()));
      }

      final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep =
          activateNewNodes(currentStep, modification, newTokensCount);

      moveGlobalVariables(nextStep, modification);

      return cancelTokensOnOriginalNodes(nextStep, keysToCancel);
    } else {
      LOGGER.debug(
          "Skipping MOVE_TOKEN processing for flowNode {} and process instance {} since newTokensCount is {}",
          modification.getFromFlowNodeId(),
          processInstanceKey,
          newTokensCount);
      return null;
    }
  }

  private List<Long> resolveFlowNodeInstanceKeysToCancel(
      final Long processInstanceKey, final Modification modification) {
    if (StringUtils.hasText(modification.getFromFlowNodeInstanceKey())) {
      return List.of(Long.parseLong(modification.getFromFlowNodeInstanceKey()));
    } else if (StringUtils.hasText(modification.getFromFlowNodeId())) {
      final int limit = operateProperties.getOperationExecutor().getMaxModifyTokensLimit();
      final List<Long> keys =
          flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
              processInstanceKey, modification.getFromFlowNodeId(), List.of(FlowNodeState.ACTIVE));
      if (keys.size() > limit) {
        LOGGER.warn(
            "Found {} active instances for flow node '{}' in process instance {}, "
                + "limiting to {} due to maxModifyTokensLimit",
            keys.size(),
            modification.getFromFlowNodeId(),
            processInstanceKey,
            limit);
        return keys.subList(0, limit);
      }
      return keys;
    } else {
      return List.of();
    }
  }

  private int resolveNewTokensCount(
      final Modification modification, final List<Long> resolvedCancelKeys) {
    Integer newTokensCount = modification.getNewTokensCount();
    if (newTokensCount == null) {
      if (StringUtils.hasText(modification.getFromFlowNodeInstanceKey())) {
        newTokensCount = 1;
      } else if (StringUtils.hasText(modification.getFromFlowNodeId())) {
        newTokensCount = resolvedCancelKeys.size();
      } else {
        LOGGER.warn(
            "MOVE_TOKEN attempted with no flowNodeId, flowNodeInstanceKey, or newTokenCount specified");
        newTokensCount = 0;
      }
    } else {
      final int limit = operateProperties.getOperationExecutor().getMaxModifyTokensLimit();
      if (newTokensCount > limit) {
        LOGGER.warn(
            "newTokensCount {} exceeds maxModifyTokensLimit {}, capping to limit",
            newTokensCount,
            limit);
        newTokensCount = limit;
      }
    }
    LOGGER.debug("MOVE_TOKEN has a newTokensCount value of {}", newTokensCount);
    return newTokensCount;
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
          final List<Long> flowNodeInstanceKeysToCancel) {
    LOGGER.debug(
        "Move [Cancel token from flowNodeInstanceKeys: {} ]", flowNodeInstanceKeysToCancel);
    for (final Long flowNodeInstanceKey : flowNodeInstanceKeysToCancel) {
      nextStep.and().terminateElement(flowNodeInstanceKey);
    }
    return nextStep;
  }
}
