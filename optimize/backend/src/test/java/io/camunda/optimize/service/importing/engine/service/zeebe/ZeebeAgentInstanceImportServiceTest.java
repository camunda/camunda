/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent.COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentMetricsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentToolValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ZeebeAgentInstanceImportServiceTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long AGENT_KEY = 200L;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConfigurationService configurationService;

  @Mock private ProcessInstanceWriter processInstanceWriter;
  @Mock private ProcessDefinitionReader processDefinitionReader;
  @Mock private DatabaseClient databaseClient;

  private ZeebeAgentInstanceImportService underTest;

  @BeforeEach
  void setUp() {
    when(configurationService.getJobExecutorQueueSize()).thenReturn(10);
    when(configurationService.getJobExecutorThreadCount()).thenReturn(1);
    underTest =
        new ZeebeAgentInstanceImportService(
            configurationService,
            processInstanceWriter,
            1,
            processDefinitionReader,
            databaseClient);
  }

  @Test
  void shouldGroupRecordsByProcessInstanceKey() {
    // given
    final List<ZeebeAgentInstanceRecordDto> records =
        List.of(
            record(
                AGENT_KEY, 1000L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.INITIALIZING, CREATED),
            record(
                AGENT_KEY + 1,
                1000L,
                PROCESS_INSTANCE_KEY + 1,
                AgentInstanceStatus.INITIALIZING,
                CREATED));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(records);

    // then
    assertThat(result)
        .as("Each distinct processInstanceKey produces one ProcessInstanceDto")
        .hasSize(2)
        .extracting(ProcessInstanceDto::getProcessInstanceId)
        .containsExactlyInAnyOrder(
            String.valueOf(PROCESS_INSTANCE_KEY), String.valueOf(PROCESS_INSTANCE_KEY + 1));
  }

  @Test
  void shouldFilterOutCommandsAndOnlyImportEvents() {
    // given: Mix of commands (CREATE, UPDATE, COMPLETE) and events (CREATED, UPDATED, COMPLETED)
    final List<ZeebeAgentInstanceRecordDto> records =
        List.of(
            record(
                AGENT_KEY,
                1000L,
                PROCESS_INSTANCE_KEY,
                AgentInstanceStatus.INITIALIZING,
                AgentInstanceIntent.CREATE), // ← Command (should be filtered out)
            record(
                AGENT_KEY, 1100L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.INITIALIZING, CREATED),
            record(
                AGENT_KEY,
                2000L,
                PROCESS_INSTANCE_KEY,
                AgentInstanceStatus.THINKING,
                AgentInstanceIntent.UPDATE), // ← Command (should be filtered out)
            record(AGENT_KEY, 2100L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.THINKING, UPDATED),
            record(
                AGENT_KEY,
                3000L,
                PROCESS_INSTANCE_KEY,
                AgentInstanceStatus.COMPLETED,
                AgentInstanceIntent.COMPLETE), // ← Command (should be filtered out)
            record(
                AGENT_KEY, 3100L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.COMPLETED, COMPLETED));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(records);

    // then: Only 1 process instance with 1 agent instance built from 3 events (commands filtered)
    assertThat(result).hasSize(1);
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getStatus()).isEqualTo("COMPLETED");
    // If commands were included, we'd have 6 records processed instead of 3
  }

  @Test
  void shouldSetAgentSkeletonFieldsFromRecord() {
    // given
    final ZeebeAgentInstanceDataDto data = baseData(PROCESS_INSTANCE_KEY);
    data.setElementId("agent-element");
    data.setProcessDefinitionVersion(3);

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(record(AGENT_KEY, 1000L, data, AgentInstanceStatus.INITIALIZING, CREATED)));

    // then
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getAgentInstanceId()).isEqualTo(String.valueOf(AGENT_KEY));
    assertThat(agent.getFlowNodeId()).isEqualTo("agent-element");
  }

  @Test
  void shouldSetDefinitionOnlyFromFirstRecord() {
    // given — CREATED carries the real model; UPDATED carries a different (wrong) model
    final ZeebeAgentInstanceDataDto createdData = baseData(PROCESS_INSTANCE_KEY);
    createdData.setDefinition(definition("gpt-4o", "openai"));

    final ZeebeAgentInstanceDataDto updatedData = baseData(PROCESS_INSTANCE_KEY);
    updatedData.setDefinition(definition("should-not-overwrite", "wrong"));
    updatedData.setStatus(AgentInstanceStatus.THINKING);

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(AGENT_KEY, 1000L, createdData, AgentInstanceStatus.INITIALIZING, CREATED),
                record(AGENT_KEY, 2000L, updatedData, AgentInstanceStatus.THINKING, UPDATED)));

    // then
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getDefinition().getModel()).isEqualTo("gpt-4o");
    assertThat(agent.getDefinition().getProvider()).isEqualTo("openai");
  }

  @Test
  void shouldUpdateStatusToLatestRecord() {
    // given
    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(
                    AGENT_KEY, 2000L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.THINKING, UPDATED),
                record(
                    AGENT_KEY,
                    3000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.COMPLETED,
                    COMPLETED)));

    // then
    assertThat(singleAgent(result).getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED.name());
  }

  @Test
  void shouldUpdateMetricsToLatestRecord() {
    // given
    final ZeebeAgentInstanceDataDto updatedData = baseData(PROCESS_INSTANCE_KEY);
    updatedData.setMetrics(metrics(512, 148, 1, 1));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(AGENT_KEY, 2000L, updatedData, AgentInstanceStatus.THINKING, UPDATED)));

    // then
    final AgentInstanceDto.AgentMetricsDto m = singleAgent(result).getMetrics();
    assertThat(m.getInputTokens()).isEqualTo(512L);
    assertThat(m.getOutputTokens()).isEqualTo(148L);
    assertThat(m.getModelCalls()).isEqualTo(1L);
    assertThat(m.getToolCalls()).isEqualTo(1L);
  }

  @Test
  void shouldReplaceToolsWithLatestRecord() {
    // given
    final ZeebeAgentInstanceDataDto updatedData = baseData(PROCESS_INSTANCE_KEY);
    updatedData.setTools(List.of(tool("tool-a"), tool("tool-b")));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(AGENT_KEY, 2000L, updatedData, AgentInstanceStatus.THINKING, UPDATED)));

    // then
    assertThat(singleAgent(result).getTools())
        .extracting(AgentInstanceDto.AgentToolDto::getName)
        .containsExactlyInAnyOrder("tool-a", "tool-b");
  }

  @Test
  void shouldSetStartDateFromFirstRecord() {
    // given — CREATED + UPDATED; startDate must come from the CREATED record
    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(
                    AGENT_KEY,
                    2000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.THINKING,
                    UPDATED)));

    // then
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getStartDate()).isNotNull();
    assertThat(agent.getLastUpdatedDate()).isNotNull();
  }

  @Test
  void shouldSetEndDateAndDurationOnCompleted() {
    // given
    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(
                    AGENT_KEY,
                    4000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.COMPLETED,
                    COMPLETED)));

    // then
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getEndDate()).isNotNull();
    assertThat(agent.getTotalDurationInMs())
        .as("Duration must be completedEpoch - createdEpoch")
        .isEqualTo(3000L);
  }

  @Test
  void shouldNotSetEndDateForNonTerminalIntents() {
    // given
    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(
                    AGENT_KEY,
                    2000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.THINKING,
                    UPDATED)));

    // then
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getEndDate()).isNull();
    assertThat(agent.getTotalDurationInMs()).isNull();
  }

  @Test
  void shouldAccumulateAggregateMetricsAcrossMultipleAgents() {
    // given — two distinct agents in the same process instance, each with metrics
    final long agent2Key = AGENT_KEY + 1;
    final ZeebeAgentInstanceDataDto data1 = baseData(PROCESS_INSTANCE_KEY);
    data1.setMetrics(metrics(100, 50, 2, 3));
    final ZeebeAgentInstanceDataDto data2 = baseData(PROCESS_INSTANCE_KEY);
    data2.setMetrics(metrics(200, 75, 4, 6));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(AGENT_KEY, 1000L, data1, AgentInstanceStatus.THINKING, CREATED),
                record(agent2Key, 1000L, data2, AgentInstanceStatus.THINKING, CREATED)));

    // then
    final ProcessInstanceDto instance = result.getFirst();
    assertThat(instance.getAgentTotalInputTokens()).isEqualTo(300L);
    assertThat(instance.getAgentTotalOutputTokens()).isEqualTo(125L);
    assertThat(instance.getAgentTotalModelCalls()).isEqualTo(6L);
    assertThat(instance.getAgentTotalToolCalls()).isEqualTo(9L);
  }

  @Test
  void shouldComputeAgentTotalTokensAsInputPlusOutput() {
    // given
    final ZeebeAgentInstanceDataDto data = baseData(PROCESS_INSTANCE_KEY);
    data.setMetrics(metrics(300, 125, 0, 0));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(record(AGENT_KEY, 1000L, data, AgentInstanceStatus.THINKING, CREATED)));

    // then
    assertThat(result.getFirst().getAgentTotalTokens()).isEqualTo(425L);
  }

  @Test
  void shouldProcessAllIntentsWithoutFiltering() {
    // given — CREATED, UPDATED, and COMPLETED records for the same agent
    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(
                    AGENT_KEY, 2000L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.THINKING, UPDATED),
                record(
                    AGENT_KEY,
                    3000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.COMPLETED,
                    COMPLETED)));

    // then — all three records were processed into one consolidated agent instance
    assertThat(result).hasSize(1);
    assertThat(singleAgent(result).getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED.name());
    assertThat(singleAgent(result).getTotalDurationInMs()).isEqualTo(2000L);
  }

  @Test
  void shouldSortRecordsByTimestampBeforeProcessing() {
    // given — records arrive out of order (COMPLETED before CREATED in the list)
    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    3000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.COMPLETED,
                    COMPLETED),
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED)));

    // then — sort ensures CREATED is processed before COMPLETED so duration can be computed
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getStartDate())
        .as("startDate must be set from the CREATED record")
        .isNotNull();
    assertThat(agent.getTotalDurationInMs())
        .as("duration must be computed even when input list is unordered, because sort runs first")
        .isEqualTo(2000L);
  }

  @Test
  void shouldMapToolNamesFromRecord() {
    // given
    final ZeebeAgentInstanceDataDto data = baseData(PROCESS_INSTANCE_KEY);
    data.setTools(List.of(tool("MCP_slack___post_message"), tool("extract_line_items")));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(record(AGENT_KEY, 1000L, data, AgentInstanceStatus.TOOL_CALLING, UPDATED)));

    // then
    assertThat(singleAgent(result).getTools())
        .extracting(AgentInstanceDto.AgentToolDto::getName)
        .containsExactlyInAnyOrder("MCP_slack___post_message", "extract_line_items");
  }

  // --- helpers ---

  private AgentInstanceDto singleAgent(final List<ProcessInstanceDto> result) {
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getAgentInstances()).hasSize(1);
    return result.getFirst().getAgentInstances().getFirst();
  }

  private ZeebeAgentInstanceRecordDto record(
      final long key,
      final long timestamp,
      final long processInstanceKey,
      final AgentInstanceStatus status,
      final AgentInstanceIntent intent) {
    final ZeebeAgentInstanceDataDto data = baseData(processInstanceKey);
    return record(key, timestamp, data, status, intent);
  }

  private ZeebeAgentInstanceRecordDto record(
      final long key,
      final long timestamp,
      final ZeebeAgentInstanceDataDto data,
      final AgentInstanceStatus status,
      final AgentInstanceIntent intent) {
    data.setStatus(status);
    final ZeebeAgentInstanceRecordDto dto = new ZeebeAgentInstanceRecordDto();
    dto.setKey(key);
    dto.setTimestamp(timestamp);
    dto.setIntent(intent);
    dto.setValue(data);
    return dto;
  }

  private ZeebeAgentInstanceDataDto baseData(final long processInstanceKey) {
    final ZeebeAgentInstanceDataDto data = new ZeebeAgentInstanceDataDto();
    data.setProcessInstanceKey(processInstanceKey);
    data.setProcessDefinitionKey(999L);
    data.setBpmnProcessId("testProcess");
    data.setTenantId("<default>");
    data.setElementId("agent-task");
    data.setProcessDefinitionVersion(1);
    data.setDefinition(definition("gpt-4o", "openai"));
    return data;
  }

  private ZeebeAgentInstanceDataDto.AgentDefinitionValueDto definition(
      final String model, final String provider) {
    final ZeebeAgentInstanceDataDto.AgentDefinitionValueDto def =
        new ZeebeAgentInstanceDataDto.AgentDefinitionValueDto();
    def.setModel(model);
    def.setProvider(provider);
    return def;
  }

  private AgentMetricsValueDto metrics(
      final long inputTokens, final long outputTokens, final int modelCalls, final int toolCalls) {
    final AgentMetricsValueDto m = new AgentMetricsValueDto();
    m.setInputTokens(inputTokens);
    m.setOutputTokens(outputTokens);
    m.setModelCalls(modelCalls);
    m.setToolCalls(toolCalls);
    return m;
  }

  private AgentToolValueDto tool(final String name) {
    final AgentToolValueDto t = new AgentToolValueDto();
    t.setName(name);
    return t;
  }

  @Test
  void shouldHandleNullMetricsWhenComputingAggregates() {
    // given: two agents, one with null metrics
    final ZeebeAgentInstanceDataDto dataWithoutMetrics = baseData(PROCESS_INSTANCE_KEY);
    dataWithoutMetrics.setMetrics(null);

    final ZeebeAgentInstanceDataDto dataWithMetrics = baseData(PROCESS_INSTANCE_KEY);
    dataWithMetrics.setMetrics(metrics(100, 200, 1, 2));

    final var recordsWithNullMetrics =
        List.of(
            record(AGENT_KEY, 1000L, dataWithoutMetrics, AgentInstanceStatus.INITIALIZING, CREATED),
            record(AGENT_KEY, 2000L, dataWithoutMetrics, AgentInstanceStatus.COMPLETED, COMPLETED),
            record(
                AGENT_KEY + 1, 1500L, dataWithMetrics, AgentInstanceStatus.INITIALIZING, CREATED),
            record(
                AGENT_KEY + 1, 2500L, dataWithMetrics, AgentInstanceStatus.COMPLETED, COMPLETED));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(recordsWithNullMetrics);

    // then: aggregates only count the agent with metrics
    assertThat(result)
        .hasSize(1)
        .first()
        .satisfies(
            aggregates -> {
              assertThat(aggregates.getAgentTotalInputTokens()).isEqualTo(100);
              assertThat(aggregates.getAgentTotalOutputTokens()).isEqualTo(200);
              assertThat(aggregates.getAgentTotalTokens()).isEqualTo(300);
              assertThat(aggregates.getAgentTotalModelCalls()).isEqualTo(1);
              assertThat(aggregates.getAgentTotalToolCalls()).isEqualTo(2);
            });
  }

  @Test
  void shouldPreservePriorStatusWhenLaterRecordHasNullStatus() {
    // given — second record has null status (malformed); first carries a real status
    final ZeebeAgentInstanceDataDto dataWithNullStatus = baseData(PROCESS_INSTANCE_KEY);
    final ZeebeAgentInstanceRecordDto firstRecord =
        record(AGENT_KEY, 1000L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.THINKING, UPDATED);
    final ZeebeAgentInstanceRecordDto recordWithNullStatus =
        record(AGENT_KEY, 2000L, dataWithNullStatus, null, UPDATED);

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(firstRecord, recordWithNullStatus));

    // then — no NPE; prior status preserved
    assertThat(singleAgent(result).getStatus()).isEqualTo(AgentInstanceStatus.THINKING.name());
  }

  @Test
  void shouldHandleNullElementInstanceKeys() {
    // given — record where elementInstanceKeys was deserialized as null
    final ZeebeAgentInstanceDataDto data = baseData(PROCESS_INSTANCE_KEY);
    data.setElementInstanceKeys(null);

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(record(AGENT_KEY, 1000L, data, AgentInstanceStatus.INITIALIZING, CREATED)));

    // then — no NPE; flowNodeInstanceIds is empty
    assertThat(singleAgent(result).getFlowNodeInstanceIds()).isEmpty();
  }

  @Test
  void shouldKeepPriorToolsWhenLaterRecordHasEmptyTools() {
    // given — first record has tools; second carries an empty tools list
    final ZeebeAgentInstanceDataDto firstData = baseData(PROCESS_INSTANCE_KEY);
    firstData.setTools(List.of(tool("tool-a"), tool("tool-b")));

    final ZeebeAgentInstanceDataDto laterData = baseData(PROCESS_INSTANCE_KEY);
    laterData.setTools(List.of());

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(AGENT_KEY, 1000L, firstData, AgentInstanceStatus.TOOL_DISCOVERY, CREATED),
                record(AGENT_KEY, 2000L, laterData, AgentInstanceStatus.THINKING, UPDATED)));

    // then — tools from the first record are preserved (latest-non-empty-wins)
    assertThat(singleAgent(result).getTools())
        .extracting(AgentInstanceDto.AgentToolDto::getName)
        .containsExactlyInAnyOrder("tool-a", "tool-b");
  }

  @Test
  void shouldNotSetStartDateOrDefinitionWhenBatchHasNoCreatedRecord() {
    // given — only UPDATED records, no CREATED (e.g. CREATED imported in a prior batch)
    final ZeebeAgentInstanceDataDto updatedData = baseData(PROCESS_INSTANCE_KEY);
    updatedData.setDefinition(definition("should-not-be-mapped", "wrong"));

    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(AGENT_KEY, 1000L, updatedData, AgentInstanceStatus.THINKING, UPDATED),
                record(AGENT_KEY, 2000L, updatedData, AgentInstanceStatus.THINKING, UPDATED)));

    // then — startDate and definition stay null/default; only CREATED-bearing batches seed them
    final AgentInstanceDto agent = singleAgent(result);
    assertThat(agent.getStartDate())
        .as("startDate must only be seeded by a CREATED record")
        .isNull();
    assertThat(agent.getDefinition())
        .as("definition must only be mapped from a CREATED record")
        .isNull();
    assertThat(agent.getLastUpdatedDate()).isNotNull();
    assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.THINKING.name());
  }

  @Test
  void shouldMergeRecordsWithDuplicateAgentKeysIntoSingleAgentInstance() {
    // given — three records, all share the same agent key but different timestamps and intents
    // when
    final List<ProcessInstanceDto> result =
        underTest.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(
                record(
                    AGENT_KEY,
                    1000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.INITIALIZING,
                    CREATED),
                record(
                    AGENT_KEY, 2000L, PROCESS_INSTANCE_KEY, AgentInstanceStatus.THINKING, UPDATED),
                record(
                    AGENT_KEY,
                    3000L,
                    PROCESS_INSTANCE_KEY,
                    AgentInstanceStatus.COMPLETED,
                    COMPLETED)));

    // then — single AgentInstanceDto consolidates all three records
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getAgentInstances())
        .hasSize(1)
        .first()
        .satisfies(
            agent -> {
              assertThat(agent.getAgentInstanceId()).isEqualTo(String.valueOf(AGENT_KEY));
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED.name());
              assertThat(agent.getStartDate()).isNotNull();
              assertThat(agent.getEndDate()).isNotNull();
              assertThat(agent.getTotalDurationInMs()).isEqualTo(2000L);
            });
  }
}
