/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Optional;

public class AuthorizationCreateProcessor
    implements DistributedTypedRecordProcessor<AuthorizationRecord> {

  private final KeyGenerator keyGenerator;
  private final CommandDistributionBehavior distributionBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final PermissionsBehavior permissionsBehavior;
  private final UserState userState;

  public AuthorizationCreateProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ProcessingState processingState,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    this.distributionBehavior = distributionBehavior;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    permissionsBehavior = new PermissionsBehavior(processingState, authCheckBehavior);
    userState = processingState.getUserState();
  }

  @Override
  public void processNewCommand(final TypedRecord<AuthorizationRecord> command) {
    permissionsBehavior
        .isAuthorized(command, PermissionType.CREATE)
        .flatMap(
            record ->
                permissionsBehavior.hasValidPermissionTypes(
                    record,
                    record.getAuthorizationPermissions(),
                    record.getResourceType(),
                    "Expected to create authorization with permission types '%s' and resource type '%s', but these permissions are not supported. Supported permission types are: '%s'"))
        .flatMap(permissionsBehavior::permissionsAlreadyExist)
        .ifRightOrLeft(
            authorizationRecord -> writeEventAndDistribute(command, command.getValue()),
            (rejection) -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
            });
  }

  @Override
  public void processDistributedCommand(final TypedRecord<AuthorizationRecord> command) {
    permissionsBehavior
        .permissionsAlreadyExist(command.getValue())
        .ifRightOrLeft(
            ignored ->
                stateWriter.appendFollowUpEvent(
                    command.getKey(), AuthorizationIntent.CREATED, command.getValue()),
            rejection ->
                rejectionWriter.appendRejection(command, rejection.type(), rejection.reason()));

    distributionBehavior.acknowledgeCommand(command);
  }

  private void writeEventAndDistribute(
      final TypedRecord<AuthorizationRecord> command,
      final AuthorizationRecord authorizationRecord) {
    final long key = keyGenerator.nextKey();
    authorizationRecord.setAuthorizationKey(key);
    stateWriter.appendFollowUpEvent(key, AuthorizationIntent.CREATED, authorizationRecord);
    responseWriter.writeEventOnCommand(
        key, AuthorizationIntent.CREATED, authorizationRecord, command);
    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }
}
