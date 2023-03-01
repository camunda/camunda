/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageState.Index;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import org.agrona.collections.MutableInteger;

/**
 * The Message TTL Checker looks for expired message deadlines, and for each of those it writes an
 * EXPIRE Message command.
 *
 * <p>To prevent that it clogs the log stream with too many EXPIRE Message commands, it only writes
 * a limited number of these commands in a single run of {@link #execute(TaskResultBuilder)}.
 *
 * <p>It determines whether to reschedule itself immediately, or after the configured {@link
 * MessageObserver#MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL interval}. If it reschedules itself
 * immediately, then it will continue where it left off the last time. Otherwise, it starts with the
 * first expired message deadline it can find.
 */
public final class MessageTimeToLiveChecker implements Task {

  private static final MessageRecord EMPTY_DELETE_MESSAGE_COMMAND =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);

  private static final int EXPIRE_COMMANDS_LIMIT_DEFAULT = 10;

  /** This determines the maximum number of EXPIRE commands it will attempt to fit in the result. */
  private static final int LIMIT = EXPIRE_COMMANDS_LIMIT_DEFAULT;

  private final ProcessingScheduleService scheduleService;
  private final MessageState messageState;
  /** Keeps track of where to continue between iterations. */
  private MessageState.Index lastIndex;

  public MessageTimeToLiveChecker(
      final ProcessingScheduleService scheduleService, final MessageState messageState) {
    this.scheduleService = scheduleService;
    this.messageState = messageState;
    lastIndex = null;
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    final var counter = new MutableInteger(0);

    final boolean shouldContinueWhereLeftOff =
        messageState.visitMessagesWithDeadlineBeforeTimestamp(
            ActorClock.currentTimeMillis(),
            lastIndex,
            (deadline, expiredMessageKey) -> {
              final var newIndex = new Index(expiredMessageKey, deadline);
              final boolean wasIndexAlreadyVisitedLastTime = newIndex.equals(lastIndex);
              lastIndex = newIndex;

              if (wasIndexAlreadyVisitedLastTime) {
                // skip this entry
                return true;
              }

              final boolean stillFitsInResult =
                  taskResultBuilder.appendCommandRecord(
                      expiredMessageKey, MessageIntent.EXPIRE, EMPTY_DELETE_MESSAGE_COMMAND);
              return stillFitsInResult && counter.incrementAndGet() <= LIMIT;
            });

    if (shouldContinueWhereLeftOff) {
      scheduleService.runDelayedAsync(Duration.ZERO, this);
    } else {
      lastIndex = null;
      scheduleService.runDelayedAsync(MessageObserver.MESSAGE_TIME_TO_LIVE_CHECK_INTERVAL, this);
    }

    return taskResultBuilder.build();
  }
}
