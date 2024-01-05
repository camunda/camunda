/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.auth.impl.TenantAuthorizationCheckerImpl;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue.ProcessInstanceMigrationMappingInstructionValue;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public final class ProcessInstanceMigrationPreconditionChecker {

  private static final String ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND =
      "Expected to migrate process instance but no process instance found with key '%d'";
  private static final String ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND =
      "Expected to migrate process instance to process definition but no process definition found with key '%d'";
  private static final String ERROR_MESSAGE_DUPLICATE_SOURCE_ELEMENT_IDS =
      "Expected to migrate process instance '%s' but the mapping instructions contain duplicate source element ids '%s'.";
  private static final String ERROR_CHILD_PROCESS_INSTANCE =
      "Expected to migrate process instance '%s' but process instance is a child process instance. Child process instances cannot be migrated.";
  private static final String ERROR_SOURCE_ELEMENT_ID_NOT_FOUND =
      """
              Expected to migrate process instance '%s' \
              but mapping instructions contain a non-existing source element id '%s'. \
              Elements provided in mapping instructions must exist \
              in the source process definition.""";
  private static final String ERROR_TARGET_ELEMENT_ID_NOT_FOUND =
      """
              Expected to migrate process instance '%s' \
              but mapping instructions contain a non-existing target element id '%s'. \
              Elements provided in mapping instructions must exist \
              in the target process definition.""";
  private static final String ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_PROCESS_INSTANCE =
      "Expected to migrate process instance but process instance has an event subprocess. Process instances with event subprocesses cannot be migrated yet.";
  private static final String ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_TARGET_PROCESS =
      "Expected to migrate process instance but target process has an event subprocess. Target processes with event subprocesses cannot be migrated yet.";
  private static final long NO_PARENT = -1L;

  public static void requireNonNullProcessInstance(
      final ElementInstance record, final long processInstanceKey) {
    if (record == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, processInstanceKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.NOT_FOUND);
    }
  }

  public static void requireAuthorizedTenant(
      final Map<String, Object> authorizations,
      final String tenantId,
      final long processInstanceKey) {
    final boolean isTenantAuthorized =
        TenantAuthorizationCheckerImpl.fromAuthorizationMap(authorizations).isAuthorized(tenantId);
    if (!isTenantAuthorized) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_INSTANCE_NOT_FOUND, processInstanceKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.NOT_FOUND);
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in case any of the
   * process instance is a child process instance.
   */
  public static void requireNullParent(
      final long parentProcessInstanceKey, final long processInstanceKey) {
    if (parentProcessInstanceKey != NO_PARENT) {
      final String reason = String.format(ERROR_CHILD_PROCESS_INSTANCE, processInstanceKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_STATE);
    }
  }

  public static void requireNonNullTargetProcessDefinition(
      final DeployedProcess targetProcessDefinition, final long targetProcessDefinitionKey) {
    if (targetProcessDefinition == null) {
      final String reason =
          String.format(ERROR_MESSAGE_PROCESS_DEFINITION_NOT_FOUND, targetProcessDefinitionKey);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.NOT_FOUND);
    }
  }

  public static void requireNonDuplicateSourceElementIds(
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions,
      final long processInstanceKey) {
    final Map<String, Long> countBySourceElementId =
        mappingInstructions.stream()
            .collect(
                Collectors.groupingBy(
                    ProcessInstanceMigrationMappingInstructionValue::getSourceElementId,
                    Collectors.counting()));
    final List<String> duplicateSourceElementIds =
        countBySourceElementId.entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .map(Entry::getKey)
            .toList();

    if (!duplicateSourceElementIds.isEmpty()) {
      final String reason =
          String.format(
              ERROR_MESSAGE_DUPLICATE_SOURCE_ELEMENT_IDS,
              processInstanceKey,
              duplicateSourceElementIds);
      throw new ProcessInstanceMigrationPreconditionFailedException(
          reason, RejectionType.INVALID_ARGUMENT);
    }
  }

  /**
   * Exception that can be thrown during the migration of a process instance, in following cases:
   *
   * <p>
   *
   * <ul>
   *   <li>A mapping instruction contains a source element id that does not exist in the source
   *       process definition.
   *   <li>A mapping instruction contains a target element id that does not exist in the target
   *       process definition.
   * </ul>
   *
   * <p>
   */
  public static void requireReferredElementsExist(
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition,
      final List<ProcessInstanceMigrationMappingInstructionValue> mappingInstructions,
      final long processInstanceKey) {

    mappingInstructions.forEach(
        instruction -> {
          final String sourceElementId = instruction.getSourceElementId();
          if (sourceProcessDefinition.getProcess().getElementById(sourceElementId) == null) {
            final String reason =
                String.format(
                    ERROR_SOURCE_ELEMENT_ID_NOT_FOUND, processInstanceKey, sourceElementId);
            throw new ProcessInstanceMigrationPreconditionFailedException(
                reason, RejectionType.INVALID_ARGUMENT);
          }

          final String targetElementId = instruction.getTargetElementId();
          if (targetProcessDefinition.getProcess().getElementById(targetElementId) == null) {
            final String reason =
                String.format(
                    ERROR_TARGET_ELEMENT_ID_NOT_FOUND, processInstanceKey, targetElementId);
            throw new ProcessInstanceMigrationPreconditionFailedException(
                reason, RejectionType.INVALID_ARGUMENT);
          }
        });
  }

  public static void requireNoEventSubprocess(
      final DeployedProcess sourceProcessDefinition,
      final DeployedProcess targetProcessDefinition) {
    if (!sourceProcessDefinition.getProcess().getEventSubprocesses().isEmpty()) {
      throw new ProcessInstanceMigrationPreconditionFailedException(
          ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_PROCESS_INSTANCE,
          RejectionType.INVALID_STATE);
    }

    if (!targetProcessDefinition.getProcess().getEventSubprocesses().isEmpty()) {
      throw new ProcessInstanceMigrationPreconditionFailedException(
          ERROR_MESSAGE_EVENT_SUBPROCESS_NOT_SUPPORTED_IN_TARGET_PROCESS,
          RejectionType.INVALID_STATE);
    }
  }

  public static final class ProcessInstanceMigrationPreconditionFailedException
      extends RuntimeException {
    private final RejectionType rejectionType;

    public ProcessInstanceMigrationPreconditionFailedException(
        final String message, final RejectionType rejectionType) {
      super(message);
      this.rejectionType = rejectionType;
    }

    public RejectionType getRejectionType() {
      return rejectionType;
    }
  }
}
