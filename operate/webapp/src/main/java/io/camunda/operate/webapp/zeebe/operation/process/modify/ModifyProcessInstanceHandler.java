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
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.zeebe.operation.AbstractOperationHandler;
import io.camunda.operate.webapp.zeebe.operation.OperationHandler;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// Modify Process Instance Implementation to execute all given modifications in one Zeebe
// 'transaction'
// So for one operation we have only one 'camundaClient.send().join()'
@Component
@ConditionalOnRdbmsDisabled
public class ModifyProcessInstanceHandler extends AbstractOperationHandler
    implements OperationHandler {

  @Autowired
  @Qualifier("operateObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private OperationsManager operationsManager;
  @Autowired private OperateServicesAdapter operateServicesAdapter;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    // Create request from serialized instructions
    final ModifyProcessInstanceRequestDto modifyProcessInstanceRequest =
        objectMapper.readValue(
            operation.getModifyInstructions(), ModifyProcessInstanceRequestDto.class);
    final Long processInstanceKey =
        Long.parseLong(modifyProcessInstanceRequest.getProcessInstanceKey());
    final List<Modification> modifications = modifyProcessInstanceRequest.getModifications();

    // Process variable modifications
    modifyVariables(processInstanceKey, getVariableModifications(modifications), operation);

    // Process token (non-variable) modifications
    operateServicesAdapter.modifyProcessInstance(
        processInstanceKey, getTokenModifications(modifications), operation.getId());
    updateFinishedInBatchOperation(operation);
    markAsSent(operation);
    operationsManager.completeOperation(operation, false);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(OperationType.MODIFY_PROCESS_INSTANCE);
  }

  private void updateFinishedInBatchOperation(final OperationEntity operation)
      throws PersistenceException {
    operationsManager.updateFinishedInBatchOperation(operation.getBatchOperationId());
  }

  private void modifyVariables(
      final Long processInstanceKey,
      final List<Modification> modifications,
      final OperationEntity operation)
      throws PersistenceException {
    for (final Modification modification : modifications) {
      final Long scopeKey =
          modification.getScopeKey() == null ? processInstanceKey : modification.getScopeKey();
      operateServicesAdapter.setVariables(
          scopeKey, modification.getVariables(), true, operation.getId());
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
