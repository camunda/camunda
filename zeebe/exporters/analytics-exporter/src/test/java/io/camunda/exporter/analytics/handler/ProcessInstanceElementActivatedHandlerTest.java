/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics.handler;

import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.BPMN_PROCESS_ID;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Process.VERSION;
import static io.camunda.exporter.analytics.AnalyticsAttributes.Tenant.ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.analytics.AnalyticsAttributes;
import io.camunda.exporter.analytics.TestOtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessInstanceElementActivatedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();
  private static final long NO_PARENT_INSTANCE = -1L;

  private InMemoryLogRecordExporter logExporter;
  private InMemoryMetricReader metricReader;
  private ProcessInstanceElementActivatedHandler handler;

  @BeforeEach
  void setUp() {
    logExporter = InMemoryLogRecordExporter.create();
    metricReader = InMemoryMetricReader.create();
    handler =
        new ProcessInstanceElementActivatedHandler(
            TestOtelSdkManager.inMemoryWithMetrics(logExporter, metricReader));
  }

  @Test
  void shouldEmitEventAndCounterForRootProcessInstance() {
    // given
    final var record = elementActivated(BpmnElementType.PROCESS, NO_PARENT_INSTANCE);
    final var value = (ProcessInstanceRecordValue) record.getValue();

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
                  .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, 100L)
                  .containsEntry(AnalyticsAttributes.Process.ROOT_INSTANCE_KEY, 100L)
                  .containsEntry(ID, "tenant-a")
                  .containsKey(AnalyticsAttributes.Log.POSITION)
                  .containsKey(AnalyticsAttributes.Event.SEQUENCE_NUMBER);

              assertThat(logRecord.getTimestampEpochNanos())
                  .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));
            });

    assertThat(findProcessInstanceCounter(metricReader.collectAllMetrics()))
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(metric.getLongSumData().getPoints())
                    .singleElement()
                    .satisfies(
                        point -> {
                          assertThat(point.getValue()).isEqualTo(1);
                          assertThat(point.getAttributes().get(BPMN_PROCESS_ID))
                              .isEqualTo(value.getBpmnProcessId());
                          assertThat(point.getAttributes().get(VERSION)).isEqualTo(3L);
                          assertThat(point.getAttributes().get(ID)).isEqualTo(value.getTenantId());
                        }));
  }

  @Test
  void shouldAggregateCounterAcrossRootProcessInstances() {
    // when
    handler.handle(typed(elementActivated(BpmnElementType.PROCESS, NO_PARENT_INSTANCE)));
    handler.handle(typed(elementActivated(BpmnElementType.PROCESS, NO_PARENT_INSTANCE)));
    handler.handle(typed(elementActivated(BpmnElementType.PROCESS, NO_PARENT_INSTANCE)));

    // then
    assertThat(findProcessInstanceCounter(metricReader.collectAllMetrics()))
        .isPresent()
        .hasValueSatisfying(
            metric ->
                assertThat(
                        metric.getLongSumData().getPoints().stream()
                            .mapToLong(LongPointData::getValue)
                            .sum())
                    .isEqualTo(3));
  }

  @Test
  void shouldSkipCallActivityChildProcessInstance() {
    // given — a process element whose parent key is set, i.e. a call-activity child
    final var record = elementActivated(BpmnElementType.PROCESS, 77L);

    // when
    handler.handle(typed(record));

    // then
    assertThat(logExporter.getFinishedLogRecordItems()).isEmpty();
    assertThat(findProcessInstanceCounter(metricReader.collectAllMetrics())).isEmpty();
  }

  @Test
  void shouldEmitEventForAdHocSubProcess() {
    // given
    final var record = elementActivated(BpmnElementType.AD_HOC_SUB_PROCESS, 77L);

    // when
    handler.handle(typed(record));

    // then
    assertThat(logExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsEntry(
                        AnalyticsAttributes.Event.NAME,
                        AnalyticsAttributes.Event.ADHOC_SUBPROCESS_ACTIVATED)
                    .containsEntry(BPMN_PROCESS_ID, "my-process")
                    .containsEntry(AnalyticsAttributes.Process.DEFINITION_KEY, 42L)
                    .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, 100L)
                    .containsEntry(AnalyticsAttributes.Element.ID, "adhoc-1")
                    .containsEntry(ID, "tenant-a"));
    assertThat(findProcessInstanceCounter(metricReader.collectAllMetrics())).isEmpty();
  }

  @Test
  void shouldSkipOtherElementTypes() {
    // given — a root-scoped element that is neither a process nor an ad-hoc sub-process
    final var record = elementActivated(BpmnElementType.SERVICE_TASK, NO_PARENT_INSTANCE);

    // when
    handler.handle(typed(record));

    // then
    assertThat(logExporter.getFinishedLogRecordItems()).isEmpty();
    assertThat(findProcessInstanceCounter(metricReader.collectAllMetrics())).isEmpty();
  }

  private static Record<?> elementActivated(
      final BpmnElementType elementType, final long parentProcessInstanceKey) {
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .from(FACTORY.generateObject(ProcessInstanceRecordValue.class))
            .withBpmnElementType(elementType)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withBpmnProcessId("my-process")
            .withVersion(3)
            .withProcessDefinitionKey(42L)
            .withProcessInstanceKey(100L)
            .withRootProcessInstanceKey(100L)
            .withElementId("adhoc-1")
            .withTenantId("tenant-a")
            .build();
    return FACTORY.generateRecord(
        ValueType.PROCESS_INSTANCE,
        r ->
            r.withRecordType(RecordType.EVENT)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withValue(value));
  }

  private static Optional<MetricData> findProcessInstanceCounter(
      final Collection<MetricData> metrics) {
    return metrics.stream()
        .filter(m -> m.getName().equals(AnalyticsAttributes.Metric.PROCESS_INSTANCE_CREATED))
        .findFirst();
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }
}
