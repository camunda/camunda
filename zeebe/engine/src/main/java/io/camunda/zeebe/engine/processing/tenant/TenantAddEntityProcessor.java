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
import io.camunda.zeebe.engine.processing.identity.TenantValidator;
import io.camunda.zeebe.engine.processing.identity.adapter.MembershipStateAdapter;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantAddEntityProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private final TenantState tenantState;
  private final TenantValidator tenantValidator;
  private final PermissionsBehavior permissionsBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;
  private final SideEffectWriter sideEffectWriter;
  private final MembershipStateAdapter membershipStateAdapter;

  public TenantAddEntityProcessor(
      final TenantState tenantState,
      final TenantValidator tenantValidator,
      final PermissionsBehavior permissionsBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior,
      final MembershipStateAdapter membershipStateAdapter) {
    this.tenantState = tenantState;
    this.tenantValidator = tenantValidator;
    this.permissionsBehavior = permissionsBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    this.commandDistributionBehavior = commandDistributionBehavior;
    this.membershipStateAdapter = membershipStateAdapter;
  }

  @Override
  public void processNewCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();
    final var tenantId = record.getTenantId();
    final var authResult =
        permissionsBehavior.isAuthorized(
            command, AuthorizationResourceType.TENANT, PermissionType.UPDATE, tenantId);
    if (authResult.isLeft()) {
      rejectCommand(command, authResult.getLeft());
      return;
    }

    final var validation = tenantValidator.validateAddEntity(record);
    if (validation.isLeft()) {
      rejectCommand(command, validation.getLeft());
      return;
    }

    final var tenantKey = tenantState.getTenantById(tenantId).orElseThrow().getTenantKey();
    record.setTenantKey(tenantKey);

    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.ENTITY_ADDED, record);
    responseWriter.writeAcceptedResponseOnCommand(
        tenantKey, TenantIntent.ENTITY_ADDED, record, command);
    invalidateMembershipCache();

    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    tenantValidator
        .validateEntityNotAssigned(command.getValue())
        .ifRightOrLeft(
            record -> {
              stateWriter.appendFollowUpEvent(command.getKey(), TenantIntent.ENTITY_ADDED, record);
              invalidateMembershipCache();
            },
            rejection -> rejectCommand(command, rejection));

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  /**
   * Flushes {@link MembershipStateAdapter}'s cache after a tenant-membership change, so a stale
   * entry does not authorize (or reject) against outdated membership until its TTL expires. The
   * scope cache is not touched: it holds RBAC grants only, which a membership change does not
   * affect.
   */
  private void invalidateMembershipCache() {
    sideEffectWriter.appendSideEffect(
        () -> {
          membershipStateAdapter.invalidateAll();
        });
  }

  private void rejectCommand(final TypedRecord<TenantRecord> command, final Rejection rejection) {
    rejectCommand(command, rejection.type(), rejection.reason());
  }

  private void rejectCommand(
      final TypedRecord<TenantRecord> command,
      final RejectionType type,
      final String errorMessage) {
    rejectionWriter.appendRejection(command, type, errorMessage);
    if (command.hasRequestMetadata()) {
      responseWriter.writeRejectedResponseOnCommand(command, type, errorMessage);
    }
  }

  private void distributeCommand(final TypedRecord<TenantRecord> command) {
    commandDistributionBehavior
        .withKey(keyGenerator.nextKey())
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }
}
