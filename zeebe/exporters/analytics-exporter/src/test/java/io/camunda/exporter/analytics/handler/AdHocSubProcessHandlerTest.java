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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdHocSubProcessHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private InMemoryLogRecordExporter memoryExporter;
  private AdHocSubProcessHandler handler;

  @BeforeEach
  void setUp() {
    memoryExporter = InMemoryLogRecordExporter.create();
    handler = new AdHocSubProcessHandler(TestOtelSdkManager.inMemory(memoryExporter));
  }

  @Test
  void shouldEmitEventForAdHocSubProcess() {
    // given
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withBpmnProcessId("my-process")
            .withProcessDefinitionKey(42L)
            .withProcessInstanceKey(99L)
            .withElementId("adhoc-1")
            .withTenantId("tenant-a")
            .build();
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                    .withValue(value));

    // when
    handler.handle(typed(record));

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord ->
                assertThat(logRecord.getAttributes().asMap())
                    .containsEntry(
                        AnalyticsAttributes.Event.NAME,
                        AnalyticsAttributes.Event.ADHOC_SUBPROCESS_ACTIVATED)
                    .containsEntry(AnalyticsAttributes.Process.BPMN_PROCESS_ID, "my-process")
                    .containsEntry(AnalyticsAttributes.Process.DEFINITION_KEY, 42L)
                    .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, 99L)
                    .containsEntry(AnalyticsAttributes.Element.ID, "adhoc-1")
                    .containsEntry(AnalyticsAttributes.Tenant.ID, "tenant-a"));
  }

  @Test
  void shouldSkipNonAdHocSubProcessElement() {
    // given
    final var value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .build();
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                    .withValue(value));

    // when
    handler.handle(typed(record));

    // then
    assertThat(memoryExporter.getFinishedLogRecordItems()).isEmpty();
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }
}
