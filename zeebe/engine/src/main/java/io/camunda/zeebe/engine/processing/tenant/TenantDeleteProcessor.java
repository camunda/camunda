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
import io.camunda.zeebe.engine.processing.identity.PermissionsBehavior;
import io.camunda.zeebe.engine.processing.identity.adapter.AuthorizationScopeStateAdapter;
import io.camunda.zeebe.engine.processing.identity.adapter.MembershipStateAdapter;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
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
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final AuthorizationScopeStateAdapter authorizationScopeStateAdapter;
  private final MembershipStateAdapter membershipStateAdapter;
  private final SideEffectWriter sideEffectWriter;

  public TenantDeleteProcessor(
      final ProcessingState state,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final AuthorizationScopeStateAdapter authorizationScopeStateAdapter,
      final MembershipStateAdapter membershipStateAdapter) {
    tenantState = state.getTenantState();
    authorizationState = state.getAuthorizationState();
    userState = state.getUserState();
    membershipState = state.getMembershipState();
    this.permissionsBehavior = permissionsBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.authorizationScopeStateAdapter = authorizationScopeStateAdapter;
    this.membershipStateAdapter = membershipStateAdapter;
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

    final var authResult =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.TENANT, PermissionType.DELETE, tenantId);
    if (authResult.isLeft()) {
      rejectCommandWithUnauthorizedError(command, authResult.getLeft());
      return;
    }

    final var tenantKey = persistedTenantRecord.get().getTenantKey();

    record.setTenantId(persistedTenantRecord.get().getTenantId());
    record.setName(persistedTenantRecord.get().getName());
    record.setTenantKey(tenantKey);

    removeAssignedEntities(record);
    deleteAuthorizations(record);

    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.DELETED, record);
    responseWriter.writeAcceptedResponseOnCommand(tenantKey, TenantIntent.DELETED, record, command);
    invalidateAuthorizationCaches();

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
              invalidateAuthorizationCaches();
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
    responseWriter.writeRejectedResponseOnCommand(command, type, errorMessage);
  }

  private void distributeCommand(final TypedRecord<TenantRecord> command) {
    commandDistributionBehavior
        .withKey(keyGenerator.nextKey())
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  /**
   * Flushes both authorization caches after a tenant is deleted. Deleting a tenant removes its
   * memberships (stale in {@link MembershipStateAdapter}'s cache) and its authorization grants
   * (stale in the {@link AuthorizationScopeStateAdapter} scope cache), so both must be invalidated.
   */
  private void invalidateAuthorizationCaches() {
    sideEffectWriter.appendSideEffect(
        () -> {
          authorizationScopeStateAdapter.invalidateAll();
          membershipStateAdapter.invalidateAll();
          return true;
        });
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
