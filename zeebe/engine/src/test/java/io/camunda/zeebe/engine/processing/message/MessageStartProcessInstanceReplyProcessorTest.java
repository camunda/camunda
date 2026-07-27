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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.state.immutable.MessageCorrelationState;
import io.camunda.zeebe.engine.state.immutable.MessageState;
import io.camunda.zeebe.engine.state.message.RequestData;
import io.camunda.zeebe.engine.state.message.StoredMessage;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
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
  private TypedResponseWriter mockResponseWriter;
  private MessageCorrelationState mockMessageCorrelationState;

  @BeforeEach
  void setUp() {
    mockStateWriter = mock(StateWriter.class);
    mockMessageState = mock(MessageState.class);
    mockResponseWriter = mock(TypedResponseWriter.class);
    mockMessageCorrelationState = mock(MessageCorrelationState.class);
  }

  private MessageStartProcessInstanceRequestStartProcessor startProcessor() {
    return new MessageStartProcessInstanceRequestStartProcessor(
        mockStateWriter, mockResponseWriter, mockMessageState, mockMessageCorrelationState);
  }

  private MessageStartProcessInstanceRequestRejectUniquenessProcessor uniquenessProcessor() {
    return new MessageStartProcessInstanceRequestRejectUniquenessProcessor(
        mockStateWriter, mockResponseWriter, mockMessageCorrelationState, mockMessageState);
  }

  private MessageStartProcessInstanceRequestRejectNoSubscriptionProcessor
      noSubscriptionProcessor() {
    return new MessageStartProcessInstanceRequestRejectNoSubscriptionProcessor(
        mockStateWriter, mockResponseWriter, mockMessageCorrelationState, mockMessageState);
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

  private void givenPendingCorrelateRequest() {
    when(mockMessageCorrelationState.existsRequestDataForMessageKey(MESSAGE_KEY)).thenReturn(true);
    when(mockMessageCorrelationState.getRequestData(MESSAGE_KEY))
        .thenReturn(new RequestData().setRequestIdProp(7L).setRequestStreamIdProp(3));
  }

  private static StoredMessage createStoredMessage() {
    final var stored = new StoredMessage();
    stored.setMessageKey(MESSAGE_KEY);
    stored.setMessage(
        new MessageRecord()
            .setName("name")
            .setCorrelationKey(CORRELATION_KEY)
            .setTimeToLive(-1L)
            .setTenantId("<default>"));
    return stored;
  }

  @Nested
  class StartReplyProcessor {

    @Test
    void shouldWriteCorrelatedStartedAndExpiredFollowUpEvents() {
      // given a fresh reply, no prior correlation, and the buffered message still present
      final var processor = startProcessor();
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
      final var processor = startProcessor();
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
      final var processor = startProcessor();
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
      return MessageStartProcessInstanceReplyProcessorTest.createStoredMessage();
    }

    @Test
    void shouldWriteDeferredCorrelatedResponseWhenOriginatedFromSyncCorrelate() {
      // given a synchronous correlate is awaiting the cross-partition reply
      final var processor = startProcessor();
      final var record = createMockReplyRecord();
      when(mockMessageState.existMessageCorrelation(eq(MESSAGE_KEY), any())).thenReturn(false);
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(createStoredMessage());
      givenPendingCorrelateRequest();

      // when
      processor.processRecord(record);

      // then the deferred CORRELATED event and accepted response are written to the waiting client
      final var correlationCaptor = ArgumentCaptor.forClass(MessageCorrelationRecord.class);
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(MESSAGE_KEY),
              eq(MessageCorrelationIntent.CORRELATED),
              correlationCaptor.capture());
      assertThat(correlationCaptor.getValue().getProcessInstanceKey())
          .isEqualTo(PROCESS_INSTANCE_KEY);
      verify(mockResponseWriter)
          .writeAcceptedResponse(
              eq(MESSAGE_KEY),
              eq(MessageCorrelationIntent.CORRELATED),
              any(),
              eq(ValueType.MESSAGE_CORRELATION),
              eq(7L),
              eq(3));
    }

    @Test
    void shouldNotWriteCorrelateResponseForPublish() {
      // given no pending correlate request (the delegating command was a publish)
      final var processor = startProcessor();
      final var record = createMockReplyRecord();
      when(mockMessageState.existMessageCorrelation(eq(MESSAGE_KEY), any())).thenReturn(false);
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(createStoredMessage());
      when(mockMessageCorrelationState.existsRequestDataForMessageKey(MESSAGE_KEY))
          .thenReturn(false);

      // when
      processor.processRecord(record);

      // then no MessageCorrelation response is produced
      verify(mockStateWriter, never())
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageCorrelationIntent.CORRELATED), any());
      verify(mockResponseWriter, never())
          .writeAcceptedResponse(anyLong(), any(), any(), any(), anyLong(), anyInt());
    }
  }

  @Nested
  class UniquenessRejectReplyProcessor {

    @Test
    void shouldWriteUniquenessRejectedFollowUpEvent() {
      // given
      final var processor = uniquenessProcessor();
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

    @Test
    void shouldWriteDeferredNotCorrelatedResponseAndExpireMessageForSyncCorrelate() {
      // given a synchronous correlate is awaiting the reply and its message is still buffered
      final var processor = uniquenessProcessor();
      final var record = createMockReplyRecord();
      givenPendingCorrelateRequest();
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(createStoredMessage());

      // when
      processor.processRecord(record);

      // then the deferred NOT_CORRELATED event and a NOT_FOUND rejected response are written, the
      // reason names the business ID, and the fire-and-forget correlate message is expired so the
      // pending ask is cleared and no late instance is started
      verify(mockStateWriter)
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageCorrelationIntent.NOT_CORRELATED), any());
      verify(mockResponseWriter)
          .writeRejectedResponse(
              eq(MESSAGE_KEY),
              eq(MessageCorrelationIntent.CORRELATE),
              any(),
              eq(ValueType.MESSAGE_CORRELATION),
              eq(RejectionType.NOT_FOUND),
              contains("business ID 'bid'"),
              eq(7L),
              eq(3));
      verify(mockResponseWriter)
          .writeRejectedResponse(
              anyLong(),
              any(),
              any(),
              any(),
              any(),
              contains("already active"),
              anyLong(),
              anyInt());
      verify(mockStateWriter)
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageIntent.EXPIRED), any());
    }

    @Test
    void shouldNotWriteResponseOrExpireForPublish() {
      // given no pending correlate request (the delegating command was a publish)
      final var processor = uniquenessProcessor();
      final var record = createMockReplyRecord();
      when(mockMessageCorrelationState.existsRequestDataForMessageKey(MESSAGE_KEY))
          .thenReturn(false);

      // when
      processor.processRecord(record);

      // then the buffered publish message is left untouched for the retry mechanism
      verify(mockStateWriter, never())
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageCorrelationIntent.NOT_CORRELATED), any());
      verify(mockStateWriter, never())
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageIntent.EXPIRED), any());
    }
  }

  @Nested
  class NoSubscriptionRejectReplyProcessor {

    @Test
    void shouldWriteNoSubscriptionRejectedFollowUpEvent() {
      // given
      final var processor = noSubscriptionProcessor();
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

    @Test
    void shouldWriteDeferredNotCorrelatedResponseAndExpireMessageForSyncCorrelate() {
      // given a synchronous correlate is awaiting the reply and its message is still buffered
      final var processor = noSubscriptionProcessor();
      final var record = createMockReplyRecord();
      givenPendingCorrelateRequest();
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(createStoredMessage());

      // when
      processor.processRecord(record);

      // then the deferred NOT_CORRELATED event, a NOT_FOUND rejected response, and the message
      // expiry are written
      verify(mockStateWriter)
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageCorrelationIntent.NOT_CORRELATED), any());
      verify(mockResponseWriter)
          .writeRejectedResponse(
              eq(MESSAGE_KEY),
              eq(MessageCorrelationIntent.CORRELATE),
              any(),
              eq(ValueType.MESSAGE_CORRELATION),
              eq(RejectionType.NOT_FOUND),
              contains("none was found"),
              eq(7L),
              eq(3));
      verify(mockStateWriter)
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageIntent.EXPIRED), any());
    }

    @Test
    void shouldNotExpireWhenBufferedMessageGone() {
      // given a sync correlate whose buffered message was already swept by its TTL deadline
      final var processor = noSubscriptionProcessor();
      final var record = createMockReplyRecord();
      givenPendingCorrelateRequest();
      when(mockMessageState.getMessage(MESSAGE_KEY)).thenReturn(null);

      // when
      processor.processRecord(record);

      // then the NOT_CORRELATED response is still written, but no EXPIRED event
      verify(mockStateWriter)
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageCorrelationIntent.NOT_CORRELATED), any());
      verify(mockStateWriter, never())
          .appendFollowUpEvent(eq(MESSAGE_KEY), eq(MessageIntent.EXPIRED), any());
    }
  }
}
