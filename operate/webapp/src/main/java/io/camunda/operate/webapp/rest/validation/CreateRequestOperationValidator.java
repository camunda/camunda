/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.validation;

import static io.camunda.webapps.schema.entities.operation.OperationType.ADD_VARIABLE;
import static io.camunda.webapps.schema.entities.operation.OperationType.UPDATE_VARIABLE;

import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.VariableReader;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRdbmsDisabled
public class CreateRequestOperationValidator {

  private static final Set<OperationType> VARIABLE_OPERATIONS =
      EnumSet.of(UPDATE_VARIABLE, ADD_VARIABLE);

  private final VariableReader variableReader;
  private final OperationReader operationReader;

  public CreateRequestOperationValidator(
      final VariableReader variableReader, final OperationReader operationReader) {
    this.variableReader = variableReader;
    this.operationReader = operationReader;
  }

  public void validate(final CreateOperationRequestDto request, final String processInstanceId) {
    if (request.getOperationType() == null) {
      throw new InvalidRequestException(
          String.format(
              "Operation type must be defined for operation on processInstanceId=%s.",
              processInstanceId));
    }

    if (isVariableOperation(request) && isRequiredVariableOperationFieldMissing(request)) {
      throw new InvalidRequestException(
          String.format(
              "ScopeId, variable name, and variable value must be defined for %s operation on processInstanceId=%s.",
              request.getOperationType(), processInstanceId));
    }

    if (request.getOperationType().equals(ADD_VARIABLE)) {
      if (variableAlreadyExists(request, processInstanceId)) {
        throw new InvalidRequestException(
            String.format(
                "Cannot add variable \"%s\" in scope \"%s\" of processInstanceId=%s: "
                    + "a variable with this name already exists.",
                request.getVariableName(), request.getVariableScopeId(), processInstanceId));
      }

      if (hasNonFailedAddVariableOperation(request, processInstanceId)) {
        throw new InvalidRequestException(
            String.format(
                "Cannot add variable \"%s\" in scope \"%s\" of processInstanceId=%s: "
                    + "an ADD_VARIABLE operation for this variable already exists.",
                request.getVariableName(), request.getVariableScopeId(), processInstanceId));
      }
    }
  }

  private static boolean isVariableOperation(final CreateOperationRequestDto request) {
    return VARIABLE_OPERATIONS.contains(request.getOperationType());
  }

  private static boolean isRequiredVariableOperationFieldMissing(
      final CreateOperationRequestDto request) {
    return request.getVariableScopeId() == null
        || request.getVariableName() == null
        || request.getVariableName().isEmpty()
        || request.getVariableValue() == null;
  }

  private boolean variableAlreadyExists(
      final CreateOperationRequestDto request, final String processInstanceId) {
    final var variable =
        variableReader.getVariableByName(
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
