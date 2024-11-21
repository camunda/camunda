/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.tenant;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
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

  private final TenantState tenantState;
  private final UserState userState;
  private final MappingState mappingState;
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
    final var persistedRecord = tenantState.getTenantByKey(tenantKey);
    if (persistedRecord.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to add entity to tenant with key '%s', but no tenant with this key exists."
              .formatted(tenantKey));
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.UPDATE)
            .addResourceId(record.getTenantId());
    if (!authCheckBehavior.isAuthorized(authorizationRequest)) {
      rejectCommandWithUnauthorizedError(command, authorizationRequest, record.getTenantId());
      return;
    }

    final var entityKey = record.getEntityKey();
    if (!isEntityPresentAndNotAssigned(
        entityKey, record.getEntityType(), command, tenantKey, record.getTenantId())) {
      return;
    }

    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.ENTITY_ADDED, record);
    responseWriter.writeEventOnCommand(tenantKey, TenantIntent.ENTITY_ADDED, record, command);

    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getKey(), TenantIntent.ENTITY_ADDED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isEntityPresentAndNotAssigned(
      final long entityKey,
      final EntityType entityType,
      final TypedRecord<TenantRecord> command,
      final long tenantKey,
      final String tenantId) {
    return switch (entityType) {
      case USER -> checkUserAssignment(entityKey, command, tenantId, tenantKey);
      case MAPPING -> checkMappingAssignment(entityKey, command, tenantKey);
      default ->
          throw new IllegalStateException(
              formatErrorMessage(entityKey, tenantKey, "doesn't exist"));
    };
  }

  private boolean checkUserAssignment(
      final long entityKey,
      final TypedRecord<TenantRecord> command,
      final String tenantId,
      final long tenantKey) {
    final var user = userState.getUser(entityKey);
    if (user.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          formatErrorMessage(entityKey, tenantKey, "doesn't exist"));
      return false;
    }
    if (user.get().getTenantIdsList().contains(tenantId)) {
      rejectCommand(
          command,
          RejectionType.INVALID_ARGUMENT,
          formatErrorMessage(entityKey, tenantKey, "is already assigned to the tenant"));
      return false;
    }
    return true;
  }

  private boolean checkMappingAssignment(
      final long entityKey, final TypedRecord<TenantRecord> command, final long tenantKey) {
    final var mapping = mappingState.get(entityKey);
    if (mapping.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          formatErrorMessage(entityKey, tenantKey, "doesn't exist"));
      return false;
    }
    if (mapping.get().getTenantKeysList().contains(tenantKey)) {
      rejectCommand(
          command,
          RejectionType.INVALID_ARGUMENT,
          formatErrorMessage(entityKey, tenantKey, "is already assigned to the tenant"));
      return false;
    }
    return true;
  }

  private String formatErrorMessage(
      final long entityKey, final long tenantKey, final String reason) {
    return "Expected to add entity with key '%s' to tenant with key '%s', but the entity %s."
        .formatted(entityKey, tenantKey, reason);
  }

  private void rejectCommandWithUnauthorizedError(
      final TypedRecord<TenantRecord> command,
      final AuthorizationRequest authorizationRequest,
      final String tenantId) {
    final var errorMessage =
        AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
            authorizationRequest.getPermissionType(),
            authorizationRequest.getResourceType(),
            "tenant id '%s'".formatted(tenantId));
    rejectCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
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
