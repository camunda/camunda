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
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantDeleteProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private static final String TENANT_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete tenant with id '%s', but no tenant with this id exists.";
  private final TenantState tenantState;
  private final AuthorizationState authorizationState;
  private final UserState userState;
  private final MembershipState membershipState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantDeleteProcessor(
      final ProcessingState state,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    tenantState = state.getTenantState();
    authorizationState = state.getAuthorizationState();
    userState = state.getUserState();
    membershipState = state.getMembershipState();
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
    final var persistedTenantRecord = tenantState.getTenantById(tenantId);

    if (persistedTenantRecord.isEmpty()) {
      rejectCommand(
          command, RejectionType.NOT_FOUND, TENANT_NOT_FOUND_ERROR_MESSAGE.formatted(tenantId));
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.DELETE)
            .addResourceId(persistedTenantRecord.get().getTenantId());
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      rejectCommandWithUnauthorizedError(command, isAuthorized.getLeft());
      return;
    }

    final var tenantKey = persistedTenantRecord.get().getTenantKey();

    record.setTenantId(persistedTenantRecord.get().getTenantId());
    record.setName(persistedTenantRecord.get().getName());
    record.setTenantKey(tenantKey);

    removeAssignedEntities(record);
    deleteAuthorizations(record);

    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.DELETED, record);
    responseWriter.writeEventOnCommand(tenantKey, TenantIntent.DELETED, record, command);
    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();
    tenantState
        .getTenantById(record.getTenantId())
        .ifPresentOrElse(
            tenant -> {
              removeAssignedEntities(command.getValue());
              deleteAuthorizations(command.getValue());
              stateWriter.appendFollowUpEvent(
                  command.getKey(), TenantIntent.DELETED, command.getValue());
            },
            () ->
                rejectCommand(
                    command,
                    RejectionType.NOT_FOUND,
                    TENANT_NOT_FOUND_ERROR_MESSAGE.formatted(record.getTenantId())));

    commandDistributionBehavior.acknowledgeCommand(command);
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

  private void removeAssignedEntities(final TenantRecord record) {
    final var tenant = tenantState.getTenantById(record.getTenantId()).orElseThrow();
    final var tenantId = tenant.getTenantId();
    final var tenantKey = tenant.getTenantKey();
    membershipState.forEachMember(
        RelationType.TENANT,
        tenantId,
        (entityType, entityId) -> {
          stateWriter.appendFollowUpEvent(
              tenantKey,
              TenantIntent.ENTITY_REMOVED,
              new TenantRecord()
                  .setTenantId(tenantId)
                  .setEntityType(entityType)
                  .setEntityId(entityId));
        });
  }

  private void deleteAuthorizations(final TenantRecord record) {
    final var tenantId = record.getTenantId();
    final var authorizationKeysForGroup =
        authorizationState.getAuthorizationKeysForOwner(AuthorizationOwnerType.TENANT, tenantId);

    authorizationKeysForGroup.forEach(
        authorizationKey -> {
          final var authorization =
              new AuthorizationRecord()
                  .setAuthorizationKey(authorizationKey)
                  .setResourceMatcher(AuthorizationResourceMatcher.UNSPECIFIED);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }
}
