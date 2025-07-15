/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.metrics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UsageMetricsExportProcessorTest {

  private MutableUsageMetricState state;
  private StateWriter stateWriter;
  private UsageMetricsExportProcessor processor;
  private TypedRecord<UsageMetricRecord> record;
  private ArgumentCaptor<UsageMetricRecord> recordArgumentCaptor;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    state = mock(MutableUsageMetricState.class);
    record = mock(TypedRecord.class);
    final UsageMetricRecord recordValue = new UsageMetricRecord();
    final var keyGenerator = mock(KeyGenerator.class);
    final var writers = mock(Writers.class);

    when(writers.state()).thenReturn(stateWriter);
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true);
    when(keyGenerator.nextKey()).thenReturn(1L);
    when(record.getTimestamp()).thenReturn(2L);
    when(record.getValue()).thenReturn(recordValue);

    recordArgumentCaptor = ArgumentCaptor.forClass(UsageMetricRecord.class);

    processor = new UsageMetricsExportProcessor(state, writers, keyGenerator);
  }

  @Test
  void shouldAppendExportedNoneEventWhenBucketNull() {
    // given
    when(state.getActiveBucket()).thenReturn(null);

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(1L), eq(UsageMetricIntent.EXPORTED), recordArgumentCaptor.capture());
    final var actual = recordArgumentCaptor.getValue();
    assertThat(actual.getIntervalType()).isEqualTo(IntervalType.ACTIVE);
    assertThat(actual.getEventType()).isEqualTo(EventType.NONE);
    assertThat(actual.getResetTime()).isEqualTo(2);
    assertThat(actual.getStartTime()).isEqualTo(-1);
    assertThat(actual.getEndTime()).isEqualTo(-1);
    assertThat(actual.getValues()).isEmpty();
  }

  @Test
  void shouldAppendExportedEvent() {
    // given
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics()
                .setFromTime(1)
                .setToTime(10)
                .setTenantRPIMap(Map.of("tenant1", 10L)));

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(1L), eq(UsageMetricIntent.EXPORTED), recordArgumentCaptor.capture());
    final var actual = recordArgumentCaptor.getValue();
    assertThat(actual.getIntervalType()).isEqualTo(IntervalType.ACTIVE);
    assertThat(actual.getEventType()).isEqualTo(EventType.RPI);
    assertThat(actual.getResetTime()).isEqualTo(2);
    assertThat(actual.getStartTime()).isEqualTo(1);
    assertThat(actual.getEndTime()).isEqualTo(10);
    assertThat(actual.getValues()).isEqualTo(Map.of("tenant1", 10L));
  }

  @Test
  void shouldAppendMultipleExportedEventsWhenRecordTooLong() {
    // given
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true);
    doAnswer(i -> i.getArgument(0, Integer.class) <= 100)
        .when(stateWriter)
        .canWriteEventOfLength(anyInt());
    final var tenantRPIMap = new HashMap<String, Long>();
    for (int i = 0; i < 5; i++) {
      tenantRPIMap.put("tenant" + i, (long) i);
    }
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics().setFromTime(1).setToTime(10).setTenantRPIMap(tenantRPIMap));

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter, times(3))
        .appendFollowUpEvent(
            eq(1L), eq(UsageMetricIntent.EXPORTED), recordArgumentCaptor.capture());
    final List<UsageMetricRecord> actual = recordArgumentCaptor.getAllValues();
    assertThat(actual).hasSize(3);
    assertThat(actual)
        .extracting(UsageMetricRecord::getIntervalType)
        .containsOnly(IntervalType.ACTIVE);
    assertThat(actual).extracting(UsageMetricRecord::getEventType).containsOnly(EventType.RPI);
    assertThat(actual).extracting(UsageMetricRecord::getResetTime).containsOnly(2L);
    assertThat(actual).extracting(UsageMetricRecord::getStartTime).containsOnly(1L);
    assertThat(actual).extracting(UsageMetricRecord::getEndTime).containsOnly(10L);
    assertThat(actual)
        .extracting(UsageMetricRecord::getValues)
        .containsOnly(
            Map.of("tenant0", 0L, "tenant1", 1L),
            Map.of("tenant2", 2L, "tenant3", 3L),
            Map.of("tenant4", 4L));
  }

  @Test
  void shouldAppendExportedEventEDI() {
    // given
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics()
                .setFromTime(1)
                .setToTime(10)
                .setTenantEDIMap(Map.of("tenant1", 10L)));
    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter)
        .appendFollowUpEvent(
            eq(1L), eq(UsageMetricIntent.EXPORTED), recordArgumentCaptor.capture());
    final var actual = recordArgumentCaptor.getValue();
    assertThat(actual.getIntervalType()).isEqualTo(IntervalType.ACTIVE);
    assertThat(actual.getEventType()).isEqualTo(EventType.EDI);
    assertThat(actual.getResetTime()).isEqualTo(2);
    assertThat(actual.getStartTime()).isEqualTo(1);
    assertThat(actual.getEndTime()).isEqualTo(10);
    assertThat(actual.getValues()).isEqualTo(Map.of("tenant1", 10L));
  }

  @Test
  void shouldAppendMixedExportedEvents() {
    // given
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics()
                .setFromTime(1)
                .setToTime(10)
                .setTenantRPIMap(Map.of("tenant1", 10L))
                .setTenantEDIMap(Map.of("tenant1", 10L)));
    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter, times(2))
        .appendFollowUpEvent(
            eq(1L), eq(UsageMetricIntent.EXPORTED), recordArgumentCaptor.capture());
    final List<UsageMetricRecord> actual = recordArgumentCaptor.getAllValues();
    assertThat(actual).hasSize(2);
    assertThat(actual)
        .extracting(UsageMetricRecord::getIntervalType)
        .containsOnly(IntervalType.ACTIVE);
    assertThat(actual).extracting(UsageMetricRecord::getResetTime).containsOnly(2L);
    assertThat(actual).extracting(UsageMetricRecord::getStartTime).containsOnly(1L);
    assertThat(actual).extracting(UsageMetricRecord::getEndTime).containsOnly(10L);
    assertThat(actual)
        .extracting(UsageMetricRecord::getEventType)
        .contains(EventType.RPI, EventType.EDI);
    assertThat(actual)
        .extracting(UsageMetricRecord::getValues)
        .containsOnly(Map.of("tenant1", 10L));
  }

  @Test
  void shouldAppendMultipleExportedEventsWhenRecordTooLongEDI() {
    // given
    when(stateWriter.canWriteEventOfLength(anyInt())).thenReturn(true);
    doAnswer(i -> i.getArgument(0, Integer.class) <= 100)
        .when(stateWriter)
        .canWriteEventOfLength(anyInt());
    final var tenantEDIMap = new HashMap<String, Long>();
    for (int i = 0; i < 5; i++) {
      tenantEDIMap.put("tenant" + i, (long) i);
    }
    when(state.getActiveBucket())
        .thenReturn(
            new PersistedUsageMetrics().setFromTime(1).setToTime(10).setTenantEDIMap(tenantEDIMap));

    // when
    processor.processRecord(record);

    // then
    verify(state).getActiveBucket();
    verify(stateWriter, times(3))
        .appendFollowUpEvent(
            eq(1L), eq(UsageMetricIntent.EXPORTED), recordArgumentCaptor.capture());
    final List<UsageMetricRecord> actual = recordArgumentCaptor.getAllValues();
    assertThat(actual).hasSize(3);
    assertThat(actual)
        .extracting(UsageMetricRecord::getIntervalType)
        .containsOnly(IntervalType.ACTIVE);
    assertThat(actual).extracting(UsageMetricRecord::getEventType).containsOnly(EventType.EDI);
    assertThat(actual).extracting(UsageMetricRecord::getResetTime).containsOnly(2L);
    assertThat(actual).extracting(UsageMetricRecord::getStartTime).containsOnly(1L);
    assertThat(actual).extracting(UsageMetricRecord::getEndTime).containsOnly(10L);
    assertThat(actual)
        .extracting(UsageMetricRecord::getValues)
        .containsOnly(
            Map.of("tenant0", 0L, "tenant1", 1L),
            Map.of("tenant2", 2L, "tenant3", 3L),
            Map.of("tenant4", 4L));
  }
}
