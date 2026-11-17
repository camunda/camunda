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
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentHistoryMessageContentValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableAgentInstanceToolValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AgentInstanceCreatedHandlerTest {

  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  @SuppressWarnings("unchecked")
  private static <T extends RecordValue> Record<T> typed(final Record<?> record) {
    return (Record<T>) record;
  }

  @Test
  void shouldEmitAgentInstanceCreatedEventWithSafeAttributesOnly() {
    // given
    final var logExporter = InMemoryLogRecordExporter.create();
    final var handler = new AgentInstanceCreatedHandler(TestOtelSdkManager.inMemory(logExporter));

    final var value =
        ImmutableAgentInstanceRecordValue.builder()
            .withAgentInstanceKey(101L)
            .withAgentDefinitionKey(202L)
            .withStatus(AgentInstanceStatus.INITIALIZING)
            .withBpmnProcessId("support-agent-process")
            .withProcessDefinitionKey(11L)
            .withProcessInstanceKey(22L)
            .withRootProcessInstanceKey(9L)
            .withTenantId("acme")
            .withProcessDefinitionVersionTag("release-2026-q1")
            .withDefinition(
                b ->
                    b.withModel("claude-sonnet-4-5")
                        .withProvider("anthropic")
                        .withSystemPrompt(
                            java.util.List.of(
                                ImmutableAgentHistoryMessageContentValue.builder()
                                    .withContentType(AgentHistoryContentType.TEXT)
                                    .withText("You are helping Jane Doe with her order.")
                                    .build())))
            .withTools(
                java.util.List.of(
                    ImmutableAgentInstanceToolValue.builder()
                        .withName("lookup_customer")
                        .withDescription("Looks up a customer by email address")
                        .withElementId("tool-lookup")
                        .build()))
            .withChangedAttributes(java.util.List.of("status"))
            .build();

    final var record =
        FACTORY.generateRecord(
            ValueType.AGENT_INSTANCE,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(AgentInstanceIntent.CREATED)
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
                      AnalyticsAttributes.Event.AGENT_INSTANCE_CREATED)
                  .containsEntry(AnalyticsAttributes.Agent.INSTANCE_KEY, 101L)
                  .containsEntry(AnalyticsAttributes.Agent.DEFINITION_KEY, 202L)
                  .containsEntry(AnalyticsAttributes.Agent.STATUS, "INITIALIZING")
                  .containsEntry(
                      AnalyticsAttributes.Process.BPMN_PROCESS_ID, "support-agent-process")
                  .containsEntry(AnalyticsAttributes.Process.DEFINITION_KEY, 11L)
                  .containsEntry(AnalyticsAttributes.Process.INSTANCE_KEY, 22L)
                  .containsEntry(AnalyticsAttributes.Process.ROOT_INSTANCE_KEY, 9L)
                  .containsEntry(AnalyticsAttributes.Tenant.ID, "acme");

              assertThat(logRecord.getTimestampEpochNanos())
                  .isEqualTo(TimeUnit.MILLISECONDS.toNanos(record.getTimestamp()));

              // Prompts, tool payloads and version tags must not appear in any attribute value
              final var allValues = attrs.values().stream().map(Object::toString).toList();
              assertThat(allValues)
                  .doesNotContain("You are helping Jane Doe with her order.")
                  .doesNotContain("claude-sonnet-4-5")
                  .doesNotContain("anthropic")
                  .doesNotContain("lookup_customer")
                  .doesNotContain("Looks up a customer by email address")
                  .doesNotContain("release-2026-q1");
            });
  }

  @Test
  void shouldReturnCorrectCategory() {
    final var handler =
        new AgentInstanceCreatedHandler(
            TestOtelSdkManager.inMemory(InMemoryLogRecordExporter.create()));

    assertThat(handler.category()).isEqualTo(AnalyticsCategory.OPTIONAL);
  }
}
