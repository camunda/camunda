/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantAddEntityProcessor implements DistributedTypedRecordProcessor<TenantRecord> {
  private static final String ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE =
      "Expected to add entity with key '%s' to tenant with key '%s', but the entity is already assigned to this tenant.";
  private final TenantState tenantState;
  private final UserState userState;
  private final MappingState mappingState;
  private final GroupState groupState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantAddEntityProcessor(
      final ProcessingState state,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    tenantState = state.getTenantState();
    userState = state.getUserState();
    mappingState = state.getMappingState();
    groupState = state.getGroupState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();

    final var tenantKey = record.getTenantKey();
    final var persistedTenant = tenantState.getTenantByKey(tenantKey);
    if (persistedTenant.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to add entity to tenant with key '%s', but no tenant with this key exists."
              .formatted(tenantKey));
      return;
    }
    final var tenantId = persistedTenant.get().getTenantId();
    record.setTenantId(tenantId);

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.UPDATE)
            .addResourceId(tenantId);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      rejectCommandWithUnauthorizedError(command, isAuthorized.getLeft());
      return;
    }

    final var entityKey = record.getEntityKey();
    if (!isEntityPresentAndNotAssigned(entityKey, record.getEntityType(), command, tenantId)) {
      return;
    }

    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.ENTITY_ADDED, record);
    responseWriter.writeEventOnCommand(tenantKey, TenantIntent.ENTITY_ADDED, record, command);

    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();
    tenantState
        .getEntityType(record.getTenantKey(), record.getEntityKey())
        .ifPresentOrElse(
            entityType ->
                rejectionWriter.appendRejection(
                    command,
                    RejectionType.ALREADY_EXISTS,
                    ENTITY_ALREADY_ASSIGNED_ERROR_MESSAGE.formatted(
                        record.getEntityKey(), record.getTenantKey())),
            () ->
                stateWriter.appendFollowUpEvent(
                    command.getKey(), TenantIntent.ENTITY_ADDED, record));

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isEntityPresentAndNotAssigned(
      final long entityKey,
      final EntityType entityType,
      final TypedRecord<TenantRecord> command,
      final String tenantId) {
    return switch (entityType) {
      case USER -> checkUserAssignment(entityKey, command, tenantId);
      case MAPPING -> checkMappingAssignment(entityKey, command, tenantId);
      case GROUP -> checkGroupAssignment(entityKey, command, tenantId);
      default ->
          throw new IllegalStateException(formatErrorMessage(entityKey, tenantId, "doesn't exist"));
    };
  }

  private boolean checkUserAssignment(
      final long entityKey, final TypedRecord<TenantRecord> command, final String tenantId) {
    final var user = userState.getUser(entityKey);
    if (user.isEmpty()) {
      createEntityNotExistRejectCommand(command, entityKey, tenantId);
      return false;
    }
    if (user.get().getTenantIdsList().contains(tenantId)) {
      createAlreadyAssignedRejectCommand(command, entityKey, tenantId);
      return false;
    }
    return true;
  }

  private boolean checkMappingAssignment(
      final long entityKey, final TypedRecord<TenantRecord> command, final String tenantId) {
    final var mapping = mappingState.get(entityKey);
    if (mapping.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          formatErrorMessage(entityKey, tenantId, "doesn't exist"));
      return false;
    }
    if (mapping.get().getTenantIdsList().contains(tenantId)) {
      createEntityNotExistRejectCommand(command, entityKey, tenantId);
      return false;
    }
    return true;
  }

  private boolean checkGroupAssignment(
      final long entityKey, final TypedRecord<TenantRecord> command, final String tenantId) {
    final var group = groupState.get("entityKey");

    if (group.isEmpty()) {
      createEntityNotExistRejectCommand(command, entityKey, tenantId);
      return false;
    }

    if (group.get().getTenantIdsList().contains(tenantId)) {
      createAlreadyAssignedRejectCommand(command, entityKey, tenantId);
      return false;
    }
    return true;
  }

  private void createEntityNotExistRejectCommand(
      final TypedRecord<TenantRecord> command, final long entityKey, final String tenantId) {
    rejectCommand(
        command, RejectionType.NOT_FOUND, formatErrorMessage(entityKey, tenantId, "doesn't exist"));
  }

  private void createAlreadyAssignedRejectCommand(
      final TypedRecord<TenantRecord> command, final long entityKey, final String tenantId) {
    rejectCommand(
        command,
        RejectionType.INVALID_ARGUMENT,
        formatErrorMessage(entityKey, tenantId, "is already assigned to the tenant"));
  }

  private String formatErrorMessage(
      final long entityKey, final String tenantId, final String reason) {
    return "Expected to add entity with key '%s' to tenant with tenantId '%s', but the entity %s."
        .formatted(entityKey, tenantId, reason);
  }

  private void rejectCommandWithUnauthorizedError(
      final TypedRecord<TenantRecord> command, final Rejection rejection) {
    rejectCommand(command, rejection.type(), rejection.reason());
  }

  private void rejectCommand(
      final TypedRecord<TenantRecord> command,
      final RejectionType type,
      final String errorMessage) {
    rejectionWriter.appendRejection(command, type, errorMessage);
    responseWriter.writeRejectionOnCommand(command, type, errorMessage);
  }

  private void distributeCommand(final TypedRecord<TenantRecord> command) {
    commandDistributionBehavior
        .withKey(keyGenerator.nextKey())
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }
}
