/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.*;
import static java.util.function.Predicate.not;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.zeebe.operation.AbstractOperationHandler;
import io.camunda.operate.webapp.zeebe.operation.ModifyProcessZeebeWrapper;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// Modify Process Instance Implementation to execute all given modifications in one Zeebe
// 'transaction'
// So for one operation we have only one 'camundaClient.send().join()'
@Component
public class SingleStepModifyProcessInstanceHandler extends AbstractOperationHandler
    implements ModifyProcessInstanceHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(SingleStepModifyProcessInstanceHandler.class);

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private OperationsManager operationsManager;
  @Autowired private MoveTokenHandler moveTokenHandler;
  @Autowired private AddTokenHandler addTokenHandler;
  @Autowired private CancelTokenHandler cancelTokenHandler;
  @Autowired private ModifyProcessZeebeWrapper modifyProcessZeebeWrapper;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    // Create request from serialized instructions
    final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest =
        objectMapper.readValue(
            operation.getModifyInstructions(), ModifyProcessInstanceRequestDto.class);
    // Process variable modifications
    modifyVariables(modifyProcessInstanceRequest, operation);

    // Process token (non-variable) modifications
    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep =
        processTokenModifications(modifyProcessInstanceRequest, operation);

    modifyProcessZeebeWrapper.sendModificationsToZeebe(lastStep, operation.getId());
    markAsSent(operation);
    operationsManager.completeOperation(operation, false);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.MODIFY_PROCESS_INSTANCE);
  }

  // Needed for tests
  @Override
  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
    modifyProcessZeebeWrapper.setCamundaClient(camundaClient);
  }

  private ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2
      processTokenModifications(
          final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest,
          final OperationEntity operation)
          throws PersistenceException {
    ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 lastStep = null;

    final Long processInstanceKey =
        Long.parseLong(modifyProcessInstanceRequest.getProcessInstanceKey());
    ModifyProcessInstanceCommandStep1 currentStep =
        modifyProcessZeebeWrapper.newModifyProcessInstanceCommand(processInstanceKey);
    final List<Modification> tokenModifications =
        getTokenModifications(modifyProcessInstanceRequest.getModifications());

    for (final var iter = tokenModifications.iterator(); iter.hasNext(); ) {
      final Modification modification = iter.next();
      ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 nextStep = null;
      switch (modification.getModification()) {
        case ADD_TOKEN:
          nextStep = addTokenHandler.addToken(currentStep, modification);
          break;
        case CANCEL_TOKEN:
          nextStep = cancelTokenHandler.cancelToken(currentStep, processInstanceKey, modification);
          break;
        case MOVE_TOKEN:
          nextStep = moveTokenHandler.moveToken(currentStep, processInstanceKey, modification);
          break;
        default:
          LOGGER.warn(
              "SingleStepModifyProcessInstanceHandler encountered a modification type that should have been filtered out: {}",
              modification.getModification());
          break;
      }

      // Append 'and' if there is at least one more operation to process
      if (nextStep != null) {
        lastStep = nextStep;
        if (iter.hasNext()) {
          currentStep = nextStep.and();
        }
      }

      // Always update the finished metrics
      operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
    }

    return lastStep;
  }

  private void updateFinishedInBatchOperation(final OperationEntity operation)
      throws PersistenceException {
    operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
  }

  private void modifyVariables(
      final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest,
      final OperationEntity operation)
      throws PersistenceException {
    final Long processInstanceKey =
        Long.parseLong(modifyProcessInstanceRequest.getProcessInstanceKey());
    final List<Modification> modifications =
        getVariableModifications(modifyProcessInstanceRequest.getModifications());
    for (final Modification modification : modifications) {
      final Long scopeKey =
          modification.getScopeKey() == null ? processInstanceKey : modification.getScopeKey();
      modifyProcessZeebeWrapper.setVariablesInZeebe(
          scopeKey, modification.getVariables(), operation.getId());
      updateFinishedInBatchOperation(operation);
    }
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
}
