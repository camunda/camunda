/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class MessageBatchExpireProcessor implements TypedRecordProcessor<MessageBatchRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(MessageBatchExpireProcessor.class);

  /** The safety margin to ensure that we can always write an empty EXPIRE command at the end. */
  private static final int FOLLOWUP_COMMAND_SAFETY_MARGIN = 8192;

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final MessageState messageState;
  private final int batchLimit;
  private final boolean appendMessageBodyOnExpired;
  private final InstantSource clock;
  private final MessageCorrelationMetrics metrics;

  private final MessageRecord emptyDeleteMessageCommand =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);

  public MessageBatchExpireProcessor(
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final MessageState messageState,
      final int batchLimit,
      final boolean appendMessageBodyOnExpired,
      final InstantSource clock,
      final MessageCorrelationMetrics metrics) {
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.messageState = messageState;
    this.batchLimit = batchLimit;
    this.appendMessageBodyOnExpired = appendMessageBodyOnExpired;
    this.clock = clock;
    this.metrics = metrics;
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
                // M7: a buffered message that carried a pending cross-partition message-start ask
                // has now blocked the whole TTL window without starting — close its ask timer(s) as
                // expired. A no-op for every message with no outstanding ask.
                if (metrics.expireCrossPartitionAsks(messageKey)) {
                  LOG.atDebug()
                      .addKeyValue("messageKey", messageKey)
                      .log(
                          "Buffered message expired while its cross-partition ask was still pending");
                }
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
