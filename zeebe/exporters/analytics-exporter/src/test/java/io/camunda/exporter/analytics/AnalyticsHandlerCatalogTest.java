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
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
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

    // then
    assertThat(registry.registrations())
        .containsExactlyInAnyOrder(
            Map.entry(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Map.entry(ValueType.USER_TASK, UserTaskIntent.CREATED),
            Map.entry(ValueType.USER_TASK, UserTaskIntent.ASSIGNED),
            Map.entry(ValueType.TENANT, TenantIntent.CREATED),
            Map.entry(ValueType.TENANT, TenantIntent.DELETED),
            Map.entry(ValueType.INCIDENT, IncidentIntent.CREATED),
            Map.entry(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            Map.entry(ValueType.PROCESS, ProcessIntent.CREATED),
            Map.entry(ValueType.PROCESS, ProcessIntent.DELETED),
            Map.entry(ValueType.DECISION, DecisionIntent.CREATED),
            Map.entry(ValueType.DECISION, DecisionIntent.DELETED),
            Map.entry(ValueType.DECISION_EVALUATION, DecisionEvaluationIntent.EVALUATED),
            Map.entry(ValueType.FORM, FormIntent.CREATED),
            Map.entry(ValueType.FORM, FormIntent.DELETED),
            Map.entry(ValueType.AGENT_INSTANCE, AgentInstanceIntent.CREATED),
            Map.entry(ValueType.AGENT_INSTANCE, AgentInstanceIntent.COMPLETED));
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
            Map.entry(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Map.entry(ValueType.USER_TASK, UserTaskIntent.ASSIGNED),
            Map.entry(ValueType.TENANT, TenantIntent.CREATED),
            Map.entry(ValueType.TENANT, TenantIntent.DELETED),
            Map.entry(ValueType.DECISION_EVALUATION, DecisionEvaluationIntent.EVALUATED))
        .doesNotContain(
            Map.entry(ValueType.USER_TASK, UserTaskIntent.CREATED),
            Map.entry(ValueType.INCIDENT, IncidentIntent.CREATED),
            Map.entry(ValueType.PROCESS, ProcessIntent.CREATED),
            Map.entry(ValueType.DECISION, DecisionIntent.CREATED),
            Map.entry(ValueType.FORM, FormIntent.CREATED),
            Map.entry(ValueType.AGENT_INSTANCE, AgentInstanceIntent.CREATED));
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
        .contains(
            Map.entry(ValueType.USER_TASK, UserTaskIntent.CREATED),
            Map.entry(ValueType.INCIDENT, IncidentIntent.CREATED),
            Map.entry(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            Map.entry(ValueType.PROCESS, ProcessIntent.CREATED),
            Map.entry(ValueType.PROCESS, ProcessIntent.DELETED),
            Map.entry(ValueType.DECISION, DecisionIntent.CREATED),
            Map.entry(ValueType.DECISION, DecisionIntent.DELETED),
            Map.entry(ValueType.FORM, FormIntent.CREATED),
            Map.entry(ValueType.FORM, FormIntent.DELETED),
            Map.entry(ValueType.AGENT_INSTANCE, AgentInstanceIntent.CREATED),
            Map.entry(ValueType.AGENT_INSTANCE, AgentInstanceIntent.COMPLETED))
        .doesNotContain(
            Map.entry(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            Map.entry(ValueType.USER_TASK, UserTaskIntent.ASSIGNED),
            Map.entry(ValueType.TENANT, TenantIntent.CREATED),
            Map.entry(ValueType.DECISION_EVALUATION, DecisionEvaluationIntent.EVALUATED));
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
