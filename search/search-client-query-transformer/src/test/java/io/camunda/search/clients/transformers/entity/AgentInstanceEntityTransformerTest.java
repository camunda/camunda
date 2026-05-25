/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity.AgentInstanceToolValue;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AgentInstanceEntityTransformerTest {

  private final AgentInstanceEntityTransformer transformer = new AgentInstanceEntityTransformer();

  private io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity buildSource() {
    final var source = new io.camunda.webapps.schema.entities.agentinstance.AgentInstanceEntity();
    source.setKey(100L);
    source.setElementInstanceKeys(List.of(1L, 2L));
    source.setStatus(
        io.camunda.webapps.schema.entities.agentinstance.AgentInstanceStatus.COMPLETED);
    source.setModel("gpt-4o");
    source.setProvider("openai");
    source.setSystemPrompt("You are helpful.");
    source.setInputTokens(50L);
    source.setOutputTokens(30L);
    source.setModelCalls(2);
    source.setToolCalls(3);
    source.setMaxTokens(1000L);
    source.setMaxModelCalls(10);
    source.setMaxToolCalls(20);
    source.setTools(
        List.of(
            new AgentInstanceToolValue("searchTool", "searches things", "elem-1"),
            new AgentInstanceToolValue("calcTool", null, null)));
    source.setElementId("Task_1");
    source.setProcessInstanceKey(200L);
    source.setRootProcessInstanceKey(300L);
    source.setProcessDefinitionKey(400L);
    source.setBpmnProcessId("myProcess");
    source.setProcessDefinitionVersion(2);
    source.setVersionTag("v2");
    source.setTenantId("<default>");
    source.setCreationDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
    source.setLastUpdatedDate(OffsetDateTime.parse("2024-01-02T00:00:00Z"));
    source.setCompletionDate(OffsetDateTime.parse("2024-01-03T00:00:00Z"));
    return source;
  }

  @Test
  void shouldMapAllFields() {
    final var result = transformer.apply(buildSource());

    assertThat(result.agentInstanceKey()).isEqualTo(100L);
    assertThat(result.elementInstanceKeys()).containsExactly(1L, 2L);
    assertThat(result.status()).isEqualTo(AgentInstanceStatus.COMPLETED);
    assertThat(result.definition().model()).isEqualTo("gpt-4o");
    assertThat(result.definition().provider()).isEqualTo("openai");
    assertThat(result.definition().systemPrompt()).isEqualTo("You are helpful.");
    assertThat(result.metrics().inputTokens()).isEqualTo(50L);
    assertThat(result.metrics().outputTokens()).isEqualTo(30L);
    assertThat(result.metrics().modelCalls()).isEqualTo(2);
    assertThat(result.metrics().toolCalls()).isEqualTo(3);
    assertThat(result.limits().maxTokens()).isEqualTo(1000L);
    assertThat(result.limits().maxModelCalls()).isEqualTo(10);
    assertThat(result.limits().maxToolCalls()).isEqualTo(20);
    assertThat(result.tools()).hasSize(2);
    assertThat(result.tools().get(0).name()).isEqualTo("searchTool");
    assertThat(result.tools().get(0).description()).isEqualTo("searches things");
    assertThat(result.tools().get(0).elementId()).isEqualTo("elem-1");
    assertThat(result.tools().get(1).name()).isEqualTo("calcTool");
    assertThat(result.tools().get(1).description()).isNull();
    assertThat(result.tools().get(1).elementId()).isNull();
    assertThat(result.elementId()).isEqualTo("Task_1");
    assertThat(result.processInstanceKey()).isEqualTo(200L);
    assertThat(result.rootProcessInstanceKey()).isEqualTo(300L);
    assertThat(result.processDefinitionKey()).isEqualTo(400L);
    assertThat(result.processDefinitionId()).isEqualTo("myProcess");
    assertThat(result.processDefinitionVersion()).isEqualTo(2);
    assertThat(result.versionTag()).isEqualTo("v2");
    assertThat(result.tenantId()).isEqualTo("<default>");
    assertThat(result.creationDate()).isEqualTo(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
    assertThat(result.lastUpdatedDate()).isEqualTo(OffsetDateTime.parse("2024-01-02T00:00:00Z"));
    assertThat(result.completionDate()).isEqualTo(OffsetDateTime.parse("2024-01-03T00:00:00Z"));
  }

  @Test
  void shouldMapRootProcessInstanceKeyMinusOneToNull() {
    final var source = buildSource();
    source.setRootProcessInstanceKey(-1L);

    final var result = transformer.apply(source);

    assertThat(result.rootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNullToolsToEmptyList() {
    final var source = buildSource();
    source.setTools(null);

    final var result = transformer.apply(source);

    assertThat(result.tools()).isEmpty();
  }

  @Test
  void shouldMapNullStatusToUnknown() {
    final var source = buildSource();
    source.setStatus(null);

    final var result = transformer.apply(source);

    assertThat(result.status()).isEqualTo(AgentInstanceStatus.UNKNOWN);
  }

  @Test
  void shouldMapNullCompletionDate() {
    final var source = buildSource();
    source.setCompletionDate(null);

    final var result = transformer.apply(source);

    assertThat(result.completionDate()).isNull();
  }

  @ParameterizedTest
  @EnumSource(io.camunda.webapps.schema.entities.agentinstance.AgentInstanceStatus.class)
  void shouldMapEveryStatus(
      final io.camunda.webapps.schema.entities.agentinstance.AgentInstanceStatus webappsStatus) {
    final var source = buildSource();
    source.setStatus(webappsStatus);

    final var result = transformer.apply(source);

    assertThat(result.status()).isNotNull();
    assertThat(result.status().name()).isEqualTo(webappsStatus.name());
  }
}
