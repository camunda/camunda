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

public class TenantUpdateProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private final TenantState tenantState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantUpdateProcessor(
      final TenantState tenantState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    this.tenantState = tenantState;
    this.authCheckBehavior = authCheckBehavior;
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
      final var errorMessage =
          "Expected to update tenant with key '%s', but no tenant with this key exists."
              .formatted(tenantKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.UPDATE)
            .addResourceId(record.getTenantId());
    if (!authCheckBehavior.isAuthorized(authorizationRequest)) {
      final var errorMessage =
          AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE.formatted(
              authorizationRequest.getPermissionType(), authorizationRequest.getResourceType());
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
      return;
    }

    final var updatedTenantId = record.getTenantId();
    if (!updatedTenantId.equals(persistedRecord.get().getTenantId())
        && tenantState.getTenantKeyById(updatedTenantId).isPresent()) {
      final var errorMessage =
          "Expected to update tenant with ID '%s', but a tenant with this ID already exists."
              .formatted(updatedTenantId);
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, errorMessage);
      return;
    }

    stateWriter.appendFollowUpEvent(
        persistedRecord.get().getTenantKey(), TenantIntent.UPDATED, record);
    responseWriter.writeEventOnCommand(
        persistedRecord.get().getTenantKey(), TenantIntent.UPDATED, record, command);

    distributeCommand(command, record);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    final TenantRecord tenantRecord = command.getValue();
    final long tenantKey = tenantState.getTenantKeyById(tenantRecord.getTenantId()).get();
    stateWriter.appendFollowUpEvent(tenantKey, TenantIntent.UPDATED, tenantRecord);
    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void distributeCommand(
      final TypedRecord<TenantRecord> command, final TenantRecord record) {
    final long tenantKey = tenantState.getTenantKeyById(record.getTenantId()).get();
    commandDistributionBehavior
        .withKey(tenantKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }
}
