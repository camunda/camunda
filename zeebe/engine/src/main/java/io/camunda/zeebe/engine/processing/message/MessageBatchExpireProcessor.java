/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.time.InstantSource;
import org.agrona.collections.MutableLong;

@ExcludeAuthorizationCheck
public final class MessageBatchExpireProcessor implements TypedRecordProcessor<MessageBatchRecord> {

  /** The safety margin to ensure that we can always write an empty EXPIRE command at the end. */
  private static final int FOLLOWUP_COMMAND_SAFETY_MARGIN = 8192;

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final MessageState messageState;
  private final int batchLimit;
  private final boolean appendMessageBodyOnExpired;
  private final InstantSource clock;

  private final MessageRecord emptyDeleteMessageCommand =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);

  public MessageBatchExpireProcessor(
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final MessageState messageState,
      final int batchLimit,
      final boolean appendMessageBodyOnExpired,
      final InstantSource clock) {
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.messageState = messageState;
    this.batchLimit = batchLimit;
    this.appendMessageBodyOnExpired = appendMessageBodyOnExpired;
    this.clock = clock;
  }

  @Override
  public void processRecord(final TypedRecord<MessageBatchRecord> record) {
    final var expiredCount = new MutableLong(0);
    final boolean hasMore =
        messageState.visitMessagesWithDeadlineBeforeTimestamp(
            clock.millis(),
            null,
            (deadline, messageKey) -> {
              final var expiredMessageRecord =
                  appendMessageBodyOnExpired
                      ? messageState.getMessage(messageKey).getMessage()
                      : emptyDeleteMessageCommand;

              final var requiredCapacity =
                  expiredMessageRecord.getLength() + FOLLOWUP_COMMAND_SAFETY_MARGIN;
              if (stateWriter.canWriteEventOfLength(requiredCapacity)
                  && expiredCount.getAndIncrement() < batchLimit) {
                stateWriter.appendFollowUpEvent(
                    messageKey, MessageIntent.EXPIRED, expiredMessageRecord);
                return true;
              } else {
                return false;
              }
            });

    if (hasMore) {
      commandWriter.appendFollowUpCommand(
          record.getKey(), MessageBatchIntent.EXPIRE, new MessageBatchRecord());
    }
  }
}
