/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.write.domain.AgentHistoryDbModel;
import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ToolCall;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentHistoryEntityMapperTest {

  @Test
  void shouldMapAllFields() {
    final var producedAt = OffsetDateTime.parse("2024-06-01T12:00:00Z");

    final var dbModel = new AgentHistoryDbModel();
    dbModel.agentHistoryKey(100L);
    dbModel.agentInstanceKey(200L);
    dbModel.elementInstanceKey(300L);
    dbModel.processInstanceKey(400L);
    dbModel.rootProcessInstanceKey(500L);
    dbModel.processDefinitionKey(600L);
    dbModel.processDefinitionId("myProcess");
    dbModel.tenantId("<default>");
    dbModel.partitionId(1);
    dbModel.jobKey(700L);
    dbModel.jobLease("lease-abc");
    dbModel.loopIteration(3);
    dbModel.role(AgentInstanceHistoryRole.ASSISTANT);
    dbModel.commitStatus(AgentInstanceHistoryCommitStatus.COMMITTED);
    dbModel.producedAt(producedAt);
    dbModel.inputTokens(150L);
    dbModel.outputTokens(75L);
    dbModel.durationMs(300L);
    dbModel.contentItems(
        List.of(new ContentItem(ContentType.TEXT, "Hello from assistant", null, null)));
    dbModel.toolCallValues(
        List.of(new ToolCall("tc-1", "myTool", "Task_1", Map.of("key", "value"))));

    final AgentInstanceHistoryEntity entity = AgentHistoryEntityMapper.toEntity(dbModel);

    assertThat(entity.historyItemKey()).isEqualTo(100L);
    assertThat(entity.agentInstanceKey()).isEqualTo(200L);
    assertThat(entity.elementInstanceKey()).isEqualTo(300L);
    assertThat(entity.processInstanceKey()).isEqualTo(400L);
    assertThat(entity.processDefinitionKey()).isEqualTo(600L);
    assertThat(entity.processDefinitionId()).isEqualTo("myProcess");
    assertThat(entity.tenantId()).isEqualTo("<default>");
    assertThat(entity.jobKey()).isEqualTo(700L);
    assertThat(entity.jobLease()).isEqualTo("lease-abc");
    assertThat(entity.loopIteration()).isEqualTo(3);
    assertThat(entity.role()).isEqualTo(AgentInstanceHistoryRole.ASSISTANT);
    assertThat(entity.commitStatus()).isEqualTo(AgentInstanceHistoryCommitStatus.COMMITTED);
    assertThat(entity.producedAt()).isEqualTo(producedAt);
    assertThat(entity.metrics().inputTokens()).isEqualTo(150L);
    assertThat(entity.metrics().outputTokens()).isEqualTo(75L);
    assertThat(entity.metrics().durationMs()).isEqualTo(300L);
    assertThat(entity.content()).hasSize(1);
    assertThat(entity.content().get(0).contentType()).isEqualTo(ContentType.TEXT);
    assertThat(entity.content().get(0).text()).isEqualTo("Hello from assistant");
    assertThat(entity.toolCalls()).hasSize(1);
    assertThat(entity.toolCalls().get(0).toolCallId()).isEqualTo("tc-1");
    assertThat(entity.toolCalls().get(0).toolName()).isEqualTo("myTool");
  }

  @Test
  void shouldMapNullContentAndToolCallsToEmptyLists() {
    final var dbModel = minimalDbModel(42L);
    dbModel.contentItems(null);
    dbModel.toolCallValues(null);

    final AgentInstanceHistoryEntity entity = AgentHistoryEntityMapper.toEntity(dbModel);

    assertThat(entity.content()).isEmpty();
    assertThat(entity.toolCalls()).isEmpty();
  }

  @Test
  void shouldReturnNullForNullDbModel() {
    assertThat(AgentHistoryEntityMapper.toEntity(null)).isNull();
  }

  @Test
  void shouldMapAllNullMetricsToNullMetrics() {
    // given — all three null means metrics were never provided
    final var dbModel = minimalDbModel(43L);
    dbModel.inputTokens(null);
    dbModel.outputTokens(null);
    dbModel.durationMs(null);

    // when
    final AgentInstanceHistoryEntity entity = AgentHistoryEntityMapper.toEntity(dbModel);

    // then
    assertThat(entity.metrics()).isNull();
  }

  @Test
  void shouldPreservePartialMetricsWhenOnlyDurationMsIsNull() {
    // given — inputTokens and outputTokens set, durationMs absent
    final var dbModel = minimalDbModel(44L);
    dbModel.inputTokens(100L);
    dbModel.outputTokens(200L);
    dbModel.durationMs(null);

    // when
    final AgentInstanceHistoryEntity entity = AgentHistoryEntityMapper.toEntity(dbModel);

    // then
    assertThat(entity.metrics()).isNotNull();
    assertThat(entity.metrics().inputTokens()).isEqualTo(100L);
    assertThat(entity.metrics().outputTokens()).isEqualTo(200L);
    assertThat(entity.metrics().durationMs()).isNull();
  }

  private AgentHistoryDbModel minimalDbModel(final long key) {
    final var model = new AgentHistoryDbModel();
    model.agentHistoryKey(key);
    model.agentInstanceKey(1L);
    model.elementInstanceKey(2L);
    model.processInstanceKey(3L);
    model.rootProcessInstanceKey(4L);
    model.processDefinitionKey(5L);
    model.processDefinitionId("process");
    model.tenantId("<default>");
    model.partitionId(1);
    model.jobKey(6L);
    model.jobLease("lease");
    model.loopIteration(1);
    model.role(AgentInstanceHistoryRole.USER);
    model.commitStatus(AgentInstanceHistoryCommitStatus.PENDING);
    model.producedAt(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
    model.inputTokens(0L);
    model.outputTokens(0L);
    model.durationMs(0L);
    model.contentItems(List.of());
    model.toolCallValues(List.of());
    return model;
  }
}
