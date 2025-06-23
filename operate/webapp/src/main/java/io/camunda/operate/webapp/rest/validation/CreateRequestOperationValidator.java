/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static io.camunda.operate.entities.OperationType.ADD_VARIABLE;
import static io.camunda.operate.entities.OperationType.UPDATE_VARIABLE;

import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.webapps.schema.entities.operation.OperationState;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CreateRequestOperationValidator {
  private final VariableReader variableReader;
  private final OperationReader operationReader;

  public CreateRequestOperationValidator(
      final VariableReader variableReader, final OperationReader operationReader) {
    this.variableReader = variableReader;
    this.operationReader = operationReader;
  }

  public void validate(
      final CreateOperationRequestDto operationRequest, final String processInstanceId) {
    if (operationRequest.getOperationType() == null) {
      throw new InvalidRequestException("Operation type must be defined.");
    }

    if (Set.of(UPDATE_VARIABLE, ADD_VARIABLE).contains(operationRequest.getOperationType())
        && (operationRequest.getVariableScopeId() == null
            || operationRequest.getVariableName() == null
            || operationRequest.getVariableName().isEmpty()
            || operationRequest.getVariableValue() == null)) {
      throw new InvalidRequestException(
          "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
    }

    if (operationRequest.getOperationType().equals(ADD_VARIABLE)) {
      if (variableAlreadyExists(operationRequest, processInstanceId)
          || hasNonFailedAddVariableOperation(operationRequest, processInstanceId)) {
        throw new InvalidRequestException(
            String.format(
                "Variable with the name \"%s\" already exists.",
                operationRequest.getVariableName()));
      }
    }
  }

  private boolean variableAlreadyExists(
      final CreateOperationRequestDto request, final String processInstanceId) {
    final var variable = variableReader.getVariableByName(
        processInstanceId, request.getVariableScopeId(), request.getVariableName());
    return variable != null;
  }

  private boolean hasNonFailedAddVariableOperation(
      final CreateOperationRequestDto request, final String processInstanceId) {
    return operationReader
        .getOperations(
            ADD_VARIABLE,
            processInstanceId,
            request.getVariableScopeId(),
            request.getVariableName())
        .stream()
        .anyMatch(op -> op.getState() != OperationState.FAILED);
  }
}
