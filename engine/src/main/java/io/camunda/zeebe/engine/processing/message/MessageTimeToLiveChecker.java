/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.message;

import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.state.immutable.MessageState;
import io.zeebe.engine.state.message.StoredMessage;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.util.sched.clock.ActorClock;

public final class MessageTimeToLiveChecker implements Runnable {

  private final TypedCommandWriter writer;
  private final MessageState messageState;

  private final MessageRecord deleteMessageCommand = new MessageRecord();

  public MessageTimeToLiveChecker(
      final TypedCommandWriter writer, final MessageState messageState) {
    this.writer = writer;
    this.messageState = messageState;
  }

  @Override
  public void run() {
    messageState.visitMessagesWithDeadlineBefore(
        ActorClock.currentTimeMillis(), this::writeDeleteMessageCommand);
  }

  private boolean writeDeleteMessageCommand(final StoredMessage storedMessage) {
    final var message = storedMessage.getMessage();

    deleteMessageCommand.reset();
    deleteMessageCommand
        .setName(message.getName())
        .setCorrelationKey(message.getCorrelationKey())
        .setTimeToLive(message.getTimeToLive())
        .setVariables(message.getVariablesBuffer());

    if (message.hasMessageId()) {
      deleteMessageCommand.setMessageId(message.getMessageIdBuffer());
    }

    writer.reset();
    writer.appendFollowUpCommand(
        storedMessage.getMessageKey(), MessageIntent.EXPIRE, deleteMessageCommand);

    final long position = writer.flush();
    return position > 0;
  }
}
