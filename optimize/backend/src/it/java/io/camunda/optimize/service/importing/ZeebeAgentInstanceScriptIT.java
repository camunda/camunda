/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto.AgentMetricsDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto.AgentToolDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZeebeAgentInstanceScriptIT extends AbstractCCSMIT {

  private static final String PROCESS_KEY = "testProcess";
  private static final String INSTANCE_ID = "100";
  private static final String AGENT_ID = "1";
  private static final String AGENT_ID_2 = "2";
  private static final OffsetDateTime START_TIME =
      OffsetDateTime.parse("2024-01-01T10:00:00+00:00");
  private static final OffsetDateTime END_TIME = OffsetDateTime.parse("2024-01-01T10:00:05+00:00");
  private static final long START_EPOCH_MS = START_TIME.toInstant().toEpochMilli();

  private ProcessInstanceWriter processInstanceWriter;
  private DatabaseClient databaseClient;

  @BeforeEach
  void setUp() {
    processInstanceWriter = embeddedOptimizeExtension.getBean(ProcessInstanceWriter.class);
    databaseClient = embeddedOptimizeExtension.getBean(DatabaseClient.class);
  }

  @Test
  void shouldInsertCreatedAgentInstanceWithoutEndDate() {
    // given
    final ProcessInstanceDto dto = buildBaseInstance();
    dto.setAgentInstances(List.of(createdAgent(AGENT_ID)));

    // when
    importProcessInstances(List.of(dto));

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getAgentInstances()).hasSize(1);
              assertThat(saved.getAgentInstances().get(0).getAgentInstanceId()).isEqualTo(AGENT_ID);
              assertThat(saved.getAgentInstances().get(0).getEndDate()).isNull();
              assertThat(saved.getAgentInstances().get(0).getTotalDurationInMs()).isNull();
              assertThat(saved.getAgentTotalInputTokens()).isEqualTo(0L);
              assertThat(saved.getAgentTotalOutputTokens()).isEqualTo(0L);
              assertThat(saved.getAgentTotalModelCalls()).isEqualTo(0L);
              assertThat(saved.getAgentTotalToolCalls()).isEqualTo(0L);
            });
  }

  @Test
  void shouldMergeCompletedEventAndComputeDuration() {
    // given: CREATED batch creates the document
    final ProcessInstanceDto created = buildBaseInstance();
    created.setAgentInstances(List.of(createdAgent(AGENT_ID)));
    importProcessInstances(List.of(created));

    // when: COMPLETED batch runs the script
    final ProcessInstanceDto completed = buildBaseInstance();
    completed.setAgentInstances(List.of(completedAgent(AGENT_ID)));
    importProcessInstances(List.of(completed));

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = saved.getAgentInstances().get(0);
              assertThat(agent.getEndDate()).isNotNull();
              assertThat(agent.getTotalDurationInMs()).isEqualTo(5_000L);
              assertThat(saved.getAgentTotalInputTokens()).isEqualTo(100L);
              assertThat(saved.getAgentTotalOutputTokens()).isEqualTo(200L);
              assertThat(saved.getAgentTotalModelCalls()).isEqualTo(3L);
              assertThat(saved.getAgentTotalToolCalls()).isEqualTo(7L);
            });
  }

  @Test
  void shouldInsertCompletedOnlyAgentWithoutDuration() {
    // given: no prior CREATED — COMPLETED arrives first (out-of-order across batches)
    final ProcessInstanceDto dto = buildBaseInstance();
    dto.setAgentInstances(List.of(completedAgent(AGENT_ID)));

    // when
    importProcessInstances(List.of(dto));

    // then: entry inserted, duration null because no startDateEpochMs
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getAgentInstances()).hasSize(1);
              assertThat(saved.getAgentInstances().get(0).getEndDate()).isNotNull();
              assertThat(saved.getAgentInstances().get(0).getTotalDurationInMs()).isNull();
              assertThat(saved.getAgentTotalInputTokens()).isEqualTo(100L);
              assertThat(saved.getAgentTotalToolCalls()).isEqualTo(7L);
            });
  }

  @Test
  void shouldMergeCreatedAfterCompletedAndComputeDuration() {
    // given: COMPLETED arrives first (out-of-order) — upsert creates doc from source
    final ProcessInstanceDto completed = buildBaseInstance();
    completed.setAgentInstances(List.of(completedAgent(AGENT_ID)));
    importProcessInstances(List.of(completed));

    // when: CREATED arrives later — script runs, merges startDate + startDateEpochMs
    final ProcessInstanceDto created = buildBaseInstance();
    created.setAgentInstances(List.of(createdAgent(AGENT_ID)));
    importProcessInstances(List.of(created));

    // then: duration computed retroactively, metrics preserved from COMPLETED
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = saved.getAgentInstances().get(0);
              assertThat(agent.getStartDate()).isNotNull();
              assertThat(agent.getEndDate()).isNotNull();
              assertThat(agent.getTotalDurationInMs()).isEqualTo(5_000L);
              assertThat(saved.getAgentTotalInputTokens()).isEqualTo(100L);
              assertThat(saved.getAgentTotalToolCalls()).isEqualTo(7L);
            });
  }

  @Test
  void shouldComputeDurationWhenCreatedAndCompletedArriveInSameBatch() {
    // given: import service merged both intents into one entry before calling the writer
    final ProcessInstanceDto dto = buildBaseInstance();
    final AgentInstanceDto merged = createdAgent(AGENT_ID);
    merged.setEndDate(END_TIME);
    merged.setMetrics(metrics());
    merged.setTools(tools());
    dto.setAgentInstances(List.of(merged));
    dto.setAgentTotalInputTokens(100L);
    dto.setAgentTotalOutputTokens(200L);
    dto.setAgentTotalModelCalls(3L);
    dto.setAgentTotalToolCalls(7L);

    // when: first import → upsert creates doc from source (script not executed)
    importProcessInstances(List.of(dto));

    // then: doc created from source; import service already computed totals
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getAgentInstances()).hasSize(1);
              assertThat(saved.getAgentTotalInputTokens()).isEqualTo(100L);
            });
  }

  @Test
  void shouldReAggregateAgentTotalsAfterMergingSecondAgent() {
    // given: two agents — CREATED for both
    final ProcessInstanceDto created = buildBaseInstance();
    created.setAgentInstances(List.of(createdAgent(AGENT_ID), createdAgent(AGENT_ID_2)));
    importProcessInstances(List.of(created));

    // when: COMPLETED for both agents arrives
    final ProcessInstanceDto completed = buildBaseInstance();
    completed.setAgentInstances(List.of(completedAgent(AGENT_ID), completedAgent(AGENT_ID_2)));
    importProcessInstances(List.of(completed));

    // then: totals summed across both agents
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getAgentInstances()).hasSize(2);
              assertThat(saved.getAgentTotalInputTokens()).isEqualTo(200L);
              assertThat(saved.getAgentTotalOutputTokens()).isEqualTo(400L);
              assertThat(saved.getAgentTotalModelCalls()).isEqualTo(6L);
              assertThat(saved.getAgentTotalToolCalls()).isEqualTo(14L);
            });
  }

  @Test
  void shouldHandleDocWithNoAgentInstancesField() {
    // given: existing process instance without any agent data (pre-feature document)
    final ProcessInstanceDto base = buildBaseInstance();
    base.setAgentInstances(null);
    base.setAgentTotalInputTokens(null);
    base.setAgentTotalOutputTokens(null);
    base.setAgentTotalModelCalls(null);
    base.setAgentTotalToolCalls(null);
    importProcessInstances(List.of(base));

    // when: agent CREATED import runs the script against the existing doc
    final ProcessInstanceDto agentBatch = buildBaseInstance();
    agentBatch.setAgentInstances(List.of(createdAgent(AGENT_ID)));
    importProcessInstances(List.of(agentBatch));

    // then: null guard in script prevents NPE; entry inserted
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            saved -> {
              assertThat(saved.getAgentInstances()).hasSize(1);
              assertThat(saved.getAgentTotalInputTokens()).isEqualTo(0L);
            });
  }

  // --- helpers ---

  private void importProcessInstances(final List<ProcessInstanceDto> instances) {
    final var requests = processInstanceWriter.generateProcessInstanceImports(instances, "test");
    databaseClient.executeImportRequestsAsBulk("agent-instance-script-test", requests, false);
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  private ProcessInstanceDto buildBaseInstance() {
    final ProcessInstanceDto dto = new ProcessInstanceDto();
    dto.setProcessInstanceId(INSTANCE_ID);
    dto.setProcessDefinitionKey(PROCESS_KEY);
    dto.setProcessDefinitionId("1");
    dto.setProcessDefinitionVersion("1");
    dto.setDataSource(new ZeebeDataSourceDto("default", 1));
    return dto;
  }

  private AgentInstanceDto createdAgent(final String agentId) {
    final AgentInstanceDto dto = new AgentInstanceDto();
    dto.setAgentInstanceId(agentId);
    dto.setFlowNodeId("agentTask");
    dto.setStartDate(START_TIME);
    dto.setStartDateEpochMs(START_EPOCH_MS);
    dto.setMetrics(new AgentMetricsDto());
    return dto;
  }

  private AgentInstanceDto completedAgent(final String agentId) {
    final AgentInstanceDto dto = new AgentInstanceDto();
    dto.setAgentInstanceId(agentId);
    dto.setFlowNodeId("agentTask");
    dto.setEndDate(END_TIME);
    dto.setMetrics(metrics());
    dto.setTools(tools());
    return dto;
  }

  private AgentMetricsDto metrics() {
    final AgentMetricsDto m = new AgentMetricsDto();
    m.setInputTokens(100L);
    m.setOutputTokens(200L);
    m.setModelCalls(3L);
    m.setToolCalls(7L);
    return m;
  }

  private List<AgentToolDto> tools() {
    final AgentToolDto tool = new AgentToolDto();
    tool.setName("search");
    return List.of(tool);
  }
}
