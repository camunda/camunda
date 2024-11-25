/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE;

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
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserDeleteProcessor implements DistributedTypedRecordProcessor<UserRecord> {
  private final UserState userState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;

  public UserDeleteProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState state,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    userState = state.getUserState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
    this.authCheckBehavior = authCheckBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<UserRecord> command) {
    final long userKey = command.getValue().getUserKey();
    final var persistedUser = userState.getUser(userKey);

    if (persistedUser.isEmpty()) {
      final var rejectionMessage =
          "Expected to delete user with key %s, but a user with this key does not exist"
              .formatted(command.getValue().getUserKey());

      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, rejectionMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, rejectionMessage);
      return;
    }

    final var authRequest =
        new AuthorizationRequest(command, AuthorizationResourceType.USER, PermissionType.DELETE)
            .addResourceId(persistedUser.get().getUsername());
    if (!authCheckBehavior.isAuthorized(authRequest)) {
      final var message =
          UNAUTHORIZED_ERROR_MESSAGE_WITH_RESOURCE.formatted(
              authRequest.getPermissionType(),
              authRequest.getResourceType(),
              "username '%s'".formatted(persistedUser.get().getUsername()));
      rejectionWriter.appendRejection(command, RejectionType.UNAUTHORIZED, message);
      responseWriter.writeRejectionOnCommand(command, RejectionType.UNAUTHORIZED, message);
      return;
    }

    stateWriter.appendFollowUpEvent(userKey, UserIntent.DELETED, command.getValue());
    responseWriter.writeEventOnCommand(userKey, UserIntent.DELETED, command.getValue(), command);

    final long distributionKey = keyGenerator.nextKey();
    distributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<UserRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getUserKey(), UserIntent.DELETED, command.getValue());

    distributionBehavior.acknowledgeCommand(command);
  }
}
