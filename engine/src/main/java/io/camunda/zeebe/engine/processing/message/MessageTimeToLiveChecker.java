/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.engine.metrics.StreamProcessorMetrics;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.scheduler.clock.ActorClock;

public final class MessageTimeToLiveChecker implements Task {

  private static final MessageRecord EMPTY_DELETE_MESSAGE_COMMAND =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);

  private final StreamProcessorMetrics metrics;
  private final MessageState messageState;

  public MessageTimeToLiveChecker(
      final MessageState messageState, final StreamProcessorMetrics metrics) {
    this.messageState = messageState;
    this.metrics = metrics;
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    try (final var timer = metrics.startMessageCheckerDurationTimer()) {
      messageState.visitMessagesWithDeadlineBefore(
          ActorClock.currentTimeMillis(),
          message -> writeDeleteMessageCommand(message, taskResultBuilder));
      return taskResultBuilder.build();
    }
  }

  private boolean writeDeleteMessageCommand(
      final long expiredMessageKey, final TaskResultBuilder taskResultBuilder) {
    return taskResultBuilder.appendCommandRecord(
        expiredMessageKey, MessageIntent.EXPIRE, EMPTY_DELETE_MESSAGE_COMMAND);
  }
}
