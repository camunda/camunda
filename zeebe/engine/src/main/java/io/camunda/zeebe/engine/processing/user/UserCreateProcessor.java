/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserState;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class UserCreateProcessor implements TypedRecordProcessor<UserRecord> {

  private final UserState userState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior distributionBehavior;

  public UserCreateProcessor(
      final ProcessingState state,
      final Writers writers,
      final CommandDistributionBehavior distributionBehavior) {
    userState = state.getUserState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.distributionBehavior = distributionBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<UserRecord> command) {
    final long key = command.getKey();
    final var username = command.getValue().getUsernameBuffer();
    final var user = userState.getUser(username);

    if (user != null) {
      rejectionWriter.appendRejection(command, RejectionType.ALREADY_EXISTS, "user already exists");
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.ALREADY_EXISTS, "user already exists");
      return;
    }

    stateWriter.appendFollowUpEvent(key, UserIntent.CREATED, command.getValue());
    responseWriter.writeEventOnCommand(key, UserIntent.CREATED, command.getValue(), command);

    distributionBehavior.distributeCommand(key, command);
  }
}
