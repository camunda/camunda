/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.es.schema.index.AgentRunIndexES;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AgentRunIndexMappingTest {

  private static final Map<String, Property.Kind> TOOL_COUNT_PROPERTIES =
      Map.of(
          "toolFlowNodeId", Property.Kind.Keyword,
          "toolName", Property.Kind.Keyword,
          "count", Property.Kind.Long);

  private final TypeMapping mapping =
      new AgentRunIndexES().addProperties(new TypeMapping.Builder()).build();

  @Test
  void shouldMapEveryFieldOfTheAgentRunSchema() {
    // when
    final Map<String, Property.Kind> kinds = kindsOf(mapping.properties());

    // then the mapping holds exactly the fields of data model proposal 01, section 1.1
    assertThat(kinds)
        .containsExactlyInAnyOrderEntriesOf(
            Map.ofEntries(
                entry("agentInstanceId", Property.Kind.Keyword),
                entry("agentId", Property.Kind.Keyword),
                entry("agentType", Property.Kind.Keyword),
                entry("flowNodeId", Property.Kind.Keyword),
                entry("flowNodeInstanceIds", Property.Kind.Keyword),
                entry("processDefinitionKey", Property.Kind.Keyword),
                entry("processDefinitionId", Property.Kind.Keyword),
                entry("processDefinitionVersion", Property.Kind.Keyword),
                entry("processInstanceId", Property.Kind.Keyword),
                entry("rootProcessInstanceId", Property.Kind.Keyword),
                entry("rootProcessDefinitionKey", Property.Kind.Keyword),
                entry("tenantId", Property.Kind.Keyword),
                entry("model", Property.Kind.Keyword),
                entry("provider", Property.Kind.Keyword),
                entry("maxModelCalls", Property.Kind.Long),
                entry("maxToolCalls", Property.Kind.Long),
                entry("maxTokens", Property.Kind.Long),
                entry("startDate", Property.Kind.Date),
                entry("lastActivityDate", Property.Kind.Date),
                entry("endDate", Property.Kind.Date),
                entry("status", Property.Kind.Keyword),
                entry("endState", Property.Kind.Keyword),
                entry("inputTokens", Property.Kind.Long),
                entry("outputTokens", Property.Kind.Long),
                entry("reasoningTokens", Property.Kind.Long),
                entry("cacheWriteTokens", Property.Kind.Long),
                entry("cacheReadTokens", Property.Kind.Long),
                entry("totalTokens", Property.Kind.Long),
                entry("modelCalls", Property.Kind.Long),
                entry("toolCalls", Property.Kind.Long),
                entry("limitHit", Property.Kind.Keyword),
                entry("hasIncident", Property.Kind.Boolean),
                entry("toolIncidentCount", Property.Kind.Long),
                entry("failureReasons", Property.Kind.Nested),
                entry("activeDurationMs", Property.Kind.Long),
                entry("toolFlowNodeIds", Property.Kind.Keyword),
                entry("toolCallsByTool", Property.Kind.Nested),
                entry("toolIncidentsByTool", Property.Kind.Nested),
                entry("lastRecordPosition", Property.Kind.Long),
                entry("enrichedAt", Property.Kind.Date)));
  }

  @Test
  void shouldMapTheToolListsAsNestedCounts() {
    // when
    final Map<String, Property.Kind> callsByTool =
        kindsOf(mapping.properties().get(AgentRunIndex.TOOL_CALLS_BY_TOOL).nested().properties());
    final Map<String, Property.Kind> incidentsByTool =
        kindsOf(
            mapping.properties().get(AgentRunIndex.TOOL_INCIDENTS_BY_TOOL).nested().properties());

    // then
    assertThat(callsByTool).containsExactlyInAnyOrderEntriesOf(TOOL_COUNT_PROPERTIES);
    assertThat(incidentsByTool).containsExactlyInAnyOrderEntriesOf(TOOL_COUNT_PROPERTIES);
  }

  @Test
  void shouldMapTheFailureReasonsAsNestedEntries() {
    // when
    final Map<String, Property.Kind> failureReasons =
        kindsOf(mapping.properties().get(AgentRunIndex.FAILURE_REASONS).nested().properties());

    // then
    assertThat(failureReasons)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "kind", Property.Kind.Keyword,
                "limit", Property.Kind.Keyword,
                "flowNodeId", Property.Kind.Keyword,
                "toolName", Property.Kind.Keyword,
                "errorType", Property.Kind.Keyword,
                "message", Property.Kind.Keyword,
                "count", Property.Kind.Long));
  }

  @Test
  void shouldKeepTheLastRecordPositionOutOfTheSearchIndex() {
    // when
    final var position = mapping.properties().get(AgentRunIndex.LAST_RECORD_POSITION).long_();

    // then the import's merge script still reads it from _source, but no query can use it
    assertThat(position.index()).isFalse();
    assertThat(position.docValues().enabled()).isFalse();
  }

  @Test
  void shouldStartAtVersionOne() {
    assertThat(new AgentRunIndexES().getVersion()).isEqualTo(1);
  }

  private static Map<String, Property.Kind> kindsOf(final Map<String, Property> properties) {
    return properties.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()._kind()));
  }
}
