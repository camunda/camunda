/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel;
import io.camunda.db.rdbms.write.domain.AgentInstanceDbModel.AgentInstanceToolDbValue;
import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentInstanceEntityMapperTest {

  @Test
  void shouldMapAllFields() {
    final var creationDate = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    final var lastUpdatedDate = OffsetDateTime.parse("2024-01-02T00:00:00Z");
    final var completionDate = OffsetDateTime.parse("2024-01-03T00:00:00Z");

    final var dbModel =
        buildModel(
            key -> {
              key.agentInstanceKey(100L);
              key.elementId("Task_1");
              key.processInstanceKey(200L);
              key.rootProcessInstanceKey(300L);
              key.processDefinitionId("myProcess");
              key.processDefinitionKey(400L);
              key.processDefinitionVersion(2);
              key.versionTag("v1");
              key.tenantId("<default>");
              key.partitionId(1);
              key.status(AgentInstanceStatus.THINKING);
              key.model("gpt-4");
              key.provider("openai");
              key.systemPrompt("You are an assistant.");
              key.maxTokens(10000L);
              key.maxModelCalls(100);
              key.maxToolCalls(50);
              key.inputTokens(500L);
              key.outputTokens(300L);
              key.modelCalls(5);
              key.toolCalls(3);
              key.creationDate(creationDate);
              key.lastUpdatedDate(lastUpdatedDate);
              key.completionDate(completionDate);
              key.elementInstanceKeys(List.of(1L, 2L, 3L));
              key.toolValues(List.of(new AgentInstanceToolDbValue("myTool", "A tool", "Task_2")));
            });

    final AgentInstanceEntity entity = AgentInstanceEntityMapper.toEntity(dbModel);

    assertThat(entity.agentInstanceKey()).isEqualTo(100L);
    assertThat(entity.elementId()).isEqualTo("Task_1");
    assertThat(entity.processInstanceKey()).isEqualTo(200L);
    assertThat(entity.rootProcessInstanceKey()).isEqualTo(300L);
    assertThat(entity.processDefinitionId()).isEqualTo("myProcess");
    assertThat(entity.processDefinitionKey()).isEqualTo(400L);
    assertThat(entity.processDefinitionVersion()).isEqualTo(2);
    assertThat(entity.versionTag()).isEqualTo("v1");
    assertThat(entity.tenantId()).isEqualTo("<default>");
    assertThat(entity.status()).isEqualTo(AgentInstanceStatus.THINKING);
    assertThat(entity.definition().model()).isEqualTo("gpt-4");
    assertThat(entity.definition().provider()).isEqualTo("openai");
    assertThat(entity.definition().systemPrompt()).isEqualTo("You are an assistant.");
    assertThat(entity.limits().maxTokens()).isEqualTo(10000L);
    assertThat(entity.limits().maxModelCalls()).isEqualTo(100);
    assertThat(entity.limits().maxToolCalls()).isEqualTo(50);
    assertThat(entity.metrics().inputTokens()).isEqualTo(500L);
    assertThat(entity.metrics().outputTokens()).isEqualTo(300L);
    assertThat(entity.metrics().modelCalls()).isEqualTo(5);
    assertThat(entity.metrics().toolCalls()).isEqualTo(3);
    assertThat(entity.creationDate()).isEqualTo(creationDate);
    assertThat(entity.lastUpdatedDate()).isEqualTo(lastUpdatedDate);
    assertThat(entity.completionDate()).isEqualTo(completionDate);
    assertThat(entity.elementInstanceKeys()).containsExactly(1L, 2L, 3L);
    assertThat(entity.tools()).hasSize(1);
    assertThat(entity.tools().get(0).name()).isEqualTo("myTool");
    assertThat(entity.tools().get(0).description()).isEqualTo("A tool");
    assertThat(entity.tools().get(0).elementId()).isEqualTo("Task_2");
  }

  @Test
  void shouldMapRootProcessInstanceKeySentinelToNull() {
    final var dbModel =
        buildModel(
            m -> {
              m.agentInstanceKey(1L);
              m.processDefinitionId("p");
              m.elementId("e");
              m.processInstanceKey(10L);
              m.processDefinitionKey(20L);
              m.processDefinitionVersion(1);
              m.tenantId("t");
              m.status(AgentInstanceStatus.COMPLETED);
              m.model("gpt");
              m.provider("openai");
              m.systemPrompt("s");
              m.creationDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
              m.lastUpdatedDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
              m.rootProcessInstanceKey(-1L);
            });

    final AgentInstanceEntity entity = AgentInstanceEntityMapper.toEntity(dbModel);

    assertThat(entity.rootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldMapNullToolsToEmptyList() {
    final var dbModel =
        buildModel(
            m -> {
              m.agentInstanceKey(1L);
              m.processDefinitionId("p");
              m.elementId("e");
              m.processInstanceKey(10L);
              m.processDefinitionKey(20L);
              m.processDefinitionVersion(1);
              m.tenantId("t");
              m.status(AgentInstanceStatus.IDLE);
              m.model("gpt");
              m.provider("openai");
              m.systemPrompt("s");
              m.creationDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
              m.lastUpdatedDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
              m.toolValues(null);
            });

    final AgentInstanceEntity entity = AgentInstanceEntityMapper.toEntity(dbModel);

    assertThat(entity.tools()).isEmpty();
  }

  @Test
  void shouldReturnNullForNullDbModel() {
    assertThat(AgentInstanceEntityMapper.toEntity(null)).isNull();
  }

  private AgentInstanceDbModel buildModel(
      final java.util.function.Consumer<AgentInstanceDbModel> config) {
    final var model = new AgentInstanceDbModel();
    config.accept(model);
    return model;
  }
}
