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

public abstract class AbstractTenantProcessor
    implements DistributedTypedRecordProcessor<TenantRecord> {

  protected final TenantState tenantState;
  protected final AuthorizationCheckBehavior authCheckBehavior;
  protected final KeyGenerator keyGenerator;
  protected final StateWriter stateWriter;
  protected final TypedRejectionWriter rejectionWriter;
  protected final TypedResponseWriter responseWriter;
  protected final CommandDistributionBehavior commandDistributionBehavior;

  public AbstractTenantProcessor(
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

  protected boolean isAuthorized(
      final TypedRecord<TenantRecord> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType) {
    return isAuthorized(command, resourceType, permissionType, null);
  }

  protected boolean isAuthorized(
      final TypedRecord<TenantRecord> command,
      final AuthorizationResourceType resourceType,
      final PermissionType permissionType,
      final String resourceId) {

    final var authorizationRequest =
        new AuthorizationRequest(command, resourceType, permissionType);

    if (resourceId != null) {
      authorizationRequest.addResourceId(resourceId);
    }

    if (!authCheckBehavior.isAuthorized(authorizationRequest)) {
      rejectCommandWithUnauthorizedError(command, authorizationRequest);
      return false;
    }
    return true;
  }

  protected void rejectCommandWithUnauthorizedError(
      final TypedRecord<TenantRecord> command, final AuthorizationRequest authorizationRequest) {
    final var errorMessage =
        AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE.formatted(
            authorizationRequest.getPermissionType(), authorizationRequest.getResourceType());
    rejectCommand(command, RejectionType.UNAUTHORIZED, errorMessage);
  }

  protected void rejectCommand(
      final TypedRecord<TenantRecord> command,
      final RejectionType type,
      final String errorMessage) {
    rejectionWriter.appendRejection(command, type, errorMessage);
    responseWriter.writeRejectionOnCommand(command, type, errorMessage);
  }

  protected void distributeCommand(
      final TypedRecord<TenantRecord> command, final long distributionKey) {
    commandDistributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  protected boolean tenantExistsWithKey(final long tenantKey) {
    return tenantState.getTenantByKey(tenantKey).isPresent();
  }

  protected boolean tenantExistsWithId(final String tenantId) {
    return tenantState.getTenantKeyById(tenantId).isPresent();
  }

  protected void appendEventAndWriteResponse(
      final long key,
      final TenantIntent intent,
      final TenantRecord record,
      final TypedRecord<TenantRecord> command) {
    stateWriter.appendFollowUpEvent(key, intent, record);
    responseWriter.writeEventOnCommand(key, intent, record, command);
  }
}
