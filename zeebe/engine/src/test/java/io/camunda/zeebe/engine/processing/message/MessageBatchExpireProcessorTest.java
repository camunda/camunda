/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.common.processing.message.MessageBatchExpireProcessor;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.common.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.common.state.immutable.MessageState;
import io.camunda.zeebe.engine.common.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;
import org.junit.Test;
import org.mockito.Mockito;

public final class MessageBatchExpireProcessorTest {

  private final StateWriter stateWriter = Mockito.mock(StateWriter.class);
  private final TypedRejectionWriter rejectionWriter = Mockito.mock(TypedRejectionWriter.class);
  private final MessageState messageState = Mockito.mock(MessageState.class);

  @Test
  public void shouldStopProcessingWhenExceedingBatchLimit() {

    // given
    final var messageBatchRecord =
        new MessageBatchRecord()
            .addMessageKey(1)
            .addMessageKey(2)
            .addMessageKey(3)
            .addMessageKey(4);

    doReturn(mock(StoredMessage.class)).when(messageState).getMessage(1);
    doReturn(mock(StoredMessage.class)).when(messageState).getMessage(2);
    doReturn(mock(StoredMessage.class)).when(messageState).getMessage(3);
    doNothing().when(stateWriter).appendFollowUpEvent(eq(1L), any(), any());
    doNothing().when(stateWriter).appendFollowUpEvent(eq(2L), any(), any());

    final var exceededBatchRecordSizeException =
        new ExceededBatchRecordSizeException(mock(RecordBatchEntry.class), 10, 1, 1);
    doThrow(exceededBatchRecordSizeException)
        .when(stateWriter)
        .appendFollowUpEvent(eq(3L), any(), any());

    // when
    final var messageBatchExpireProcessor = createProcessor(false);
    messageBatchExpireProcessor.processRecord(
        new UnwrittenRecord(-1, 1, messageBatchRecord, new RecordMetadata()));

    // then
    verify(stateWriter, times(3)).appendFollowUpEvent(anyLong(), any(), any());
  }

  @Test
  public void shouldExportWithMessageBody() {
    // given
    final var messageBatchRecord = new MessageBatchRecord().addMessageKey(1).addMessageKey(2);

    doReturn(
            new StoredMessage()
                .setMessage(
                    new MessageRecord()
                        .setName("message1")
                        .setCorrelationKey("correlationKey1")
                        .setTimeToLive(100L)))
        .when(messageState)
        .getMessage(1L);
    doReturn(
            new StoredMessage()
                .setMessage(
                    new MessageRecord()
                        .setName("message2")
                        .setCorrelationKey("correlationKey2")
                        .setTimeToLive(101L)))
        .when(messageState)
        .getMessage(2L);
    doNothing().when(stateWriter).appendFollowUpEvent(eq(1L), any(), any());
    doNothing().when(stateWriter).appendFollowUpEvent(eq(2L), any(), any());

    // when
    final var messageBatchExpireProcessor = createProcessor(true);
    messageBatchExpireProcessor.processRecord(
        new UnwrittenRecord(-1, 1, messageBatchRecord, new RecordMetadata()));

    // then
    verify(stateWriter, times(1))
        .appendFollowUpEvent(
            eq(1L),
            eq(MessageIntent.EXPIRED),
            argThat(
                record -> {
                  final var messageRecord = (MessageRecord) record;
                  return messageRecord.getName().equals("message1")
                      && messageRecord.getCorrelationKey().equals("correlationKey1")
                      && messageRecord.getTimeToLive() == 100L;
                }));

    verify(stateWriter, times(1))
        .appendFollowUpEvent(
            eq(2L),
            eq(MessageIntent.EXPIRED),
            argThat(
                record -> {
                  final var messageRecord = (MessageRecord) record;
                  return messageRecord.getName().equals("message2")
                      && messageRecord.getCorrelationKey().equals("correlationKey2")
                      && messageRecord.getTimeToLive() == 101L;
                }));
  }

  @Test
  public void shouldSkipNotFoundMessages() {
    // given
    final var messageBatchRecord = new MessageBatchRecord().addMessageKey(1).addMessageKey(2);

    doReturn(
            new StoredMessage()
                .setMessage(
                    new MessageRecord()
                        .setName("message1")
                        .setCorrelationKey("correlationKey1")
                        .setTimeToLive(100L)))
        .when(messageState)
        .getMessage(1L);
    doReturn(null).when(messageState).getMessage(2L);
    doNothing().when(stateWriter).appendFollowUpEvent(eq(1L), any(), any());

    // when
    final var messageBatchExpireProcessor = createProcessor(true);
    messageBatchExpireProcessor.processRecord(
        new UnwrittenRecord(-1, 1, messageBatchRecord, new RecordMetadata()));

    // then
    verify(stateWriter, times(1))
        .appendFollowUpEvent(
            eq(1L),
            eq(MessageIntent.EXPIRED),
            argThat(
                record -> {
                  final var messageRecord = (MessageRecord) record;
                  return messageRecord.getName().equals("message1")
                      && messageRecord.getCorrelationKey().equals("correlationKey1")
                      && messageRecord.getTimeToLive() == 100L;
                }));

    verify(stateWriter, times(0)).appendFollowUpEvent(eq(2L), eq(MessageIntent.EXPIRED), any());
  }

  @Test
  public void shouldRejectIfNoMessagesFound() {
    // given
    final var messageBatchRecord = new MessageBatchRecord().addMessageKey(1).addMessageKey(2);

    doReturn(
            new StoredMessage()
                .setMessage(
                    new MessageRecord()
                        .setName("message1")
                        .setCorrelationKey("correlationKey1")
                        .setTimeToLive(100L)))
        .when(messageState)
        .getMessage(1L);
    doReturn(null).when(messageState).getMessage(1L);
    doReturn(null).when(messageState).getMessage(2L);

    // when
    final UnwrittenRecord record =
        new UnwrittenRecord(-1, 1, messageBatchRecord, new RecordMetadata());
    final var messageBatchExpireProcessor = createProcessor(true);
    messageBatchExpireProcessor.processRecord(record);

    // then
    verify(rejectionWriter, times(1))
        .appendRejection(
            record,
            RejectionType.NOT_FOUND,
            "Expected to expire 2 messages in a batch, but none of the messages were found in the state.");
    verify(stateWriter, times(0)).appendFollowUpEvent(eq(1L), eq(MessageIntent.EXPIRED), any());
    verify(stateWriter, times(0)).appendFollowUpEvent(eq(2L), eq(MessageIntent.EXPIRED), any());
  }

  private MessageBatchExpireProcessor createProcessor(final boolean appendMessageBodyOnExpired) {
    return new MessageBatchExpireProcessor(
        stateWriter, rejectionWriter, messageState, appendMessageBodyOnExpired);
  }
}
