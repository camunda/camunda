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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserTaskAssignedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private static final String ASSIGNEE = "john.doe@example.com";

  /** Independently computed: {@code printf 'john.doe@example.com' | shasum -a 256}. */
  private static final String ASSIGNEE_SHA_256 =
      "836f82db99121b3481011f16b49dfa5fbc714a0d1b1b9f784a1ebbbf5b39577f";

  private InMemoryLogRecordExporter logExporter;
  private UserTaskAssignedHandler handler;

  @BeforeEach
  void setUp() {
    logExporter = InMemoryLogRecordExporter.create();
    handler = new UserTaskAssignedHandler(TestOtelSdkManager.inMemory(logExporter));
  }

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  private static Record<?> assignedRecord(final String assignee) {
    final var value =
        ImmutableUserTaskRecordValue.builder()
            .withUserTaskKey(77L)
            .withProcessInstanceKey(100L)
            .withTenantId("tenant-a")
            .withAssignee(assignee)
            .withCandidateUsersList(List.of("alice@example.com"))
            .withCandidateGroupsList(List.of("finance-team"))
            .build();
    return FACTORY.generateRecord(
        ValueType.USER_TASK,
        r ->
            r.withRecordType(RecordType.EVENT)
                .withIntent(UserTaskIntent.ASSIGNED)
                .withValue(value));
  }

  @Test
  void shouldEmitHashedAssigneeAndNeverTheRawValue() {
    // given
    final var record = assignedRecord(ASSIGNEE);

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
                      AnalyticsAttributes.Event.NAME, AnalyticsAttributes.Event.USER_TASK_ASSIGNED)
                  .containsEntry(AnalyticsAttributes.UserTask.ASSIGNEE_HASH, ASSIGNEE_SHA_256)
                  .containsEntry(AnalyticsAttributes.UserTask.KEY, 77L)
                  .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, 100L)
                  .containsEntry(AnalyticsAttributes.Tenant.ID, "tenant-a")
                  .containsKey(AnalyticsAttributes.Log.POSITION)
                  .containsKey(AnalyticsAttributes.Event.SEQUENCE_NUMBER);

              assertThat(logRecord.getTimestampEpochNanos())
                  .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));

              // PII must not appear anywhere in any attribute value
              final var allValues = attrs.values().stream().map(Object::toString).toList();
              assertThat(allValues)
                  .noneMatch(v -> v.contains(ASSIGNEE))
                  .noneMatch(v -> v.contains("alice@example.com"))
                  .noneMatch(v -> v.contains("finance-team"));
            });
  }

  @Test
  void shouldSkipWhenAssigneeIsEmpty() {
    // when
    handler.handle(typed(assignedRecord("")));

    // then
    assertThat(logExporter.getFinishedLogRecordItems()).isEmpty();
  }
}
