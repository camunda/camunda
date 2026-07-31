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

    final var dbModel =
        new AgentHistoryDbModel.Builder()
            .agentHistoryKey(100L)
            .agentInstanceKey(200L)
            .elementInstanceKey(300L)
            .processInstanceKey(400L)
            .rootProcessInstanceKey(500L)
            .processDefinitionKey(600L)
            .processDefinitionId("myProcess")
            .tenantId("<default>")
            .partitionId(1)
            .jobKey(700L)
            .jobLease("lease-abc")
            .loopIteration(3)
            .role(AgentInstanceHistoryRole.ASSISTANT)
            .commitStatus(AgentInstanceHistoryCommitStatus.COMMITTED)
            .producedAt(producedAt)
            .inputTokens(150L)
            .outputTokens(75L)
            .durationMs(300L)
            .contentItems(
                List.of(new ContentItem(ContentType.TEXT, "Hello from assistant", null, null)))
            .toolCallValues(
                List.of(new ToolCall("tc-1", "myTool", "Task_1", Map.of("key", "value"))))
            .build();

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
    final var dbModel = minimalDbModel(42L).contentItems(null).toolCallValues(null).build();

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
    final var dbModel =
        minimalDbModel(43L).inputTokens(null).outputTokens(null).durationMs(null).build();

    // when
    final AgentInstanceHistoryEntity entity = AgentHistoryEntityMapper.toEntity(dbModel);

    // then
    assertThat(entity.metrics()).isNull();
  }

  @Test
  void shouldPreservePartialMetricsWhenOnlyDurationMsIsNull() {
    // given — inputTokens and outputTokens set, durationMs absent
    final var dbModel =
        minimalDbModel(44L).inputTokens(100L).outputTokens(200L).durationMs(null).build();

    // when
    final AgentInstanceHistoryEntity entity = AgentHistoryEntityMapper.toEntity(dbModel);

    // then
    assertThat(entity.metrics()).isNotNull();
    assertThat(entity.metrics().inputTokens()).isEqualTo(100L);
    assertThat(entity.metrics().outputTokens()).isEqualTo(200L);
    assertThat(entity.metrics().durationMs()).isNull();
  }

  private AgentHistoryDbModel.Builder minimalDbModel(final long key) {
    return new AgentHistoryDbModel.Builder()
        .agentHistoryKey(key)
        .agentInstanceKey(1L)
        .elementInstanceKey(2L)
        .processInstanceKey(3L)
        .rootProcessInstanceKey(4L)
        .processDefinitionKey(5L)
        .processDefinitionId("process")
        .tenantId("<default>")
        .partitionId(1)
        .jobKey(6L)
        .jobLease("lease")
        .loopIteration(1)
        .role(AgentInstanceHistoryRole.USER)
        .commitStatus(AgentInstanceHistoryCommitStatus.PENDING)
        .producedAt(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
        .inputTokens(0L)
        .outputTokens(0L)
        .durationMs(0L)
        .contentItems(List.of())
        .toolCallValues(List.of());
  }
}
