/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalyticsHandlerCatalogTest {

  @Test
  void shouldRegisterAllExpectedHandlersWhenAllCategoriesActive() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var otelSdkManager = TestOtelSdkManager.inMemory(logExporter);

    // when
    final var registry = AnalyticsHandlerCatalog.build(otelSdkManager, AnalyticsCategory.all());

    // then — must contain at least all known contractual + optional handlers
    assertThat(registry.registrations())
        .contains(
            Map.entry(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATED),
            Map.entry(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Map.entry(ValueType.USAGE_METRIC, UsageMetricIntent.EXPORTED),
            Map.entry(ValueType.USER_TASK, UserTaskIntent.CREATED));
  }

  @Test
  void shouldRegisterOnlyContractualHandlersWhenOptionalCategoryDisabled() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var otelSdkManager = TestOtelSdkManager.inMemory(logExporter);

    // when
    final var registry =
        AnalyticsHandlerCatalog.build(otelSdkManager, Set.of(AnalyticsCategory.CONTRACTUAL));

    // then — contractual handlers present, optional handlers absent
    assertThat(registry.registrations())
        .contains(
            Map.entry(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATED),
            Map.entry(ValueType.USAGE_METRIC, UsageMetricIntent.EXPORTED),
            Map.entry(ValueType.USER_TASK, UserTaskIntent.CREATED))
        .doesNotContain(
            Map.entry(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  void shouldRegisterOnlyOptionalHandlersWhenContractualCategoryDisabled() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var otelSdkManager = TestOtelSdkManager.inMemory(logExporter);

    // when
    final var registry =
        AnalyticsHandlerCatalog.build(otelSdkManager, Set.of(AnalyticsCategory.OPTIONAL));

    // then — optional handlers present, contractual handlers absent
    assertThat(registry.registrations())
        .contains(Map.entry(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .doesNotContain(
            Map.entry(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationIntent.CREATED),
            Map.entry(ValueType.USAGE_METRIC, UsageMetricIntent.EXPORTED),
            Map.entry(ValueType.USER_TASK, UserTaskIntent.CREATED));
  }

  @Test
  void shouldRegisterNoHandlersWhenNoCategoriesActive() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var otelSdkManager = TestOtelSdkManager.inMemory(logExporter);

    // when
    final var registry = AnalyticsHandlerCatalog.build(otelSdkManager, Set.of());

    // then
    assertThat(registry.registrations()).isEmpty();
  }
}
