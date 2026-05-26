/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the reply processors that handle cross-partition responses on P_K. Each processor
 * should write the follow-up event; the actual state changes happen in the appliers.
 */
public final class MessageStartProcessInstanceReplyProcessorTest {

  private StateWriter mockStateWriter;

  @BeforeEach
  void setUp() {
    mockStateWriter = mock(StateWriter.class);
  }

  @Nested
  class StartReplyProcessor {

    @Test
    void shouldWriteStartedFollowUpEvent() {
      // given
      final var processor = new MessageStartProcessInstanceStartReplyProcessor(mockStateWriter);
      final var record = createMockRecord(100L);

      // when
      processor.processRecord(record);

      // then
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(100L), eq(MessageStartProcessInstanceRequestIntent.STARTED), any());
    }
  }

  @Nested
  class UniquenessRejectReplyProcessor {

    @Test
    void shouldWriteUniquenessRejectedFollowUpEvent() {
      // given
      final var processor =
          new MessageStartProcessInstanceUniquenessRejectReplyProcessor(mockStateWriter);
      final var record = createMockRecord(200L);

      // when
      processor.processRecord(record);

      // then
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(200L), eq(MessageStartProcessInstanceRequestIntent.UNIQUENESS_REJECTED), any());
    }
  }

  @Nested
  class NoSubscriptionRejectReplyProcessor {

    @Test
    void shouldWriteNoSubscriptionRejectedFollowUpEvent() {
      // given
      final var processor =
          new MessageStartProcessInstanceNoSubscriptionRejectReplyProcessor(mockStateWriter);
      final var record = createMockRecord(300L);

      // when
      processor.processRecord(record);

      // then
      verify(mockStateWriter)
          .appendFollowUpEvent(
              eq(300L),
              eq(MessageStartProcessInstanceRequestIntent.NO_SUBSCRIPTION_REJECTED),
              any());
    }
  }

  @SuppressWarnings("unchecked")
  private TypedRecord<MessageStartProcessInstanceRequestRecord> createMockRecord(final long key) {
    final var record = mock(TypedRecord.class);
    when(record.getKey()).thenReturn(key);
    when(record.getValue()).thenReturn(new MessageStartProcessInstanceRequestRecord());
    return record;
  }
}
