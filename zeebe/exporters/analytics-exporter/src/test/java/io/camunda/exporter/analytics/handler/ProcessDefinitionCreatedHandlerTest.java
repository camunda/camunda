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
import io.camunda.exporter.analytics.AnalyticsCategory;
import io.camunda.exporter.analytics.TestOtelSdkManager;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ProcessDefinitionCreatedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  @Test
  void shouldEmitProcessDefinitionCreatedEventWithSafeAttributesOnly() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var handler =
        new ProcessDefinitionCreatedHandler(TestOtelSdkManager.inMemory(logExporter));

    final var value =
        ImmutableProcess.builder()
            .withBpmnProcessId("order-process")
            .withProcessDefinitionKey(11L)
            .withVersion(3)
            .withVersionTag("release-2026-q1")
            .withResourceName("Jane Doe's order process.bpmn")
            .withResource("<definitions/>".getBytes(StandardCharsets.UTF_8))
            .withTenantId("acme")
            .build();

    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessIntent.CREATED)
                    .withValue(value));

    // when
    handler.handle(typed(record));

    // then
    assertThat(logExporter.getFinishedLogRecordItems())
        .singleElement()
        .satisfies(
            logRecord -> {
              final var attrs = logRecord.getAttributes().asMap();

              assertThat(attrs)
                  .containsEntry(
                      AnalyticsAttributes.Event.NAME,
                      AnalyticsAttributes.Event.PROCESS_DEFINITION_CREATED)
                  .containsEntry(AnalyticsAttributes.Process.BPMN_PROCESS_ID, "order-process")
                  .containsEntry(AnalyticsAttributes.Process.DEFINITION_KEY, 11L)
                  .containsEntry(AnalyticsAttributes.Process.VERSION, 3L)
                  .containsEntry(AnalyticsAttributes.Tenant.ID, "acme");

              assertThat(logRecord.getTimestampEpochNanos())
                  .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));

              // Author-supplied free text must not appear in any attribute value
              final var allValues = attrs.values().stream().map(Object::toString).toList();
              assertThat(allValues)
                  .doesNotContain("release-2026-q1")
                  .doesNotContain("Jane Doe's order process.bpmn")
                  .doesNotContain("<definitions/>");
            });
  }

  @Test
  void shouldReturnCorrectCategory() {
    final var handler =
        new ProcessDefinitionCreatedHandler(
            TestOtelSdkManager.inMemory(InMemoryLogRecordExporter.create()));

    assertThat(handler.category()).isEqualTo(AnalyticsCategory.OPTIONAL);
  }
}
