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
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateKeyFormat;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validateOperationReference;

import io.camunda.zeebe.gateway.protocol.rest.CancelProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceMappingInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceCreationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationMoveBatchOperationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryFilterMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.http.ProblemDetail;

public class ProcessInstanceRequestValidator {

  public static final ProcessInstanceMigrationBatchOperationPlan EMPTY_MIGRATION_PLAN =
      new ProcessInstanceMigrationBatchOperationPlan();

  public static Optional<ProblemDetail> validateCreateProcessInstanceRequest(
      final ProcessInstanceCreationInstruction request) {
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
          // Validate processDefinitionKey format if provided
          validateKeyFormat(request.getProcessDefinitionKey(), "processDefinitionKey", violations);
          validateOperationReference(request.getOperationReference(), violations);
          validateTags(request.getTags(), violations);
        });
  }

  private static void validateTags(final Set<String> tags, final List<String> violations) {
    violations.addAll(TagsValidator.validate(tags));
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

  public static Optional<ProblemDetail> validateMigrateProcessInstanceBatchOperationRequest(
      final ProcessInstanceMigrationBatchOperationRequest request) {
    return validate(
        violations -> {
          final var filter =
              SearchQueryFilterMapper.toRequiredProcessInstanceFilter(request.getFilter());
          filter.ifLeft(violations::addAll);

          final var migrationPlan = request.getMigrationPlan();
          if (migrationPlan == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("migrationPlan"));
            return;
          }

          if (migrationPlan.getTargetProcessDefinitionKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetProcessDefinitionKey"));
          } else {
            // Validate targetProcessDefinitionKey format
            validateKeyFormat(
                migrationPlan.getTargetProcessDefinitionKey(),
                "targetProcessDefinitionKey",
                violations);
          }

          if (migrationPlan.getMappingInstructions() == null
              || migrationPlan.getMappingInstructions().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("mappingInstructions"));
          } else {
            validateMappingInstructions(migrationPlan.getMappingInstructions(), violations);
          }
        });
  }

  public static Optional<ProblemDetail> validateMigrateProcessInstanceRequest(
      final ProcessInstanceMigrationInstruction request) {
    return validate(
        violations -> {
          if (request.getTargetProcessDefinitionKey() == null) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetProcessDefinitionKey"));
          } else {
            // Validate targetProcessDefinitionKey format
            validateKeyFormat(
                request.getTargetProcessDefinitionKey(), "targetProcessDefinitionKey", violations);
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
      final ProcessInstanceModificationInstruction request) {
    return validate(
        violations -> {
          validateActivateInstructions(request.getActivateInstructions(), violations);
          validateTerminateInstructions(request.getTerminateInstructions(), violations);
          validateOperationReference(request.getOperationReference(), violations);
        });
  }

  public static Optional<ProblemDetail> validateModifyProcessInstanceBatchOperationRequest(
      final ProcessInstanceModificationBatchOperationRequest request) {
    return validate(
        violations -> {
          final var filter =
              SearchQueryFilterMapper.toRequiredProcessInstanceFilter(request.getFilter());
          filter.ifLeft(violations::addAll);
          if (request.getMoveInstructions() == null || request.getMoveInstructions().isEmpty()) {
            violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("moveInstructions"));
          } else {
            validateMoveBatchInstructions(request.getMoveInstructions(), violations);
          }
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
      final List<ProcessInstanceModificationActivateInstruction> instructions,
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
      final List<ProcessInstanceModificationTerminateInstruction> instructions,
      final List<String> violations) {
    validateInstructions(
        instructions,
        (instruction) -> instruction.getElementInstanceKey() != null,
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementInstanceKey"));
    // Also validate the format of elementInstanceKey values
    instructions.stream()
        .filter(instruction -> instruction.getElementInstanceKey() != null)
        .forEach(
            instruction ->
                validateKeyFormat(
                    instruction.getElementInstanceKey(), "elementInstanceKey", violations));
  }

  private static void validateMoveBatchInstructions(
      final List<ProcessInstanceModificationMoveBatchOperationInstruction> instructions,
      final List<String> violations) {
    validateInstructions(
        instructions,
        (instruction) ->
            instruction.getSourceElementId() != null && !instruction.getSourceElementId().isEmpty(),
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("sourceElementId"));
    validateInstructions(
        instructions,
        (instruction) ->
            instruction.getTargetElementId() != null && !instruction.getTargetElementId().isEmpty(),
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetElementId"));
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
