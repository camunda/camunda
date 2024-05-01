/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.exceptions.OperateRuntimeException;
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
      if (modification.getFromFlowNodeId() != null) {
        newTokensCount =
            flowNodeInstanceReader
                .getFlowNodeInstanceKeysByIdAndStates(
                    processInstanceKey,
                    modification.getFromFlowNodeId(),
                    List.of(FlowNodeState.ACTIVE))
                .size();
      } else if (modification.getFromFlowNodeInstanceKey() != null) {
        // If a flow node instance key was specified, assume that flow node is valid. Zeebe
        // will correctly fail attempts to migrate off an invalid flow node
        newTokensCount = 1;
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
