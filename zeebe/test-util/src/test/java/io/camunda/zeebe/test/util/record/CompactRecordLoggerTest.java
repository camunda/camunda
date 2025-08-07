/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompactRecordLoggerTest {

  private CompactRecordLogger compactRecordLogger;

  @BeforeEach
  public void beforeEach() {
    compactRecordLogger = new CompactRecordLogger(List.of());
  }

  @ParameterizedTest(name = "has compact logging for {0}")
  @EnumSource(
      value = ValueType.class,
      // Excluding ValueTypes not yet supported by CompactRecordLogger.
      // They will be covered in follow-up PRs as part of:
      // https://github.com/camunda/camunda/issues/31825.
      mode = EnumSource.Mode.EXCLUDE,
      names = {
        "ESCALATION",
        "IDENTITY_SETUP",
        "SCALE",
        "FORM",
        "PROCESS_INSTANCE_MIGRATION",
        "CHECKPOINT",
        "BATCH_OPERATION_INITIALIZATION"
      })
  public void shouldHaveCompactLoggerForValueType(final ValueType valueType) {
    assertThat(compactRecordLogger.getSupportedValueTypes())
        .as(
            """
                    Expected CompactRecordLogger to support logging for value type '%s'.
                    If you're introducing a new record, make sure to add a compact logger for it.
                """,
            valueType)
        .contains(valueType);
  }

  /**
   * Tests the summarization logic for records via the `summarizeXyz(...)` methods in {@link
   * CompactRecordLogger}.
   */
  @Nested
  class CompactSummaryTest {

    @Test
    void shouldSummarizeUsageMetricsWithCounterCorrectly() {
      // given
      final var logger = new CompactRecordLogger(java.util.List.of());
      final var mockRecord = mock(Record.class);
      final var value = mock(UsageMetricRecordValue.class);

      // when
      when(mockRecord.getValueType()).thenReturn(ValueType.USAGE_METRIC);
      when(mockRecord.getValue()).thenReturn(value);
      when(value.getEventType()).thenReturn(EventType.RPI);
      when(value.getIntervalType()).thenReturn(IntervalType.ACTIVE);
      when(value.getStartTime()).thenReturn(1718000000000L);
      when(value.getEndTime()).thenReturn(1718003600000L);
      when(value.getResetTime()).thenReturn(1718001800000L);
      when(value.getCounterValues()).thenReturn(Map.of("tenant1", 42L, "tenant2", 84L));

      final String result = logger.summarizeUsageMetrics(mockRecord);

      // then
      final String expected =
          "RPI:ACTIVE start[2024-06-10T06:13:20] end[2024-06-10T07:13:20] reset[2024-06-10T06:43:20] metricValues: {tenant1=42, tenant2=84}";
      assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldSummarizeUsageMetricsWithSetCorrectly() {
      // given
      final var logger = new CompactRecordLogger(java.util.List.of());
      final var mockRecord = mock(Record.class);
      final var value = mock(UsageMetricRecordValue.class);

      // when
      when(mockRecord.getValueType()).thenReturn(ValueType.USAGE_METRIC);
      when(mockRecord.getValue()).thenReturn(value);
      when(value.getEventType()).thenReturn(EventType.TU);
      when(value.getIntervalType()).thenReturn(IntervalType.ACTIVE);
      when(value.getStartTime()).thenReturn(1718000000000L);
      when(value.getEndTime()).thenReturn(1718003600000L);
      when(value.getResetTime()).thenReturn(1718001800000L);
      when(value.getSetValues())
          .thenReturn(Map.of("tenant1", Set.of(1234567L, 7654321L), "tenant2", Set.of(9876543L)));

      final String result = logger.summarizeUsageMetrics(mockRecord);

      // then
      assertThat(result)
          .startsWith(
              "TU:ACTIVE start[2024-06-10T06:13:20] end[2024-06-10T07:13:20] reset[2024-06-10T06:43:20] metricValues: {")
          .satisfies(
              r ->
                  assertThat(r)
                      .containsAnyOf("tenant1=[7654321, 1234567]", "tenant1=[1234567, 7654321]")
                      .contains("tenant2=[9876543]"));
    }
  }
}
