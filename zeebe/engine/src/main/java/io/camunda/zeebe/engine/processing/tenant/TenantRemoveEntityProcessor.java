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
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantRemoveEntityProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private final TenantState tenantState;
  private final UserState userState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantRemoveEntityProcessor(
      final ProcessingState state,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    tenantState = state.getTenantState();
    userState = state.getUserState();
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
    final var tenantId = record.getTenantId();
    final var persistedTenant = tenantState.getTenantById(tenantId);

    if (persistedTenant.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to remove entity from tenant '%s', but no tenant with this id exists."
              .formatted(tenantId));
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.UPDATE)
            .addResourceId(tenantId);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      rejectCommandWithUnauthorizedError(command, isAuthorized.getLeft());
      return;
    }

    if (!validateEntityAssignment(command)) {
      return;
    }

    final var tenantKey = persistedTenant.get().getTenantKey();
    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.ENTITY_REMOVED, record);
    responseWriter.writeEventOnCommand(tenantKey, TenantIntent.ENTITY_REMOVED, record, command);
    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    if (validateEntityAssignment(command)) {
      stateWriter.appendFollowUpEvent(
          command.getKey(), TenantIntent.ENTITY_REMOVED, command.getValue());
    }

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean validateEntityAssignment(final TypedRecord<TenantRecord> command) {
    return switch (command.getValue().getEntityType()) {
      case USER -> validateUserAssignment(command);
      default ->
          throw new UnsupportedOperationException(
              "Removing entities of type '%s' is not supported."
                  .formatted(command.getValue().getEntityType()));
    };
  }

  private boolean validateUserAssignment(final TypedRecord<TenantRecord> command) {
    final var tenantId = command.getValue().getTenantId();
    final var entityId = command.getValue().getEntityId();
    final var user = userState.getUser(entityId);
    if (user.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to remove user '%s' from tenant, but no user with this id exists."
              .formatted(entityId));
      return false;
    }
    if (!user.get().getTenantIdsList().contains(tenantId)) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to remove user '%s' from tenant '%s', but the user is not assigned to this tenant."
              .formatted(entityId, tenantId));
      return false;
    }
    return true;
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
    if (command.hasRequestMetadata()) {
      responseWriter.writeRejectionOnCommand(command, type, errorMessage);
    }
  }

  private void distributeCommand(final TypedRecord<TenantRecord> command) {
    commandDistributionBehavior
        .withKey(keyGenerator.nextKey())
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }
}
