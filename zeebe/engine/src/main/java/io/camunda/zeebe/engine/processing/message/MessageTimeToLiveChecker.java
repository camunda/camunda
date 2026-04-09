/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;

/**
 * Periodically checks for expired message deadlines and writes a {@link MessageBatchIntent#EXPIRE}
 * trigger command when any are found. The actual expiry work (querying state and writing {@code
 * EXPIRED} events) is done by the {@link MessageBatchExpireProcessor}.
 */
public final class MessageTimeToLiveChecker implements Task {

  private final Duration executionInterval;
  private final boolean enableMessageTtlCheckerAsync;
  private final ProcessingScheduleService scheduleService;
  private final MessageState messageState;
  private final InstantSource clock;

  public MessageTimeToLiveChecker(
      final Duration executionInterval,
      final boolean enableMessageTtlCheckerAsync,
      final ProcessingScheduleService scheduleService,
      final MessageState messageState,
      final InstantSource clock) {
    this.executionInterval = executionInterval;
    this.enableMessageTtlCheckerAsync = enableMessageTtlCheckerAsync;
    this.messageState = messageState;
    this.scheduleService = scheduleService;
    this.clock = clock;
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final boolean hasExpired =
        messageState.visitMessagesWithDeadlineBeforeTimestamp(
            clock.millis(), null, (deadline, key) -> false);
    if (hasExpired) {
      taskResultBuilder.appendCommandRecord(MessageBatchIntent.EXPIRE, new MessageBatchRecord());
    }
    reschedule(executionInterval);
    return taskResultBuilder.build();
  }

  private void reschedule(final Duration idleInterval) {
    final var timestamp = clock.millis() + idleInterval.toMillis();
    if (enableMessageTtlCheckerAsync) {
      scheduleService.runAtAsync(timestamp, this);
    } else {
      scheduleService.runAt(timestamp, this);
    }
  }
}
