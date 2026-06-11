/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Asserts that every attribute key emitted by any registered handler is declared in {@link
 * AnalyticsAttributes}. This makes AnalyticsAttributes the auditable PII contract: adding a new
 * attribute key requires a visible PR diff to that class, where a reviewer can confirm the field is
 * safe to export.
 */
class HandlerPiiGuardTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @Test
  void shouldOnlyEmitDeclaredAttributeKeys() throws IllegalAccessException {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var otelSdkManager = TestOtelSdkManager.inMemory(logExporter);
    final var registry = HandlerCatalog.build(otelSdkManager);

    final var allowedKeys = collectDeclaredAttributeKeys();

    // when — exercise every registered handler with a synthetic record
    for (final var entry : registry.registrations()) {
      registry.handle(
          FACTORY.generateRecord(
              entry.getKey(),
              r -> r.withRecordType(RecordType.EVENT).withIntent(entry.getValue())));
    }

    // AdHocSubProcessHandler has an internal filter that skips non-AD_HOC_SUB_PROCESS elements.
    // Generate one targeted record to ensure its emission path is covered.
    final var adHocValue =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .withBpmnProcessId("guard-process")
            .withProcessDefinitionKey(1L)
            .withProcessInstanceKey(2L)
            .withElementId("adhoc-guard")
            .withTenantId("<default>")
            .build();
    final var adHocRecord =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                    .withValue(adHocValue));
    registry.handle(adHocRecord);

    // then — every attribute key in every emitted log record must be declared
    final var logRecords = logExporter.getFinishedLogRecordItems();
    assertThat(logRecords)
        .as("Expected at least one log record to be emitted across all handlers")
        .isNotEmpty();

    for (final var logRecord : logRecords) {
      for (final var key : logRecord.getAttributes().asMap().keySet()) {
        assertThat(allowedKeys)
            .as(
                "Attribute key '%s' was emitted but is not declared in AnalyticsAttributes — "
                    + "add it to the appropriate nested class to make the PII boundary explicit",
                key.getKey())
            .contains(key);
      }
    }
  }

  /**
   * Collects all {@code static final AttributeKey<?>} fields from all nested classes in {@link
   * AnalyticsAttributes}. String event-name constants are not AttributeKey instances and are
   * naturally excluded.
   */
  private static Set<AttributeKey<?>> collectDeclaredAttributeKeys() throws IllegalAccessException {
    final var keys = new HashSet<AttributeKey<?>>();
    for (final var nestedClass : AnalyticsAttributes.class.getDeclaredClasses()) {
      for (final var field : nestedClass.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())
            && AttributeKey.class.isAssignableFrom(field.getType())) {
          keys.add((AttributeKey<?>) field.get(null));
        }
      }
    }
    return Set.copyOf(keys);
  }
}
