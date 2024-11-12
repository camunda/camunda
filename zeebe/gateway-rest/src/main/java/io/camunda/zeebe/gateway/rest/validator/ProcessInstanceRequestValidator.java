/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ALL_REQUIRED_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_ONLY_ONE_FIELD;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateOperationReference;

import io.camunda.zeebe.gateway.protocol.rest.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceMappingInstruction;
import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.ModifyProcessInstanceActivateInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.ModifyProcessInstanceTerminateInstruction;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.http.ProblemDetail;

public class ProcessInstanceRequestValidator {

  public static Optional<ProblemDetail> validateCreateProcessInstanceRequest(
      final CreateProcessInstanceRequest request) {
    return validate(
        violations -> {
          if (request.getProcessDefinitionId() == null
              && request.getProcessDefinitionKey() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("processDefinitionId", "processDefinitionKey")));
          }
          if (request.getProcessDefinitionId() != null
              && request.getProcessDefinitionKey() != null) {
            violations.add(
                ERROR_MESSAGE_ONLY_ONE_FIELD.formatted(
                    List.of("processDefinitionId", "processDefinitionKey")));
          }
          validateOperationReference(request.getOperationReference(), violations);
        });
  }

  public static Optional<ProblemDetail> validateCancelProcessInstanceRequest(
      final CancelProcessInstanceRequest request) {
    return validate(
        violations -> {
          if (request != null) {
            validateOperationReference(request.getOperationReference(), violations);
          }
        });
  }

  public static Optional<ProblemDetail> validateMigrateProcessInstanceRequest(
      final MigrateProcessInstanceRequest request) {
    return validate(
        violations -> {
          if (request.getTargetProcessDefinitionKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetProcessDefinitionKey"));
          }
          if (request.getMappingInstructions() == null
              || request.getMappingInstructions().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
          } else {
            validateMappingInstructions(request.getMappingInstructions(), violations);
          }
          validateOperationReference(request.getOperationReference(), violations);
        });
  }

  public static Optional<ProblemDetail> validateModifyProcessInstanceRequest(
      final ModifyProcessInstanceRequest request) {
    return validate(
        violations -> {
          validateActivateInstructions(request.getActivateInstructions(), violations);
          validateTerminateInstructions(request.getTerminateInstructions(), violations);
          validateOperationReference(request.getOperationReference(), violations);
        });
  }

  private static void validateMappingInstructions(
      final List<MigrateProcessInstanceMappingInstruction> mappingInstructions,
      final List<String> violations) {
    validateInstructions(
        mappingInstructions,
        (instruction) ->
            (instruction.getSourceElementId() != null
                    && !instruction.getSourceElementId().isEmpty())
                && (instruction.getTargetElementId() != null
                    && !instruction.getTargetElementId().isEmpty()),
        violations,
        ERROR_MESSAGE_ALL_REQUIRED_FIELD.formatted(List.of("sourceElementId", "targetElementId")));
  }

  private static void validateActivateInstructions(
      final List<ModifyProcessInstanceActivateInstruction> instructions,
      final List<String> violations) {
    validateInstructions(
        instructions,
        (instruction) -> instruction.getElementId() != null,
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementId"));
    final var variableInstructions =
        instructions.stream()
            .flatMap(instruction -> instruction.getVariableInstructions().stream())
            .toList();
    validateInstructions(
        variableInstructions,
        (variableInstruction) -> !variableInstruction.getVariables().isEmpty(),
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("variables"));
  }

  private static void validateTerminateInstructions(
      final List<ModifyProcessInstanceTerminateInstruction> instructions,
      final List<String> violations) {
    validateInstructions(
        instructions,
        (instruction) -> instruction.getElementInstanceKey() != null,
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementInstanceKey"));
  }

  private static <T> void validateInstructions(
      final List<T> instructions,
      final Predicate<T> match,
      final List<String> violations,
      final String message) {
    final boolean areInstructionsValid = instructions.stream().allMatch(match);
    if (!areInstructionsValid) {
      violations.add(message);
    }
  }
}
