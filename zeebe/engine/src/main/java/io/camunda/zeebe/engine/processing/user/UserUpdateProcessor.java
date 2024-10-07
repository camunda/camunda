/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
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
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class UserUpdateProcessor implements DistributedTypedRecordProcessor<UserRecord> {

  private final UserState userState;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;

  public UserUpdateProcessor(
      final KeyGenerator keyGenerator,
      final ProcessingState state,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior) {
    this.keyGenerator = keyGenerator;
    userState = state.getUserState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<UserRecord> command) {
    final long userKey = command.getValue().getUserKey();
    final var persistedUser = userState.getUser(userKey);

    if (persistedUser.isEmpty()) {
      final var rejectionMessage =
          "Expected to update user with username %s, but a user with this username does not exist"
              .formatted(command.getValue().getUsername());

      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, rejectionMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, rejectionMessage);
      return;
    }

    final var updatedUser = overlayUser(persistedUser.get(), command.getValue());

    stateWriter.appendFollowUpEvent(userKey, UserIntent.UPDATED, updatedUser);
    responseWriter.writeEventOnCommand(userKey, UserIntent.UPDATED, updatedUser, command);

    final long distributionKey = keyGenerator.nextKey();
    distributionBehavior
        .withKey(distributionKey)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<UserRecord> command) {
    stateWriter.appendFollowUpEvent(
        command.getValue().getUserKey(), UserIntent.UPDATED, command.getValue());

    distributionBehavior.acknowledgeCommand(command);
  }

  private UserRecord overlayUser(final UserRecord persistedUser, final UserRecord updatedUser) {
    if (!updatedUser.getName().isEmpty()) {
      persistedUser.setName(updatedUser.getName());
    }

    if (!updatedUser.getEmail().isEmpty()) {
      persistedUser.setEmail(updatedUser.getEmail());
    }

    if (!updatedUser.getPassword().isEmpty()) {
      persistedUser.setPassword(updatedUser.getPassword());
    }

    return persistedUser;
  }
}
