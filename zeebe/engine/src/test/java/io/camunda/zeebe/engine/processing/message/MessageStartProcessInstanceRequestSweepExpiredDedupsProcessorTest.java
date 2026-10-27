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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.MessageCorrelationMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState;
import io.camunda.zeebe.engine.state.immutable.MessageStartProcessInstanceDedupState.ExpiredEntryVisitor;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartProcessInstanceRequestRecord;
import io.camunda.zeebe.protocol.record.intent.MessageStartProcessInstanceRequestIntent;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.InstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for the swept-dedup metric (M11) recorded by the expired-dedup sweeper on {@code P_B}.
 * The processor deletes past-deadline dedup rows in bounded batches; the metric lets an operator
 * see how many entries each sweep cycle reclaims.
 */
public final class MessageStartProcessInstanceRequestSweepExpiredDedupsProcessorTest {

  private static final long RECORD_KEY = 42L;
  private static final int BATCH_LIMIT = 64;
  private static final String SWEPT_METRIC =
      "zeebe.message.start.cross.partition.dedup.swept.total";

  private StateWriter mockStateWriter;
  private TypedCommandWriter mockCommandWriter;
  private MessageStartProcessInstanceDedupState mockDedupState;
  private TypedRecord<MessageStartProcessInstanceRequestRecord> mockRecord;
  private SimpleMeterRegistry meterRegistry;
  private MessageStartProcessInstanceRequestSweepExpiredDedupsProcessor processor;

  @BeforeEach
  void setUp() {
    mockStateWriter = mock(StateWriter.class);
    mockCommandWriter = mock(TypedCommandWriter.class);
    mockDedupState = mock(MessageStartProcessInstanceDedupState.class);
    mockRecord = mock(TypedRecord.class);
    when(mockRecord.getKey()).thenReturn(RECORD_KEY);

    meterRegistry = new SimpleMeterRegistry();
    processor =
        new MessageStartProcessInstanceRequestSweepExpiredDedupsProcessor(
            mockStateWriter,
            mockCommandWriter,
            mockDedupState,
            BATCH_LIMIT,
            InstantSource.system(),
            new MessageCorrelationMetrics(meterRegistry));
  }

  @Test
  void shouldRecordSweptCountForDeletedEntries() {
    // given three past-deadline dedup rows to visit
    visitEntries(3);

    // when
    processor.processRecord(mockRecord);

    // then the swept counter (M11) is incremented once per deleted entry
    assertThat(meterRegistry.get(SWEPT_METRIC).counter().count()).isEqualTo(3.0);
  }

  @Test
  void shouldNotRecordWhenNothingSwept() {
    // given no past-deadline dedup rows
    visitEntries(0);

    // when
    processor.processRecord(mockRecord);

    // then the swept counter (M11) stays at zero
    assertThat(meterRegistry.get(SWEPT_METRIC).counter().count()).isEqualTo(0.0);
  }

  @Test
  void shouldMarkExpiredDedupDeletedEventsAsNotOrdinalControlled() {
    // given one past-deadline dedup row
    visitEntries(1);

    // when
    processor.processRecord(mockRecord);

    // then the deletion event belongs to no instance
    final var eventCaptor = ArgumentCaptor.forClass(MessageStartProcessInstanceRequestRecord.class);
    verify(mockStateWriter)
        .appendFollowUpEvent(
            eq(RECORD_KEY),
            eq(MessageStartProcessInstanceRequestIntent.EXPIRED_DEDUP_DELETED),
            eventCaptor.capture());
    assertThat(eventCaptor.getValue().getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  void shouldMarkFollowUpSweepCommandAsNotOrdinalControlled() {
    // given more past-deadline rows than fit in one batch
    when(mockDedupState.visitExpiredEntries(anyLong(), any())).thenReturn(true);

    // when
    processor.processRecord(mockRecord);

    // then the follow-up trigger belongs to no instance
    final var commandCaptor =
        ArgumentCaptor.forClass(MessageStartProcessInstanceRequestRecord.class);
    verify(mockCommandWriter)
        .appendFollowUpCommand(
            eq(RECORD_KEY),
            eq(MessageStartProcessInstanceRequestIntent.SWEEP_EXPIRED_DEDUPS),
            commandCaptor.capture());
    assertThat(commandCaptor.getValue().getStorageOrdinal())
        .isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  private void visitEntries(final int count) {
    when(mockDedupState.visitExpiredEntries(anyLong(), any()))
        .thenAnswer(
            invocation -> {
              final ExpiredEntryVisitor visitor = invocation.getArgument(1);
              for (int i = 0; i < count; i++) {
                visitor.visit(100L + i, 200L + i);
              }
              return false;
            });
  }
}
