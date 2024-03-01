/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.*;
import static java.util.function.Predicate.not;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Modify Process Instance Implementation to execute all given modifications in one Zeebe
// 'transaction'
// So for one operation we have only one 'zeebeClient.send().join()'
@Component
public class SingleStepModifyProcessInstanceHandler extends AbstractOperationHandler
    implements ModifyProcessInstanceHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(SingleStepModifyProcessInstanceHandler.class);
  @Autowired private ObjectMapper objectMapper;
  @Autowired private OperationsManager operationsManager;
  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest =
        objectMapper.readValue(
            operation.getModifyInstructions(), ModifyProcessInstanceRequestDto.class);
    final Long processInstanceKey =
        Long.parseLong(modifyProcessInstanceRequest.getProcessInstanceKey());
    final List<Modification> modifications = modifyProcessInstanceRequest.getModifications();
    // Variables first
    modifyVariables(processInstanceKey, getVariableModifications(modifications), operation);
    // Token Modifications in given order
    List<Modification> tokenModifications = getTokenModifications(modifications);

    ModifyProcessInstanceCommandStep1 currentStep =
        zeebeClient.newModifyProcessInstanceCommand(processInstanceKey);
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep = null;
    final int lastModificationIndex = tokenModifications.size() - 1;
    for (int i = 0; i < tokenModifications.size(); i++) {
      Modification modification = tokenModifications.get(i);
      if (modification.getModification().equals(ADD_TOKEN)) {
        var nextStep = addToken(currentStep, modification);
        if (i < lastModificationIndex) {
          currentStep = nextStep.and();
        } else {
          lastStep = nextStep;
        }
      } else if (modification.getModification().equals(CANCEL_TOKEN)) {
        var nextStep = cancelToken(currentStep, processInstanceKey, modification);
        if (i < lastModificationIndex) {
          currentStep = nextStep.and();
        } else {
          lastStep = nextStep;
        }
      } else if (modification.getModification().equals(MOVE_TOKEN)) {
        var nextStep = moveToken(currentStep, processInstanceKey, modification);
        if (i < lastModificationIndex) {
          currentStep = nextStep.and();
        } else {
          lastStep = nextStep;
        }
      }
      updateFinishedInBatchOperation(operation);
    }
    if (lastStep != null) {
      lastStep.send().join();
    }
    markAsSent(operation);
    completeOperation(operation, false);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.MODIFY_PROCESS_INSTANCE);
  }

  private void updateFinishedInBatchOperation(final OperationEntity operation)
      throws PersistenceException {
    operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
  }

  private void completeOperation(final OperationEntity operation, boolean updateFinishedInBatch)
      throws PersistenceException {
    operationsManager.completeOperation(operation, updateFinishedInBatch);
  }

  private void modifyVariables(
      final Long processInstanceKey,
      final List<Modification> modifications,
      OperationEntity operation)
      throws PersistenceException {
    for (Modification modification : modifications) {
      final Long scopeKey = determineScopeKey(processInstanceKey, modification);
      zeebeClient
          .newSetVariablesCommand(scopeKey)
          .variables(modification.getVariables())
          .local(true)
          .send()
          .join();
      updateFinishedInBatchOperation(operation);
    }
  }

  private Long determineScopeKey(final Long processInstanceKey, final Modification modification) {
    return modification.getScopeKey() == null ? processInstanceKey : modification.getScopeKey();
  }

  private List<Long> getNotFinishedFlowNodeInstanceKeysFor(
      final Long processInstanceKey, final String flowNodeId) {
    return flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
        processInstanceKey, flowNodeId, List.of(FlowNodeState.ACTIVE));
  }

  // Needed for tests
  public void setZeebeClient(final ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
  }

  private List<Modification> getVariableModifications(final List<Modification> modifications) {
    return modifications.stream().filter(this::isVariableModification).collect(Collectors.toList());
  }

  private List<Modification> getTokenModifications(final List<Modification> modifications) {
    return modifications.stream()
        .filter(not(this::isVariableModification))
        .collect(Collectors.toList());
  }

  private boolean isVariableModification(final Modification modification) {
    return modification.getModification().equals(ADD_VARIABLE)
        || modification.getModification().equals(EDIT_VARIABLE);
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 addToken(
      ModifyProcessInstanceCommandStep1 currentStep, final Modification modification) {
    // 0. Prepare
    final String flowNodeId = modification.getToFlowNodeId();
    final Map<String, List<Map<String, Object>>> flowNodeId2variables =
        modification.variablesForAddToken();
    logger.debug("Add token to flowNodeId {} with variables: {}", flowNodeId, flowNodeId2variables);
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
      for (String scopeId : flowNodeId2variables.keySet()) {
        final List<Map<String, Object>> variablesForFlowNode = flowNodeId2variables.get(scopeId);
        for (Map<String, Object> vars : variablesForFlowNode) {
          nextStep = nextStep.withVariables(vars, scopeId);
        }
      }
    }
    return nextStep;
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 cancelToken(
      ModifyProcessInstanceCommandStep1 currentStep,
      final Long processInstanceKey,
      final Modification modification) {
    final String flowNodeId = modification.getFromFlowNodeId();
    final String flowNodeInstanceKeyAsString = modification.getFromFlowNodeInstanceKey();
    if (StringUtils.hasText(flowNodeInstanceKeyAsString)) {
      final Long flowNodeInstanceKey = Long.parseLong(flowNodeInstanceKeyAsString);
      logger.debug("Cancel token from flowNodeInstanceKey {} ", flowNodeInstanceKey);
      return cancelFlowNodeInstances(currentStep, List.of(flowNodeInstanceKey));
    } else {
      final List<Long> flowNodeInstanceKeys =
          getNotFinishedFlowNodeInstanceKeysFor(processInstanceKey, flowNodeId);
      if (flowNodeInstanceKeys.isEmpty()) {
        throw new OperateRuntimeException(
            String.format(
                "Abort CANCEL_TOKEN: Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s",
                processInstanceKey, flowNodeId));
      }
      logger.debug("Cancel token from flowNodeInstanceKeys {} ", flowNodeInstanceKeys);
      return cancelFlowNodeInstances(currentStep, flowNodeInstanceKeys);
    }
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 moveToken(
      ModifyProcessInstanceCommandStep1 currentStep,
      final Long processInstanceKey,
      final Modification modification) {
    // 0. Prepare
    final String toFlowNodeId = modification.getToFlowNodeId();
    Integer newTokensCount = modification.getNewTokensCount();
    // Add least one token will be added
    if (newTokensCount == null || newTokensCount < 1) {
      newTokensCount = 1;
    }
    // flowNodeId => List of variables (Map)
    //  flowNodeId => [ { "key": "value" }, {"key for another flowNode with the same id": "value"} ]
    Map<String, List<Map<String, Object>>> flowNodeId2variables =
        modification.variablesForAddToken();
    if (flowNodeId2variables == null) {
      flowNodeId2variables = new HashMap<>();
    }

    // 1. Add tokens
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3 nextStep = null;
    // Create flowNodes with variables
    final List<Map<String, Object>> toFlowNodeIdVariables =
        flowNodeId2variables.getOrDefault(toFlowNodeId, List.of());
    logger.debug(
        "Move [Add token to flowNodeId: {} with variables: {} ]",
        toFlowNodeId,
        toFlowNodeIdVariables);
    for (int i = 0; i < newTokensCount; i++) {
      if (nextStep == null) {
        if (modification.getAncestorElementInstanceKey() != null) {
          nextStep =
              currentStep.activateElement(
                  toFlowNodeId, modification.getAncestorElementInstanceKey());
        } else {
          nextStep = currentStep.activateElement(toFlowNodeId);
        }
      } else {
        if (modification.getAncestorElementInstanceKey() != null) {
          nextStep
              .and()
              .activateElement(toFlowNodeId, modification.getAncestorElementInstanceKey());
        } else {
          nextStep = nextStep.and().activateElement(toFlowNodeId);
        }
      }
      if (i < toFlowNodeIdVariables.size()) {
        nextStep = nextStep.withVariables(toFlowNodeIdVariables.get(i), toFlowNodeId);
      }
    }
    for (final String flowNodeId : flowNodeId2variables.keySet()) {
      if (!flowNodeId.equals(toFlowNodeId)) {
        final List<Map<String, Object>> flowNodeVars = flowNodeId2variables.get(flowNodeId);
        for (Map<String, Object> vars : flowNodeVars) {
          nextStep.withVariables(vars, flowNodeId);
        }
      }
    }
    // 2. cancel
    final String fromFlowNodeId = modification.getFromFlowNodeId();
    final String fromFlowNodeInstanceKey = modification.getFromFlowNodeInstanceKey();
    List<Long> flowNodeInstanceKeysToCancel;
    if (StringUtils.hasText(fromFlowNodeInstanceKey)) {
      final Long flowNodeInstanceKey = Long.parseLong(fromFlowNodeInstanceKey);
      flowNodeInstanceKeysToCancel = List.of(flowNodeInstanceKey);
    } else {
      flowNodeInstanceKeysToCancel =
          getNotFinishedFlowNodeInstanceKeysFor(processInstanceKey, fromFlowNodeId);
      if (flowNodeInstanceKeysToCancel.isEmpty()) {
        throw new OperateRuntimeException(
            String.format(
                "Abort MOVE_TOKEN (CANCEL step): Can't find not finished flowNodeInstance keys for process instance %s and flowNode id %s",
                processInstanceKey, fromFlowNodeId));
      }
    }
    logger.debug(
        "Move [Cancel token from flowNodeInstanceKeys: {} ]", flowNodeInstanceKeysToCancel);
    return cancelFlowNodeInstances(nextStep.and(), flowNodeInstanceKeysToCancel);
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      cancelFlowNodeInstances(
          ModifyProcessInstanceCommandStep1 currentStep, final List<Long> flowNodeInstanceKeys) {
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
    final int size = flowNodeInstanceKeys.size();
    for (int i = 0; i < size; i++) {
      if (i < size - 1) {
        currentStep = currentStep.terminateElement(flowNodeInstanceKeys.get(i)).and();
      } else {
        nextStep = currentStep.terminateElement(flowNodeInstanceKeys.get(i));
      }
    }
    return nextStep;
  }
}
