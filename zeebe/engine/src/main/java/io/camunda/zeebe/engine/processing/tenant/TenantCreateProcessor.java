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
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class TenantCreateProcessor implements DistributedTypedRecordProcessor<TenantRecord> {

  private static final String TENANT_ALREADY_EXISTS_ERROR_MESSAGE =
      "Expected to create tenant with ID '%s', but a tenant with this ID already exists";
  private final TenantState tenantState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public TenantCreateProcessor(
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
    if (!isAuthorizedToCreate(command)) {
      return;
    }

    final var record = command.getValue();
    if (tenantAlreadyExists(record.getTenantId())) {
      rejectCommand(
          command,
          RejectionType.ALREADY_EXISTS,
          TENANT_ALREADY_EXISTS_ERROR_MESSAGE.formatted(record.getTenantId()));
    } else {
      createTenant(command, record);
      distributeCommand(command, record);
    }
  }

  @Override
  public void processDistributedCommand(final TypedRecord<TenantRecord> command) {
    final var record = command.getValue();
    tenantState
        .getTenantById(record.getTenantId())
        .ifPresentOrElse(
            tenant -> {
              final var errorMessage =
                  TENANT_ALREADY_EXISTS_ERROR_MESSAGE.formatted(tenant.getTenantId());
              rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, errorMessage);
            },
            () -> stateWriter.appendFollowUpEvent(command.getKey(), TenantIntent.CREATED, record));

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private boolean isAuthorizedToCreate(final TypedRecord<TenantRecord> command) {
    final var authorizationRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.TENANT, PermissionType.CREATE);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      rejectCommandWithUnauthorizedError(command, isAuthorized.getLeft());
      return false;
    }
    return true;
  }

  private boolean tenantAlreadyExists(final String tenantId) {
    return tenantState.getTenantById(tenantId).isPresent();
  }

  private void createTenant(final TypedRecord<TenantRecord> command, final TenantRecord record) {
    final long key = keyGenerator.nextKey();
    record.setTenantKey(key);
    stateWriter.appendFollowUpEvent(key, TenantIntent.CREATED, record);
    responseWriter.writeEventOnCommand(key, TenantIntent.CREATED, record, command);
  }

  private void distributeCommand(
      final TypedRecord<TenantRecord> command, final TenantRecord record) {
    final long key = record.getTenantKey();
    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
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
}
