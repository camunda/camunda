/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.scheduled.api.Outcome;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;

/**
 * Detects expired message deadlines and writes a single {@link MessageBatchIntent#EXPIRE} command
 * when any are found. The actual expiry work is done by {@code MessageBatchExpireProcessor}.
 *
 * <p>Lifecycle, scheduling cadence (default fallback interval 1 minute), error handling, logging
 * and metrics are provided by {@code ManagedScheduledTask}.
 */
public final class MessageTimeToLiveCheckScheduler implements ScheduledTask {

  private final MessageState messageState;

  public MessageTimeToLiveCheckScheduler(final MessageState messageState) {
    this.messageState = messageState;
  }

  @Override
  public String name() {
    return "message-ttl-check";
  }

  @Override
  public Outcome run(final TaskContext ctx) {
    final boolean hasExpired =
        messageState.visitMessagesWithDeadlineBeforeTimestamp(
            ctx.clock().millis(), null, (deadline, key) -> false);
    if (hasExpired) {
      ctx.sink().append(MessageBatchIntent.EXPIRE, new MessageBatchRecord());
    }
    return Outcome.IDLE;
  }
}
