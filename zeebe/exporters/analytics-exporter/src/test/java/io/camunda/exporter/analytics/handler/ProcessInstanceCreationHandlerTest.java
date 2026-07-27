/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.INSTANCE_KEY;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.VERSION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Tenant.ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.OtelSdkManager;
import io.camunda.exporter.analytics.TestOtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProcessInstanceCreationHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private InMemoryLogRecordExporter logExporter;
  private InMemoryMetricReader metricReader;
  private ProcessInstanceCreationHandler handler;

  @BeforeEach
  void setUp() {
    logExporter = InMemoryLogRecordExporter.create();
    metricReader = InMemoryMetricReader.create();
    final OtelSdkManager manager =
        TestOtelSdkManager.inMemoryWithMetrics(logExporter, metricReader);
    handler = new ProcessInstanceCreationHandler(manager);
  }

  private static Record<?> piCreatedRecord() {
    return FACTORY.generateRecord(
        ValueType.PROCESS_INSTANCE_CREATION,
        r -> r.withRecordType(RecordType.EVENT).withIntent(ProcessInstanceCreationIntent.CREATED));
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  private static Optional<MetricData> findCounter(final Collection<MetricData> metrics) {
    return metrics.stream()
        .filter(m -> m.getName().equals(AnalyticsAttributes.Metric.PROCESS_INSTANCE_CREATED))
        .findFirst();
  }

  @Nested
  class LogEvent {

    @Test
    void shouldEmitLogEventWithAllAttributes() {
      // given
      final var value =
          ImmutableProcessInstanceCreationRecordValue.builder()
              .from(FACTORY.generateObject(ProcessInstanceCreationRecordValue.class))
              .withBpmnProcessId("my-process")
              .withVersion(3)
              .withProcessDefinitionKey(42L)
              .withProcessInstanceKey(100L)
              .withRootProcessInstanceKey(200L)
              .withTenantId("tenant-a")
              .build();
      final var record =
          FACTORY.generateRecord(
              ValueType.PROCESS_INSTANCE_CREATION,
              r ->
                  r.withRecordType(RecordType.EVENT)
                      .withIntent(ProcessInstanceCreationIntent.CREATED)
                      .withKey(999L)
                      .withValue(value));

      // when
      handler.handle(typed(record));

      // then
      assertThat(logExporter.getFinishedLogRecordItems())
          .singleElement()
          .satisfies(
              logRecord -> {
                assertThat(logRecord.getAttributes().asMap())
                    .containsEntry(
                        AnalyticsAttributes.Event.NAME,
                        AnalyticsAttributes.Event.PROCESS_INSTANCE_CREATED)
                    .containsEntry(BPMN_PROCESS_ID, "my-process")
                    .containsEntry(VERSION, 3L)
                    .containsEntry(AnalyticsAttributes.Process.DEFINITION_KEY, 42L)
                    .containsEntry(INSTANCE_KEY, 100L)
                    .containsEntry(AnalyticsAttributes.Process.ROOT_INSTANCE_KEY, 200L)
                    .containsEntry(ID, "tenant-a")
                    .containsKey(AnalyticsAttributes.Log.POSITION)
                    .containsKey(AnalyticsAttributes.Event.SEQUENCE_NUMBER);

                assertThat(logRecord.getTimestampEpochNanos())
                    .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));
              });
    }
  }

  @Nested
  class MetricCounter {

    @Test
    void shouldAggregateCounterAcrossHandles() {
      // when
      handler.handle(typed(piCreatedRecord()));
      handler.handle(typed(piCreatedRecord()));
      handler.handle(typed(piCreatedRecord()));

      // then
      assertThat(findCounter(metricReader.collectAllMetrics()))
          .isPresent()
          .hasValueSatisfying(
              metric -> {
                final long total =
                    metric.getLongSumData().getPoints().stream()
                        .mapToLong(LongPointData::getValue)
                        .sum();
                assertThat(total).isEqualTo(3);
              });
    }

    @Test
    void shouldIncludeCorrectCounterAttributes() {
      // when
      handler.handle(typed(piCreatedRecord()));

      // then
      assertThat(findCounter(metricReader.collectAllMetrics()))
          .isPresent()
          .hasValueSatisfying(
              metric ->
                  assertThat(metric.getLongSumData().getPoints())
                      .first()
                      .satisfies(
                          point -> {
                            final var attrs = point.getAttributes();
                            assertThat(attrs.get(BPMN_PROCESS_ID)).isNotNull();
                            assertThat(attrs.get(VERSION)).isNotNull();
                            assertThat(attrs.get(ID)).isNotNull();
                          }));
    }
  }
}
