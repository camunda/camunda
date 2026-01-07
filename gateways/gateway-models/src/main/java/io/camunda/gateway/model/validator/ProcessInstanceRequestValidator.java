/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.model.validator;

import static io.camunda.gateway.model.validator.ErrorMessages.ERROR_MESSAGE_ALL_REQUIRED_FIELD;
import static io.camunda.gateway.model.validator.ErrorMessages.ERROR_MESSAGE_AT_LEAST_ONE_FIELD;
import static io.camunda.gateway.model.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.model.validator.RequestValidator.validate;
import static io.camunda.gateway.model.validator.RequestValidator.validateKeyFormat;
import static io.camunda.gateway.model.validator.RequestValidator.validateOperationReference;

import io.camunda.gateway.model.mapper.search.SearchQueryFilterMapper;
import io.camunda.gateway.protocol.model.CancelProcessInstanceRequest;
import io.camunda.gateway.protocol.model.DirectAncestorKeyInstruction;
import io.camunda.gateway.protocol.model.MigrateProcessInstanceMappingInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionById;
import io.camunda.gateway.protocol.model.ProcessInstanceCreationInstructionByKey;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationActivateInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveBatchOperationInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateByIdInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateByKeyInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationTerminateInstruction;
import io.camunda.gateway.protocol.model.SourceElementIdInstruction;
import io.camunda.gateway.protocol.model.SourceElementInstanceKeyInstruction;
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
    if (request instanceof final ProcessInstanceCreationInstructionById byId) {
      return validateById(byId);
    }
    return validateByKey((ProcessInstanceCreationInstructionByKey) request);
  }

  private static Optional<ProblemDetail> validateByKey(
      final ProcessInstanceCreationInstructionByKey request) {
    return validate(
        violations -> {
          if (request.getProcessDefinitionKey() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("processDefinitionId", "processDefinitionKey")));
          }
          validateKeyFormat(request.getProcessDefinitionKey(), "processDefinitionKey", violations);
          validateOperationReference(request.getOperationReference(), violations);
          validateTags(request.getTags(), violations);
        });
  }

  private static Optional<ProblemDetail> validateById(
      final ProcessInstanceCreationInstructionById request) {
    return validate(
        violations -> {
          if (request.getProcessDefinitionId() == null) {
            violations.add(
                ERROR_MESSAGE_AT_LEAST_ONE_FIELD.formatted(
                    List.of("processDefinitionId", "processDefinitionKey")));
          }
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
          validateMoveInstructions(request.getMoveInstructions(), violations);
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
    instructions.forEach(
        instruction ->
            validateKeyFormat(
                instruction.getAncestorElementInstanceKey(),
                "ancestorElementInstanceKey",
                violations));
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
    instructions.forEach(
        instruction -> {
          if (instruction
              instanceof final ProcessInstanceModificationTerminateByKeyInstruction byKey) {
            if (byKey.getElementInstanceKey() == null) {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementInstanceKey"));
            } else {
              validateKeyFormat(byKey.getElementInstanceKey(), "elementInstanceKey", violations);
            }
          } else {
            final String elementId =
                ((ProcessInstanceModificationTerminateByIdInstruction) instruction).getElementId();
            if (elementId == null || elementId.isBlank()) {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("elementId"));
            }
          }
        });
  }

  private static void validateMoveInstructions(
      final List<ProcessInstanceModificationMoveInstruction> instructions,
      final List<String> violations) {
    instructions.forEach(
        instruction -> {
          switch (instruction.getSourceElementInstruction()) {
            case final SourceElementIdInstruction byId -> {
              if (byId.getSourceElementId() == null || byId.getSourceElementId().isBlank()) {
                violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("sourceElementId"));
              }
            }
            case final SourceElementInstanceKeyInstruction byKey -> {
              validateKeyFormat(
                  byKey.getSourceElementInstanceKey(), "sourceElementInstanceKey", violations);
            }
            case null -> {
              violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("sourceElementInstruction"));
            }
            default -> {
              // no-op for forward compatibility
            }
          }
        });

    validateInstructions(
        instructions,
        instruction ->
            instruction.getTargetElementId() != null && !instruction.getTargetElementId().isBlank(),
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("targetElementId"));
    instructions.forEach(
        instruction -> {
          if (instruction.getAncestorScopeInstruction()
              instanceof final DirectAncestorKeyInstruction direct) {
            validateKeyFormat(
                direct.getAncestorElementInstanceKey(), "ancestorElementInstanceKey", violations);
          }
        });
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

  private static void validateMoveBatchInstructions(
      final List<ProcessInstanceModificationMoveBatchOperationInstruction> instructions,
      final List<String> violations) {
    validateInstructions(
        instructions,
        instruction ->
            instruction.getSourceElementId() != null && !instruction.getSourceElementId().isBlank(),
        violations,
        ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("sourceElementId"));
    validateInstructions(
        instructions,
        instruction ->
            instruction.getTargetElementId() != null && !instruction.getTargetElementId().isBlank(),
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
