/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation;

import static io.camunda.webapps.schema.entities.operation.OperationType.ADD_VARIABLE;
import static io.camunda.webapps.schema.entities.operation.OperationType.UPDATE_VARIABLE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Update the variable. */
@Component
@ConditionalOnRdbmsDisabled
public class UpdateVariableHandler extends AbstractOperationHandler implements OperationHandler {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    if (!scopeBelongsToProcessInstance(
        operation.getScopeKey(), operation.getProcessInstanceKey())) {
      failOperation(
          operation,
          String.format(
              "Scope %s does not belong to process instance %s.",
              operation.getScopeKey(), operation.getProcessInstanceKey()));
      return;
    }
    final Map<String, Object> updateVariableMap =
        getVariableMap(operation.getVariableName(), operation.getVariableValue());
    final var key =
        operationServicesAdapter.setVariables(
            operation.getScopeKey(), updateVariableMap, true, operation.getId());
    markAsSent(operation, key);
  }

  private boolean scopeBelongsToProcessInstance(
      final Long scopeKey, final Long processInstanceKey) {
    if (scopeKey.equals(processInstanceKey)) {
      return true;
    }
    return flowNodeInstanceReader.getAllFlowNodeInstances(processInstanceKey).stream()
        .map(FlowNodeInstanceEntity::getKey)
        .anyMatch(scopeKey::equals);
  }

  @Override
  public Set<OperationType> getTypes() {
    return Set.of(UPDATE_VARIABLE, ADD_VARIABLE);
  }

  private Map<String, Object> getVariableMap(final String variableName, final String variableValue)
      throws JsonProcessingException {
    final var variableJson = String.format("{\"%s\":%s}", variableName, variableValue);
    return objectMapper.readValue(variableJson, Map.class);
  }
}
