/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static io.camunda.zeebe.protocol.record.intent.MessageIntent.*;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
public final class MessageBatchExpireProcessor implements TypedRecordProcessor<MessageBatchRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(MessageBatchExpireProcessor.class);
  private final StateWriter stateWriter;

  private final MessageRecord emptyDeleteMessageCommand =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);

  public MessageBatchExpireProcessor(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  @Override
  public void processRecord(final TypedRecord<MessageBatchRecord> record) {

    int expiredMessagesCount = 0;
    final int totalMessagesCount = record.getValue().getMessageKeys().size();
    for (final long messageKey : record.getValue().getMessageKeys()) {
      try {
        stateWriter.appendFollowUpEvent(messageKey, EXPIRED, emptyDeleteMessageCommand);
        expiredMessagesCount++;
      } catch (final ExceededBatchRecordSizeException exceededBatchRecordSizeException) {
        LOG.warn(
            "Expected to expire messages in a batch, but exceeded the resulting batch size after expiring {} out of {} messages. "
                + "Try using a lower Message TTL Checker's batch limit.",
            expiredMessagesCount,
            totalMessagesCount);
        break;
      }
    }
  }
}
