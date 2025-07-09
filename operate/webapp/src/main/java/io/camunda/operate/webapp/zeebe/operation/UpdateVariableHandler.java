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
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Update the variable. */
@Component
public class UpdateVariableHandler extends AbstractOperationHandler implements OperationHandler {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void handleWithException(final OperationEntity operation) throws Exception {
    final Map<String, Object> updateVariableMap =
        getVariableMap(operation.getVariableName(), operation.getVariableValue());
    final var key =
        operationServicesAdapter.setVariables(
            operation.getScopeKey(), updateVariableMap, true, operation.getId());
    markAsSent(operation, key);
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
