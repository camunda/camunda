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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class IncidentCreatedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  @Test
  void shouldEmitIncidentCreatedEventWithSafeAttributesOnly() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var handler = new IncidentCreatedHandler(TestOtelSdkManager.inMemory(logExporter));

    final var value =
        ImmutableIncidentRecordValue.builder()
            .withBpmnProcessId("order-process")
            .withProcessDefinitionKey(11L)
            .withProcessInstanceKey(22L)
            .withElementId("call-payment-service")
            .withTenantId("acme")
            .withErrorType(ErrorType.IO_MAPPING_ERROR)
            .withErrorMessage("failed to evaluate expression '=customerEmail': jane@example.com")
            .build();

    final var record =
        FACTORY.generateRecord(
            ValueType.INCIDENT,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(IncidentIntent.CREATED)
                    .withKey(4242L)
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
                      AnalyticsAttributes.Event.PROCESS_INCIDENT_CREATED)
                  .containsEntry(AnalyticsAttributes.Incident.KEY, 4242L)
                  .containsEntry(AnalyticsAttributes.Process.BPMN_PROCESS_ID, "order-process")
                  .containsEntry(AnalyticsAttributes.Process.DEFINITION_KEY, 11L)
                  .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, 22L)
                  .containsEntry(AnalyticsAttributes.Tenant.ID, "acme");

              assertThat(logRecord.getTimestampEpochNanos())
                  .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));

              // The error message can carry variable values and must never be exported
              final var allValues = attrs.values().stream().map(Object::toString).toList();
              assertThat(allValues)
                  .doesNotContain(
                      "failed to evaluate expression '=customerEmail': jane@example.com");
            });
  }

  @Test
  void shouldReturnCorrectCategory() {
    final var handler =
        new IncidentCreatedHandler(TestOtelSdkManager.inMemory(InMemoryLogRecordExporter.create()));

    assertThat(handler.category()).isEqualTo(AnalyticsCategory.OPTIONAL);
  }
}
