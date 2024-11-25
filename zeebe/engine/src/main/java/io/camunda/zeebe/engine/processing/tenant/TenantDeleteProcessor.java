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
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantDeleteProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private final TenantState tenantState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantDeleteProcessor(
      final TenantState tenantState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.tenantState = tenantState;
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
    final var persistedTenantRecord = tenantState.getTenantByKey(tenantKey);

    if (persistedTenantRecord.isEmpty()) {
      rejectCommand(
          command,
          RejectionType.NOT_FOUND,
          "Expected to delete tenant with key '%s', but no tenant with this key exists."
              .formatted(tenantKey));
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.DELETE)
            .addResourceId(persistedTenantRecord.get().getTenantId());
    if (!authCheckBehavior.isAuthorized(authorizationRequest)) {
      rejectCommandWithUnauthorizedError(
          command, authorizationRequest, persistedTenantRecord.get().getTenantId());
      return;
    }

    record.setTenantId(persistedTenantRecord.get().getTenantId());
    record.setName(persistedTenantRecord.get().getName());

    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.DELETED, record);
    responseWriter.writeEventOnCommand(tenantKey, TenantIntent.DELETED, record, command);
    distributeCommand(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    stateWriter.appendFollowUpEvent(command.getKey(), TenantIntent.DELETED, command.getValue());
    commandDistributionBehavior.acknowledgeCommand(command);
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
