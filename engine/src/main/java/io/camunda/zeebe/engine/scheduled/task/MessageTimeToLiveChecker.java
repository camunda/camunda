/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.scheduled.task;

import io.camunda.zeebe.engine.scheduled.ScheduledEngineTask;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.immutable.MessageState.Index;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import java.time.Duration;
import org.agrona.collections.MutableInteger;

public class MessageTimeToLiveChecker implements ScheduledEngineTask {

  private long currentTimestamp;
  private MessageState.Index lastIndex;

  private final MessageRecord emptyDeleteMessageCommand =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);
  private final int batchLimit;
  private final Duration executionInterval;

  public MessageTimeToLiveChecker(final int batchLimit, final Duration executionInterval) {
    this.batchLimit = batchLimit;
    this.executionInterval = executionInterval;
  }

  @Override
  public Result execute(final Context context) {
    final var state = context.state().getMessageState();
    final var builder = context.resultBuilder();
    if (currentTimestamp == -1) {
      currentTimestamp = context.clock().millis();
    }

    final var counter = new MutableInteger(0);
    final boolean shouldContinueWhereLeftOff =
        state.visitMessagesWithDeadlineBeforeTimestamp(
            currentTimestamp,
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
                  builder.appendCommandRecord(
                      expiredMessageKey, MessageIntent.EXPIRE, emptyDeleteMessageCommand);
              return stillFitsInResult && counter.incrementAndGet() < batchLimit;
            });
    final var result = builder.build();
    if (shouldContinueWhereLeftOff) {
      return new Result(result, new SchedulingDecision.Delayed(Duration.ZERO));
    } else {
      lastIndex = null;
      currentTimestamp = -1;
      return new Result(result, new SchedulingDecision.Delayed(executionInterval));
    }
  }
}
