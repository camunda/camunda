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
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableTenantRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TenantDeletedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  @Test
  void shouldEmitTenantDeletedEventWithSafeAttributesOnly() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var handler = new TenantDeletedHandler(TestOtelSdkManager.inMemory(logExporter));

    final var value =
        ImmutableTenantRecordValue.builder()
            .withTenantId("acme")
            .withTenantKey(77L)
            .withName("ACME Corporation")
            .withDescription("Tenant for the ACME account")
            .withEntityId("john.doe@example.com")
            .build();

    final var record =
        FACTORY.generateRecord(
            ValueType.TENANT,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(TenantIntent.DELETED)
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
                      AnalyticsAttributes.Event.NAME, AnalyticsAttributes.Event.TENANT_DELETED)
                  .containsEntry(AnalyticsAttributes.Tenant.ID, "acme")
                  .containsKey(AnalyticsAttributes.Log.POSITION)
                  .containsKey(AnalyticsAttributes.Event.SEQUENCE_NUMBER);

              assertThat(logRecord.getTimestampEpochNanos())
                  .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));

              // Free-text and entity fields must not appear in any attribute value
              final var allValues = attrs.values().stream().map(Object::toString).toList();
              assertThat(allValues)
                  .doesNotContain("ACME Corporation")
                  .doesNotContain("Tenant for the ACME account")
                  .doesNotContain("john.doe@example.com");
            });
  }

  @Test
  void shouldReturnCorrectCategory() {
    final var handler =
        new TenantDeletedHandler(TestOtelSdkManager.inMemory(InMemoryLogRecordExporter.create()));

    assertThat(handler.category()).isEqualTo(AnalyticsCategory.CONTRACTUAL);
  }
}
