/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics.usage;

import static io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics.TIME_NOT_SET;
import static io.camunda.zeebe.util.HashUtil.getStringHashValue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsageMetricExportProcessorTest {
  private static final long ASSIGNEE_HASH_1 = getStringHashValue("assignee1");
  private static final String TENANT_1 = "tenant1";
  private MutableUsageMetricState state;
  private StateWriter stateWriter;
  private UsageMetricExportProcessor processor;
  private TypedRecord<UsageMetricRecord> record;
  private InstantSource clock;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    state = mock(MutableUsageMetricState.class);
    record = mock(TypedRecord.class);
    clock = mock(InstantSource.class);
    final UsageMetricRecord recordValue = new UsageMetricRecord();
    final var keyGenerator = mock(KeyGenerator.class);
    final var writers = mock(Writers.class);

    when(writers.state()).thenReturn(stateWriter);
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true);
    when(keyGenerator.nextKey()).thenReturn(1L);
    when(record.getValue()).thenReturn(recordValue);
    when(clock.millis()).thenReturn(2L);

    processor = new UsageMetricExportProcessor(state, writers, keyGenerator, clock);
  }

  private DirectBuffer toDirectBuffer(final Object data) {
    return BufferUtil.wrapArray(MsgPackConverter.convertToMsgPack(data));
  }

  @Test
  void shouldAppendExportedNoneEventWhenBucketNull() {
    // given
    when(state.getActiveBucket()).thenReturn(null);

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verifyAppendFollowUpNoneEvent(2L);
  }

  @Test
  void shouldAppendExportedNoneEventWhenBucketNotInitialized() {
    // given
    when(state.getActiveBucket())
        .thenReturn(new PersistedUsageMetrics().setTenantRPIMap(Map.of(TENANT_1, 10L)));

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verifyAppendFollowUpNoneEvent(2L);
  }

  @Test
  void shouldAppendExportedEventRPI() {
    // given
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics()
                .setFromTime(1)
                .setToTime(10)
                .setTenantRPIMap(Map.of(TENANT_1, 10L)));
    when(clock.millis()).thenReturn(3L);

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter)
        .appendFollowUpEvent(
            1L,
            UsageMetricIntent.EXPORTED,
            new UsageMetricRecord()
                .setIntervalType(IntervalType.ACTIVE)
                .setEventType(EventType.RPI)
                .setResetTime((long) TIME_NOT_SET)
                .setStartTime(1L)
                .setEndTime(3L)
                .setCounterValues(toDirectBuffer(Map.of(TENANT_1, 10L)))
                .setSetValues(toDirectBuffer(Map.of())));
    verifyAppendFollowUpNoneEvent(3L);
  }

  @Test
  void shouldAppendExportedEventEDI() {
    // given
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics()
                .setFromTime(1)
                .setToTime(10)
                .setTenantEDIMap(Map.of(TENANT_1, 10L)));

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter)
        .appendFollowUpEvent(
            1L,
            UsageMetricIntent.EXPORTED,
            new UsageMetricRecord()
                .setIntervalType(IntervalType.ACTIVE)
                .setEventType(EventType.EDI)
                .setResetTime((long) TIME_NOT_SET)
                .setStartTime(1L)
                .setEndTime(2L)
                .setCounterValues(toDirectBuffer(Map.of(TENANT_1, 10L)))
                .setSetValues(toDirectBuffer(Map.of())));
    verifyAppendFollowUpNoneEvent(2L);
  }

  @Test
  void shouldAppendExportedEventTU() {
    // given
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics()
                .setFromTime(1)
                .setToTime(10)
                .setTenantTUMap(Map.of(TENANT_1, Set.of(ASSIGNEE_HASH_1))));
    when(clock.millis()).thenReturn(4L);

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter)
        .appendFollowUpEvent(
            1L,
            UsageMetricIntent.EXPORTED,
            new UsageMetricRecord()
                .setIntervalType(IntervalType.ACTIVE)
                .setEventType(EventType.TU)
                .setResetTime((long) TIME_NOT_SET)
                .setStartTime(1L)
                .setEndTime(4L)
                .setCounterValues(toDirectBuffer(Map.of()))
                .setSetValues(toDirectBuffer(Map.of(TENANT_1, Set.of(ASSIGNEE_HASH_1)))));
    verifyAppendFollowUpNoneEvent(4L);
  }

  @Test
  void shouldAppendMixedExportedEvents() {
    // given
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics()
                .setFromTime(1)
                .setToTime(10)
                .setTenantRPIMap(Map.of(TENANT_1, 10L))
                .setTenantEDIMap(Map.of(TENANT_1, 10L))
                .setTenantTUMap(Map.of(TENANT_1, Set.of(ASSIGNEE_HASH_1))));
    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter)
        .appendFollowUpEvent(
            1L,
            UsageMetricIntent.EXPORTED,
            new UsageMetricRecord()
                .setIntervalType(IntervalType.ACTIVE)
                .setEventType(EventType.RPI)
                .setResetTime((long) TIME_NOT_SET)
                .setStartTime(1L)
                .setEndTime(2L)
                .setCounterValues(toDirectBuffer(Map.of(TENANT_1, 10)))
                .setSetValues(toDirectBuffer(Map.of())));
    verify(stateWriter)
        .appendFollowUpEvent(
            1L,
            UsageMetricIntent.EXPORTED,
            new UsageMetricRecord()
                .setIntervalType(IntervalType.ACTIVE)
                .setEventType(EventType.EDI)
                .setResetTime((long) TIME_NOT_SET)
                .setStartTime(1L)
                .setEndTime(2L)
                .setCounterValues(toDirectBuffer(Map.of(TENANT_1, 10)))
                .setSetValues(toDirectBuffer(Map.of())));
    verify(stateWriter)
        .appendFollowUpEvent(
            1L,
            UsageMetricIntent.EXPORTED,
            new UsageMetricRecord()
                .setIntervalType(IntervalType.ACTIVE)
                .setEventType(EventType.TU)
                .setResetTime((long) TIME_NOT_SET)
                .setStartTime(1L)
                .setEndTime(2L)
                .setCounterValues(toDirectBuffer(Map.of()))
                .setSetValues(toDirectBuffer(Map.of(TENANT_1, Set.of(ASSIGNEE_HASH_1)))));
    verifyAppendFollowUpNoneEvent(2);
  }

  private void verifyAppendFollowUpNoneEvent(final long resetTime) {
    verify(stateWriter)
        .appendFollowUpEvent(
            1L,
            UsageMetricIntent.EXPORTED,
            new UsageMetricRecord()
                .setIntervalType(IntervalType.ACTIVE)
                .setEventType(EventType.NONE)
                .setResetTime(resetTime)
                .setStartTime((long) TIME_NOT_SET)
                .setEndTime((long) TIME_NOT_SET)
                .setCounterValues(toDirectBuffer(Map.of()))
                .setSetValues(toDirectBuffer(Map.of())));
  }
}
