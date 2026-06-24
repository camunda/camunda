/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the reply processors that handle cross-partition responses on P_K. The STARTED
 * reply processor is the cross-partition arm's behavioural payload: it writes the CORRELATED event,
 * the STARTED bookkeeping event, and the EXPIRED follow-up that removes the buffered message; the
 * rejection processors only write the matching follow-up event for the cleanup applier.
 */
public final class MessageStartProcessInstanceReplyProcessorTest {

  private static final long MESSAGE_KEY = 100L;
  private static final long PROCESS_DEFINITION_KEY = 200L;
  private static final long PROCESS_INSTANCE_KEY = 300L;
  private static final long MESSAGE_START_EVENT_SUBSCRIPTION_KEY = 400L;
  private static final String BPMN_PROCESS_ID = "process";
  private static final String CORRELATION_KEY = "ck-1";

  private StateWriter mockStateWriter;
  private MessageState mockMessageState;

  @BeforeEach
  void setUp() {
    mockStateWriter = mock(StateWriter.class);
    mockMessageState = mock(MessageState.class);
  }

  @SuppressWarnings("unchecked")
  private TypedRecord<MessageStartProcessInstanceRequestRecord> createMockReplyRecord() {
    final var record = mock(TypedRecord.class);
    when(record.getKey()).thenReturn(999L);
    when(record.getValue())
        .thenReturn(
            new MessageStartProcessInstanceRequestRecord()
                .setMessageKey(MESSAGE_KEY)
                .setMessageName("msg")
                .setCorrelationKey(CORRELATION_KEY)
                .setBusinessId("bid")
                .setProcessDefinitionKey(PROCESS_DEFINITION_KEY)
                .setBpmnProcessId(BPMN_PROCESS_ID)
                .setStartEventId("start")
                .setMessageStartEventSubscriptionKey(MESSAGE_START_EVENT_SUBSCRIPTION_KEY)
                .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
                .setTenantId("<default>"));
    return record;
  }

  @Nested
  class StartReplyProcessor {

    @Test
    void shouldWriteCorrelatedStartedAndExpiredFollowUpEvents() {
      // given a fresh reply, no prior correlation, and the buffered message still present
      final var processor =
          new MessageStartProcessInstanceRequestStartProcessor(mockStateWriter, mockMessageState);
      final var record = createMockReplyRecord();
      when(mockMessageState.existMessageCorrelation(eq(MESSAGE_KEY), any())).thenReturn(false);
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(createStoredMessage());

      // when
      processor.processRecord(record);

      // then
      final var subscriptionCaptor =
          ArgumentCaptor.forClass(MessageStartEventSubscriptionRecord.class);
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(MESSAGE_START_EVENT_SUBSCRIPTION_KEY),
              eq(MessageStartEventSubscriptionIntent.CORRELATED),
              subscriptionCaptor.capture());
      assertThat(subscriptionCaptor.getValue().getMessageKey()).isEqualTo(MESSAGE_KEY);
      assertThat(subscriptionCaptor.getValue().getBpmnProcessId()).isEqualTo(BPMN_PROCESS_ID);
      assertThat(subscriptionCaptor.getValue().getProcessInstanceKey())
          .isEqualTo(PROCESS_INSTANCE_KEY);

      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(record.getKey()), eq(MessageStartProcessInstanceRequestIntent.STARTED), any());

      verify(mockStateWriter)
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageIntent.EXPIRED), any());
    }

    @Test
    void shouldSkipCorrelatedWhenAlreadyCorrelated() {
      // given a retried reply: the correlation marker is already present
      final var processor =
          new MessageStartProcessInstanceRequestStartProcessor(mockStateWriter, mockMessageState);
      final var record = createMockReplyRecord();
      when(mockMessageState.existMessageCorrelation(eq(MESSAGE_KEY), any())).thenReturn(true);
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(null);

      // when
      processor.processRecord(record);

      // then
      verify(mockStateWriter, never())
          .appendFollowUpEvent(
              eq(MESSAGE_START_EVENT_SUBSCRIPTION_KEY),
              eq(MessageStartEventSubscriptionIntent.CORRELATED),
              any());
      // STARTED is still emitted so the pending-ask cleanup runs.
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(record.getKey()), eq(MessageStartProcessInstanceRequestIntent.STARTED), any());
    }

    @Test
    void shouldSkipExpiredWhenBufferedMessageGone() {
      // given the buffered message no longer exists (TTL already fired, or a previous reply's
      // EXPIRED applier already removed it)
      final var processor =
          new MessageStartProcessInstanceRequestStartProcessor(mockStateWriter, mockMessageState);
      final var record = createMockReplyRecord();
      when(mockMessageState.existMessageCorrelation(eq(MESSAGE_KEY), any())).thenReturn(false);
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(null);

      // when
      processor.processRecord(record);

      // then
      verify(mockStateWriter, never())
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageIntent.EXPIRED), any());
    }

    private StoredMessage createStoredMessage() {
      final var stored = new StoredMessage();
      stored.setMessageKey(MESSAGE_KEY);
      stored.setMessage(
          new MessageRecord()
              .setName("name")
              .setCorrelationKey(CORRELATION_KEY)
              .setTimeToLive(60_000L)
              .setTenantId("<default>"));
      return stored;
    }
  }

  @Nested
  class UniquenessRejectReplyProcessor {

    @Test
    void shouldWriteUniquenessRejectedFollowUpEvent() {
      // given
      final var processor =
          new MessageStartProcessInstanceRequestRejectUniquenessProcessor(mockStateWriter);
      final var record = createMockReplyRecord();

      // when
      processor.processRecord(record);

      // then
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(record.getKey()),
              eq(MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED),
              any());
    }
  }

  @Nested
  class NoSubscriptionRejectReplyProcessor {

    @Test
    void shouldWriteNoSubscriptionRejectedFollowUpEvent() {
      // given
      final var processor =
          new MessageStartProcessInstanceRequestRejectNoSubscriptionProcessor(mockStateWriter);
      final var record = createMockReplyRecord();

      // when
      processor.processRecord(record);

      // then
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(record.getKey()),
              eq(MessageStartProcessInstanceRequestIntent.NO_SUBSCRIPTION_REJECTED),
              any());
    }
  }
}
