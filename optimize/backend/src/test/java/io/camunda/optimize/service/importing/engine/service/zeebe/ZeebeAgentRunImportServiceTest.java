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
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentDefinitionValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentLimitsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentMetricsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZeebeAgentRunImportServiceTest {

  private static final long RUN_KEY = 2251799813685300L;
  private static final long OTHER_RUN_KEY = 2251799813685400L;
  private static final long BASE_TIME = 1_700_000_000_000L;

  @Test
  void shouldFoldRecordsOfOneRunIntoTheNewestState() {
    // given
    final var created =
        record(RUN_KEY, AgentInstanceIntent.CREATED, 10, 0, AgentInstanceStatus.INITIALIZING);
    final var updated =
        record(RUN_KEY, AgentInstanceIntent.UPDATED, 11, 1, AgentInstanceStatus.THINKING);
    usage(updated, 100, 40, 5, 20, 10, 2, 3);
    final var completed =
        record(RUN_KEY, AgentInstanceIntent.COMPLETED, 12, 2, AgentInstanceStatus.COMPLETED);
    usage(completed, 100, 40, 5, 20, 10, 2, 3);

    // when
    final List<AgentRunDto> runs =
        ZeebeAgentRunImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(created, updated, completed));

    // then
    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.getAgentInstanceId()).isEqualTo(String.valueOf(RUN_KEY));
              assertThat(run.getAgentId()).isEqualTo("support:agent");
              assertThat(run.getFlowNodeId()).isEqualTo("agent");
              assertThat(run.getFlowNodeInstanceIds()).containsExactly("7");
              assertThat(run.getProcessDefinitionKey()).isEqualTo("support");
              assertThat(run.getProcessDefinitionId()).isEqualTo("3");
              assertThat(run.getProcessDefinitionVersion()).isEqualTo("2");
              assertThat(run.getProcessInstanceId()).isEqualTo("5");
              assertThat(run.getRootProcessInstanceId()).isEqualTo("4");
              assertThat(run.getTenantId()).isEqualTo("<default>");
              assertThat(run.getModel()).isEqualTo("claude");
              assertThat(run.getProvider()).isEqualTo("anthropic");
              assertThat(run.getStatus()).isEqualTo("COMPLETED");
              assertThat(run.getInputTokens()).isEqualTo(100L);
              assertThat(run.getOutputTokens()).isEqualTo(40L);
              assertThat(run.getReasoningTokens()).isEqualTo(5L);
              assertThat(run.getCacheCreationTokens()).isEqualTo(20L);
              assertThat(run.getCacheReadTokens()).isEqualTo(10L);
              assertThat(run.getTotalTokens()).isEqualTo(170L);
              assertThat(run.getModelCalls()).isEqualTo(2L);
              assertThat(run.getToolCalls()).isEqualTo(3L);
              assertThat(run.getStartDate()).isEqualTo(date(0));
              assertThat(run.getEndDate()).isEqualTo(date(2));
              assertThat(run.getLastRecordPosition()).isEqualTo(12L);
              assertThat(run.getLimitHit()).isEmpty();
            });
  }

  @Test
  void shouldOrderRecordsByPositionNotByPageOrderOrTimestamp() {
    // given two records with the same timestamp, delivered newest first
    final var older =
        record(RUN_KEY, AgentInstanceIntent.UPDATED, 20, 1, AgentInstanceStatus.THINKING);
    usage(older, 10, 0, 0, 0, 0, 1, 0);
    final var newer =
        record(RUN_KEY, AgentInstanceIntent.UPDATED, 21, 1, AgentInstanceStatus.TOOL_CALLING);
    usage(newer, 30, 5, 0, 0, 0, 2, 1);

    // when
    final List<AgentRunDto> runs =
        ZeebeAgentRunImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(newer, older));

    // then
    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.getStatus()).isEqualTo("TOOL_CALLING");
              assertThat(run.getInputTokens()).isEqualTo(30L);
              assertThat(run.getModelCalls()).isEqualTo(2L);
              assertThat(run.getLastRecordPosition()).isEqualTo(21L);
            });
  }

  @Test
  void shouldTakeTotalsFromCompletedRecordWhenEarlierRecordsAreGone() {
    // given only the COMPLETED record, as for a run that started before Zeebe removed its records
    final var completed =
        record(RUN_KEY, AgentInstanceIntent.COMPLETED, 50, 9, AgentInstanceStatus.COMPLETED);
    usage(completed, 500, 200, 0, 0, 0, 6, 4);

    // when
    final List<AgentRunDto> runs =
        ZeebeAgentRunImportService.filterAndMapZeebeRecordsToOptimizeEntities(List.of(completed));

    // then
    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.getTotalTokens()).isEqualTo(700L);
              assertThat(run.getModelCalls()).isEqualTo(6L);
              assertThat(run.getStartDate()).isEqualTo(date(9));
              assertThat(run.getEndDate()).isEqualTo(date(9));
            });
  }

  @Test
  void shouldListEveryLimitTheRunReached() {
    // given
    final var completed =
        record(RUN_KEY, AgentInstanceIntent.COMPLETED, 30, 3, AgentInstanceStatus.COMPLETED);
    usage(completed, 800, 200, 0, 0, 0, 5, 2);
    limits(completed, 1000, 5, 10);

    // when
    final List<AgentRunDto> runs =
        ZeebeAgentRunImportService.filterAndMapZeebeRecordsToOptimizeEntities(List.of(completed));

    // then
    assertThat(runs)
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.getMaxTokens()).isEqualTo(1000L);
              assertThat(run.getMaxModelCalls()).isEqualTo(5L);
              assertThat(run.getMaxToolCalls()).isEqualTo(10L);
              assertThat(run.getLimitHit())
                  .containsExactly(
                      ZeebeAgentRunImportService.LIMIT_MODEL_CALLS,
                      ZeebeAgentRunImportService.LIMIT_TOKENS);
            });
  }

  @Test
  void shouldNotReportLimitsThatAreNotSet() {
    // given limits of -1, which mean no limit
    final var completed =
        record(RUN_KEY, AgentInstanceIntent.COMPLETED, 30, 3, AgentInstanceStatus.COMPLETED);
    usage(completed, 800, 200, 0, 0, 0, 5, 2);

    // when
    final List<AgentRunDto> runs =
        ZeebeAgentRunImportService.filterAndMapZeebeRecordsToOptimizeEntities(List.of(completed));

    // then
    assertThat(runs).singleElement().satisfies(run -> assertThat(run.getLimitHit()).isEmpty());
  }

  @Test
  void shouldSetLastActivityToTheNewestRecordThatChangedUsage() {
    // given the last record only changes the status
    final var created =
        record(RUN_KEY, AgentInstanceIntent.CREATED, 10, 0, AgentInstanceStatus.INITIALIZING);
    final var updated =
        record(RUN_KEY, AgentInstanceIntent.UPDATED, 11, 4, AgentInstanceStatus.THINKING);
    usage(updated, 100, 40, 0, 0, 0, 1, 0);
    final var completed =
        record(RUN_KEY, AgentInstanceIntent.COMPLETED, 12, 8, AgentInstanceStatus.COMPLETED);
    usage(completed, 100, 40, 0, 0, 0, 1, 0);

    // when
    final List<AgentRunDto> runs =
        ZeebeAgentRunImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(created, updated, completed));

    // then
    assertThat(runs)
        .singleElement()
        .satisfies(run -> assertThat(run.getLastActivityDate()).isEqualTo(date(4)));
  }

  @Test
  void shouldKeepRunsApartAndSkipOtherIntents() {
    // given
    final var first =
        record(RUN_KEY, AgentInstanceIntent.CREATED, 10, 0, AgentInstanceStatus.INITIALIZING);
    final var second =
        record(OTHER_RUN_KEY, AgentInstanceIntent.CREATED, 11, 0, AgentInstanceStatus.IDLE);
    final var command =
        record(OTHER_RUN_KEY, AgentInstanceIntent.UPDATE, 12, 1, AgentInstanceStatus.THINKING);

    // when
    final List<AgentRunDto> runs =
        ZeebeAgentRunImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(first, second, command));

    // then
    assertThat(runs)
        .extracting(AgentRunDto::getAgentInstanceId, AgentRunDto::getStatus)
        .containsExactlyInAnyOrder(
            tuple(String.valueOf(RUN_KEY), "INITIALIZING"),
            tuple(String.valueOf(OTHER_RUN_KEY), "IDLE"));
  }

  private static ZeebeAgentInstanceRecordDto record(
      final long runKey,
      final AgentInstanceIntent intent,
      final long position,
      final long secondsAfterStart,
      final AgentInstanceStatus status) {
    final ZeebeAgentInstanceDataDto value = new ZeebeAgentInstanceDataDto();
    value.setAgentInstanceKey(runKey);
    value.setElementId("agent");
    value.setElementInstanceKey(7L);
    value.setElementInstanceKeys(List.of(7L));
    value.setBpmnProcessId("support");
    value.setProcessDefinitionKey(3L);
    value.setProcessDefinitionVersion(2);
    value.setRootProcessInstanceKey(4L);
    value.setProcessInstanceKey(5L);
    value.setStatus(status);
    final AgentDefinitionValueDto definition = new AgentDefinitionValueDto();
    definition.setModel("claude");
    definition.setProvider("anthropic");
    value.setDefinition(definition);
    final ZeebeAgentInstanceRecordDto record = new ZeebeAgentInstanceRecordDto();
    record.setKey(runKey);
    record.setIntent(intent);
    record.setPosition(position);
    record.setTimestamp(BASE_TIME + secondsAfterStart * 1000);
    record.setValue(value);
    return record;
  }

  private static void usage(
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

  private static OffsetDateTime date(final long secondsAfterStart) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(BASE_TIME + secondsAfterStart * 1000), ZoneId.systemDefault());
  }
}
