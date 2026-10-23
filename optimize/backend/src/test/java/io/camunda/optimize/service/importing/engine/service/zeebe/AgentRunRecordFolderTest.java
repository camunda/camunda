/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.ModelTokensDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentDefinitionValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentLimitsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentMetricsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentToolValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunRecordFolderTest {

  private static final long AGENT_KEY = 700L;
  private static final long START = 1_700_000_000_000L;

  @Test
  void shouldTakeLatestConfigAndMetricsAndMapIdentityInC7Naming() {
    // given
    final var created = record(1, AgentInstanceIntent.CREATED, START, "bedrock", "claude");
    final var updated = record(2, AgentInstanceIntent.UPDATED, START + 1_000, "openai", "gpt");
    metrics(updated, 100, 40, 10, 0, 0, 2, 1);
    updated.getValue().setChangedAttributes(List.of("metrics", "status"));

    // when
    final AgentRunDto run = AgentRunRecordFolder.fold(List.of(updated, created)).run();

    // then
    assertThat(run.getAgentInstanceId()).isEqualTo(String.valueOf(AGENT_KEY));
    assertThat(run.getAgentDefinitionId()).isEqualTo("55");
    assertThat(run.getAgentId()).isEqualTo("loan:review");
    assertThat(run.getProcessDefinitionKey()).isEqualTo("loan");
    assertThat(run.getProcessDefinitionId()).isEqualTo("9001");
    assertThat(run.getProcessInstanceId()).isEqualTo("123");
    assertThat(run.getRootProcessInstanceId()).isEqualTo("120");
    assertThat(run.getFlowNodeInstanceIds()).containsExactly("124");
    assertThat(run.getModel()).isEqualTo("gpt");
    assertThat(run.getProvider()).isEqualTo("openai");
    assertThat(run.getModelCalls()).isEqualTo(2L);
    assertThat(run.getLastRecordPosition()).isEqualTo(2L);
  }

  @Test
  void shouldNotMoveLastActivityOnCompletionOrConfigOnlyUpdate() {
    // given
    final var created = record(1, AgentInstanceIntent.CREATED, START, "bedrock", "claude");
    final var thinking = record(2, AgentInstanceIntent.UPDATED, START + 5_000, "bedrock", "claude");
    thinking.getValue().setChangedAttributes(List.of("metrics", "status"));
    final var configCommit = record(3, AgentInstanceIntent.UPDATED, START + 6_000, "openai", "gpt");
    configCommit.getValue().setChangedAttributes(List.of("model", "provider"));
    final var completed = record(4, AgentInstanceIntent.COMPLETED, START + 60_000, "openai", "gpt");
    completed.getValue().setChangedAttributes(List.of("status"));

    // when
    final AgentRunDto run =
        AgentRunRecordFolder.fold(List.of(created, thinking, configCommit, completed)).run();

    // then
    assertThat(run.getStartDate().toInstant().toEpochMilli()).isEqualTo(START);
    assertThat(run.getLastActivityDate().toInstant().toEpochMilli()).isEqualTo(START + 5_000);
    assertThat(run.getEndDate().toInstant().toEpochMilli()).isEqualTo(START + 60_000);
    assertThat(run.getDurationMs()).isEqualTo(5_000L);
    assertThat(run.getStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void shouldStoreUnreportedMetricsAsNullAndSumTotalTokensWithoutReasoning() {
    // given
    final var created = record(1, AgentInstanceIntent.CREATED, START, "openai", "o3");
    metrics(created, 1_000, 300, 200, -1, 50, 1, -1);

    // when
    final AgentRunDto run = AgentRunRecordFolder.fold(List.of(created)).run();

    // then
    assertThat(run.getCacheCreationTokens()).isNull();
    assertThat(run.getToolCalls()).isNull();
    assertThat(run.getReasoningTokens()).isEqualTo(200L);
    // input + output + cache read; reasoning is already inside output
    assertThat(run.getTotalTokens()).isEqualTo(1_350L);
  }

  @Test
  void shouldLeaveTotalTokensNullWhenNoTokenWasReported() {
    // given
    final var created = record(1, AgentInstanceIntent.CREATED, START, "openai", "o3");
    metrics(created, -1, -1, -1, -1, -1, -1, -1);

    // when
    final AgentRunDto run = AgentRunRecordFolder.fold(List.of(created)).run();

    // then
    assertThat(run.getTotalTokens()).isNull();
    assertThat(run.getHitLimit()).isFalse();
  }

  @Test
  void shouldSplitTokensByTheModelInEffectWhenTheyWereReported() {
    // given
    final var created = record(1, AgentInstanceIntent.CREATED, START, "bedrock", "claude");
    final var first = record(2, AgentInstanceIntent.UPDATED, START + 1_000, "bedrock", "claude");
    metrics(first, 100, 50, 0, 10, 0, 1, 0);
    first.getValue().setChangedAttributes(List.of("metrics"));
    final var second = record(3, AgentInstanceIntent.UPDATED, START + 2_000, "openai", "gpt");
    metrics(second, 160, 80, 0, 10, 20, 2, 1);
    second.getValue().setChangedAttributes(List.of("metrics"));

    // when
    final AgentRunDto run = AgentRunRecordFolder.fold(List.of(created, first, second)).run();

    // then
    assertThat(run.getTokensByModel())
        .extracting(ModelTokensDto::getModel, ModelTokensDto::getTotalTokens)
        .containsExactlyInAnyOrder(tuple("claude", 160L), tuple("gpt", 110L));
    assertThat(run.getTotalTokens()).isEqualTo(270L);
    assertThat(run.getLastMetricTotals().getInputTokens()).isEqualTo(160L);
  }

  @Test
  void shouldFlagReachedLimitsOnlyWhenTheyAreSet() {
    // given
    final var created = record(1, AgentInstanceIntent.CREATED, START, "openai", "gpt");
    metrics(created, 10, 10, 0, 0, 0, 5, 3);
    limits(created, -1L, 5, 10);

    // when
    final AgentRunDto run = AgentRunRecordFolder.fold(List.of(created)).run();

    // then
    assertThat(run.getHitLimit()).isTrue();
    assertThat(run.getLimitHit()).containsExactly("maxModelCalls");
    assertThat(run.getMaxTokens()).isEqualTo(-1L);
  }

  @Test
  void shouldCollectToolElementsFromAnyRecordCarryingThem() {
    // given
    final var created = record(1, AgentInstanceIntent.CREATED, START, "openai", "gpt");
    created.getValue().setTools(List.of(tool("search", "searchTask")));
    final var updated = record(2, AgentInstanceIntent.UPDATED, START + 1_000, "openai", "gpt");
    updated.getValue().setTools(List.of(tool("search", "searchTask"), tool("score", "scoreTask")));

    // when
    final AgentRunDto run = AgentRunRecordFolder.fold(List.of(created, updated)).run();

    // then
    assertThat(run.getToolFlowNodeIds()).containsExactly("searchTask", "scoreTask");
    assertThat(run.getToolCallsByTool())
        .extracting(AgentRunDto.ToolCallsDto::getToolName)
        .containsExactly("search", "score");
  }

  private static ZeebeAgentInstanceRecordDto record(
      final long position,
      final AgentInstanceIntent intent,
      final long timestamp,
      final String provider,
      final String model) {
    final ZeebeAgentInstanceDataDto value = new ZeebeAgentInstanceDataDto();
    value.setAgentInstanceKey(AGENT_KEY);
    value.setAgentDefinitionKey(55L);
    value.setElementId("review");
    value.setElementInstanceKey(124L);
    value.setElementInstanceKeys(List.of(124L));
    value.setBpmnProcessId("loan");
    value.setProcessDefinitionKey(9001L);
    value.setProcessDefinitionVersion(3);
    value.setProcessInstanceKey(123L);
    value.setRootProcessInstanceKey(120L);
    value.setStatus(
        intent == AgentInstanceIntent.COMPLETED
            ? AgentInstanceStatus.COMPLETED
            : AgentInstanceStatus.THINKING);
    final AgentDefinitionValueDto definition = new AgentDefinitionValueDto();
    definition.setProvider(provider);
    definition.setModel(model);
    value.setDefinition(definition);
    final ZeebeAgentInstanceRecordDto record = new ZeebeAgentInstanceRecordDto();
    record.setKey(AGENT_KEY);
    record.setPosition(position);
    record.setTimestamp(timestamp);
    record.setIntent(intent);
    record.setValue(value);
    return record;
  }

  private static void metrics(
      final ZeebeAgentInstanceRecordDto record,
      final long input,
      final long output,
      final long reasoning,
      final long cacheCreation,
      final long cacheRead,
      final int modelCalls,
      final int toolCalls) {
    final AgentMetricsValueDto metrics = new AgentMetricsValueDto();
    metrics.setInputTokens(input);
    metrics.setOutputTokens(output);
    metrics.setReasoningTokenCount(reasoning);
    metrics.setCacheCreationTokenCount(cacheCreation);
    metrics.setCacheReadTokenCount(cacheRead);
    metrics.setModelCalls(modelCalls);
    metrics.setToolCalls(toolCalls);
    record.getValue().setMetrics(metrics);
  }

  private static void limits(
      final ZeebeAgentInstanceRecordDto record,
      final long maxTokens,
      final int maxModelCalls,
      final int maxToolCalls) {
    final AgentLimitsValueDto limits = new AgentLimitsValueDto();
    limits.setMaxTokens(maxTokens);
    limits.setMaxModelCalls(maxModelCalls);
    limits.setMaxToolCalls(maxToolCalls);
    record.getValue().setLimits(limits);
  }

  private static AgentToolValueDto tool(final String name, final String elementId) {
    final AgentToolValueDto tool = new AgentToolValueDto();
    tool.setName(name);
    tool.setElementId(elementId);
    return tool;
  }
}
