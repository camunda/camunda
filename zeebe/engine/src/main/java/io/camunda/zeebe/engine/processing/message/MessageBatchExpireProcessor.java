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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.HandlesIntent;
import io.camunda.zeebe.protocol.record.intent.MessageBatchIntent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExcludeAuthorizationCheck
@HandlesIntent(intent = MessageBatchIntent.class, type = "EXPIRE")
public final class MessageBatchExpireProcessor implements TypedRecordProcessor<MessageBatchRecord> {

  private static final Logger LOG = LoggerFactory.getLogger(MessageBatchExpireProcessor.class);
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final MessageState messageState;
  private final boolean appendMessageBodyOnExpired;

  private final MessageRecord emptyDeleteMessageCommand =
      new MessageRecord().setName("").setCorrelationKey("").setTimeToLive(-1L);

  public MessageBatchExpireProcessor(
      final StateWriter stateWriter,
      final TypedRejectionWriter rejectionWriter,
      final MessageState messageState,
      final boolean appendMessageBodyOnExpired) {
    this.stateWriter = stateWriter;
    this.rejectionWriter = rejectionWriter;
    this.messageState = messageState;
    this.appendMessageBodyOnExpired = appendMessageBodyOnExpired;
  }

  @Override
  public void processRecord(final TypedRecord<MessageBatchRecord> record) {
    int expiredMessagesCount = 0;
    final int totalMessagesCount = record.getValue().getMessageKeys().size();

    for (final long messageKey : record.getValue().getMessageKeys()) {
      try {
        if (appendMessageBodyOnExpired) {
          expiredMessagesCount += expireWithMessageBody(messageKey);
        } else {
          stateWriter.appendFollowUpEvent(messageKey, EXPIRED, emptyDeleteMessageCommand);
          expiredMessagesCount++;
        }
      } catch (final ExceededBatchRecordSizeException exceededBatchRecordSizeException) {
        LOG.warn(
            "Expected to expire messages in a batch, but exceeded the resulting batch size after expiring {} out of {} messages. "
                + "Try using a lower Message TTL Checker's batch limit.",
            expiredMessagesCount,
            totalMessagesCount);
        break;
      }
    }

    if (appendMessageBodyOnExpired && expiredMessagesCount == 0) {
      rejectionWriter.appendRejection(
          record,
          RejectionType.NOT_FOUND,
          String.format(
              "Expected to expire %d messages in a batch, but none of the messages were found in the state.",
              totalMessagesCount));
    }
  }

  /**
   * Expires a message and appends the message body to the follow-up event.
   *
   * @param messageKey the key of the message to expire
   * @return the number of messages expired (1 if successful, 0 if not found)
   */
  private int expireWithMessageBody(final long messageKey) {
    final StoredMessage persistedMessage = messageState.getMessage(messageKey);
    if (persistedMessage != null) {
      stateWriter.appendFollowUpEvent(messageKey, EXPIRED, persistedMessage.getMessage());
      return 1;
    } else {
      // the message was expired before processing it here, so we can skip it
      LOG.warn(
          "Expected to expire messages in a batch, but message with key {} was not found.",
          messageKey);
      return 0;
    }
  }
}
