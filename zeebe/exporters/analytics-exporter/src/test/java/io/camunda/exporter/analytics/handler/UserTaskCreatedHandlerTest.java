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
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class UserTaskCreatedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  @Test
  void shouldEmitUserTaskCreatedEventWithSafeAttributesOnly() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var handler = new UserTaskCreatedHandler(TestOtelSdkManager.inMemory(logExporter));

    final var value =
        ImmutableUserTaskRecordValue.builder()
            .withBpmnProcessId("test-process")
            .withProcessDefinitionKey(42L)
            .withProcessInstanceKey(100L)
            .withElementId("user-task-1")
            .withTenantId("<default>")
            // PII fields — must NOT appear in the emitted event
            .withAssignee("john.doe@example.com")
            .withCandidateUsersList(List.of("alice@example.com"))
            .withCandidateGroupsList(List.of("finance-team"))
            .build();

    final var record =
        FACTORY.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(UserTaskIntent.CREATED)
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
                      AnalyticsAttributes.Event.NAME, AnalyticsAttributes.Event.USER_TASK_CREATED)
                  .containsEntry(AnalyticsAttributes.Process.BPMN_PROCESS_ID, "test-process")
                  .containsEntry(AnalyticsAttributes.Process.DEFINITION_KEY, 42L)
                  .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, 100L)
                  .containsEntry(AnalyticsAttributes.Element.ID, "user-task-1")
                  .containsEntry(AnalyticsAttributes.Tenant.ID, "<default>")
                  .containsKey(AnalyticsAttributes.Log.POSITION)
                  .containsKey(AnalyticsAttributes.Event.SEQUENCE_NUMBER);

              assertThat(logRecord.getTimestampEpochNanos())
                  .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));

              // PII must not appear in any attribute value
              final var allValues = attrs.values().stream().map(Object::toString).toList();
              assertThat(allValues)
                  .doesNotContain("john.doe@example.com")
                  .doesNotContain("alice@example.com")
                  .doesNotContain("finance-team");
            });
  }
}
