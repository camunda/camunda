/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.streamprocessor.ProcessingResult;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandsBuilder;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import java.util.function.Supplier;

public final class MessageTimeToLiveChecker implements Supplier<ProcessingResult> {

  private final CommandsBuilder commandsBuilder;
  private final MessageState messageState;

  private final MessageRecord deleteMessageCommand = new MessageRecord();

  public MessageTimeToLiveChecker(final CommandsBuilder writer, final MessageState messageState) {
    commandsBuilder = writer;
    this.messageState = messageState;
  }

  @Override
  public ProcessingResult get() {
    commandsBuilder.reset();
    messageState.visitMessagesWithDeadlineBefore(
        ActorClock.currentTimeMillis(), this::writeDeleteMessageCommand);

    return new ProcessingResult(commandsBuilder);
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

    commandsBuilder.appendFollowUpCommand(
        storedMessage.getMessageKey(), MessageIntent.EXPIRE, deleteMessageCommand);

    // TODO we need a check to determine how much events/commands can be added (we already have
    // something like that)
    return true;
  }
}
