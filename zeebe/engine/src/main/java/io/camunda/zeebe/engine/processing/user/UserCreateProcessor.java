/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.Permission;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserCreateProcessor implements DistributedTypedRecordProcessor<UserRecord> {

  private final UserState userState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final TypedCommandWriter commandWriter;

  public UserCreateProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState state,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    userState = state.getUserState();
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    commandWriter = writers.command();
    this.distributionBehavior = distributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<UserRecord> command) {
    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.USER, PermissionType.CREATE);
    if (!authCheckBehavior.isAuthorized(authRequest)) {
      final var message =
          AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE.formatted(
              authRequest.getPermissionType(), authRequest.getResourceType());
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, message);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, message);
      return;
    }

    final var username = command.getValue().getUsername();
    final var user = userState.getUser(username);

    if (user.isPresent()) {
      final var message =
          "Expected to create user with username '%s', but a user with this username already exists"
              .formatted(user.get().getUsername());
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, message);
      responseWriter.writeRejectionOnCommand(command, RejectionType.ALREADY_EXISTS, message);
      return;
    }

    final long key = keyGenerator.nextKey();
    command.getValue().setUserKey(key);

    stateWriter.appendFollowUpEvent(key, UserIntent.CREATED, command.getValue());
    addUserPermissions(key, username);
    responseWriter.writeEventOnCommand(key, UserIntent.CREATED, command.getValue(), command);

    distributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<UserRecord> command) {
    stateWriter.appendFollowUpEvent(command.getKey(), UserIntent.CREATED, command.getValue());

    distributionBehavior.acknowledgeCommand(command);
  }

  private void addUserPermissions(final long key, final String username) {
    final var authorizationRecord =
        new AuthorizationRecord()
            .setOwnerKey(key)
            .setOwnerType(AuthorizationOwnerType.USER)
            .setResourceType(AuthorizationResourceType.USER)
            .addPermission(
                new Permission().setPermissionType(PermissionType.READ).addResourceId(username))
            .addPermission(
                new Permission().setPermissionType(PermissionType.UPDATE).addResourceId(username));
    commandWriter.appendFollowUpCommand(
        key, AuthorizationIntent.ADD_PERMISSION, authorizationRecord);
  }
}
