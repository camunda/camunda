/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.TestOtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUsageMetricRecordValue;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsageMetricHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private InMemoryLogRecordExporter memoryExporter;
  private UsageMetricHandler handler;

  @BeforeEach
  void setUp() {
    memoryExporter = InMemoryLogRecordExporter.create();
    handler = new UsageMetricHandler(TestOtelSdkManager.inMemory(memoryExporter));
  }

  @Test
  void shouldEmitRpiEventWithSummedCounterValues() {
    // given
    final var record = usageMetricRecord(UsageMetricRecordValue.EventType.RPI, 10L, 20L);

    // when
    handler.handle(typed(record));

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsEntry(
                        AnalyticsAttributes.EVENT_NAME,
                        AnalyticsAttributes.EVENT_USAGE_METRIC_EXPORTED)
                    .containsEntry(AnalyticsAttributes.USAGE_METRIC_EVENT_TYPE, "RPI")
                    .containsEntry(AnalyticsAttributes.USAGE_METRIC_COUNT, 30L)
                    .containsEntry(AnalyticsAttributes.USAGE_METRIC_INTERVAL_START, 1000L)
                    .containsEntry(AnalyticsAttributes.USAGE_METRIC_INTERVAL_END, 2000L));
  }

  @Test
  void shouldEmitEdiEventWithSummedCounterValues() {
    // given
    final var record = usageMetricRecord(UsageMetricRecordValue.EventType.EDI, 5L, 15L);

    // when
    handler.handle(typed(record));

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsEntry(AnalyticsAttributes.USAGE_METRIC_EVENT_TYPE, "EDI")
                    .containsEntry(AnalyticsAttributes.USAGE_METRIC_COUNT, 20L));
  }

  @Test
  void shouldEmitTuEventWithSetSizes() {
    // given
    final var value =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(UsageMetricRecordValue.EventType.TU)
            .withSetValues(Map.of("tenant-a", Set.of(1L, 2L), "tenant-b", Set.of(3L)))
            .withStartTime(1000L)
            .withEndTime(2000L)
            .build();
    final var record =
        FACTORY.generateRecord(
            ValueType.USAGE_METRIC,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(UsageMetricIntent.EXPORTED)
                    .withValue(value));

    // when
    handler.handle(typed(record));

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsEntry(AnalyticsAttributes.USAGE_METRIC_COUNT, 3L));
  }

  @Test
  void shouldSkipNoneEventType() {
    // given
    final var value =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(UsageMetricRecordValue.EventType.NONE)
            .build();
    final var record =
        FACTORY.generateRecord(
            ValueType.USAGE_METRIC,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(UsageMetricIntent.EXPORTED)
                    .withValue(value));

    // when
    handler.handle(typed(record));

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems()).isEmpty();
  }

  // -- helpers --

  private Record<?> usageMetricRecord(
      final UsageMetricRecordValue.EventType eventType,
      final long tenantACount,
      final long tenantBCount) {
    final var value =
        ImmutableUsageMetricRecordValue.builder()
            .withEventType(eventType)
            .withCounterValues(Map.of("tenant-a", tenantACount, "tenant-b", tenantBCount))
            .withStartTime(1000L)
            .withEndTime(2000L)
            .build();
    return FACTORY.generateRecord(
        ValueType.USAGE_METRIC,
        r ->
            r.withRecordType(RecordType.EVENT)
                .withIntent(UsageMetricIntent.EXPORTED)
                .withValue(value));
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }
}
