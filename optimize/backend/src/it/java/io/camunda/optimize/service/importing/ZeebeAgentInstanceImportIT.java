/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_AGENT_INSTANCE_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.AgentInstanceDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentDefinitionValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentMetricsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Full pipeline IT: seeds fake AGENT_INSTANCE records directly into the Zeebe export index, runs
 * the Optimize import pipeline, and verifies the resulting ProcessInstance document.
 *
 * <p>Seeding fake records avoids a live Zeebe broker dependency and makes the tests fast and
 * deterministic.
 */
class ZeebeAgentInstanceImportIT extends AbstractCCSMIT {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long PROCESS_DEFINITION_KEY = 10L;
  private static final String BPMN_PROCESS_ID = "agentTestProcess";
  private static final String ELEMENT_ID = "agentTask";
  private static final String MODEL = "gpt-4o";
  private static final String PROVIDER = "openai";
  private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

  private final AtomicLong positionCounter = new AtomicLong(1);
  private final AtomicLong keyCounter = new AtomicLong(200);

  private String agentInstanceIndex;

  @BeforeEach
  void setUp() {
    agentInstanceIndex =
        zeebeExtension.getZeebeRecordPrefix() + "-" + ZEEBE_AGENT_INSTANCE_INDEX_NAME;
  }

  @Test
  void shouldImportCreatedAgentInstance() {
    // given
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .anySatisfy(
            pi -> {
              assertThat(pi.getProcessInstanceId()).isEqualTo(String.valueOf(PROCESS_INSTANCE_KEY));
              assertThat(pi.getAgentInstances()).hasSize(1);
              final AgentInstanceDto agent = pi.getAgentInstances().get(0);
              assertThat(agent.getAgentInstanceId()).isEqualTo(String.valueOf(agentKey));
              assertThat(agent.getFlowNodeId()).isEqualTo(ELEMENT_ID);
              assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.INITIALIZING.name());
              assertThat(agent.getStartDate()).isNotNull();
              assertThat(agent.getEndDate()).isNull();
              assertThat(agent.getTotalDurationInMs()).isNull();
              assertThat(agent.getDefinition()).isNotNull();
              assertThat(agent.getDefinition().getModel()).isEqualTo(MODEL);
              assertThat(agent.getDefinition().getProvider()).isEqualTo(PROVIDER);
              assertThat(agent.getMetrics().getInputTokens()).isZero();
              assertThat(agent.getMetrics().getOutputTokens()).isZero();
              assertThat(agent.getMetrics().getModelCalls()).isZero();
              assertThat(agent.getMetrics().getToolCalls()).isZero();
            });
  }

  @Test
  void shouldImportUpdatedAgentInstance_accumulatesMetrics() {
    // given
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 512L, 128L, 1, 2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final ProcessInstanceDto pi =
        databaseIntegrationTestExtension.getAllProcessInstances().stream()
            .filter(p -> String.valueOf(PROCESS_INSTANCE_KEY).equals(p.getProcessInstanceId()))
            .findFirst()
            .orElseThrow();
    final AgentInstanceDto agent = pi.getAgentInstances().get(0);

    assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.THINKING.name());
    assertThat(agent.getMetrics().getInputTokens()).isEqualTo(512L);
    assertThat(agent.getMetrics().getOutputTokens()).isEqualTo(128L);
    assertThat(agent.getMetrics().getModelCalls()).isEqualTo(1L);
    assertThat(agent.getMetrics().getToolCalls()).isEqualTo(2L);

    assertThat(pi.getAgentTotalInputTokens()).isEqualTo(512L);
    assertThat(pi.getAgentTotalOutputTokens()).isEqualTo(128L);
    assertThat(pi.getAgentTotalModelCalls()).isEqualTo(1L);
    assertThat(pi.getAgentTotalTokens()).isEqualTo(640L);
  }

  @Test
  void shouldImportCompletedAgentInstance_setsDurationAndEndDate() {
    // given
    final long agentKey = keyCounter.getAndIncrement();
    seedRecord(
        agentKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    seedRecord(
        agentKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 500L, 200L, 2, 3);
    seedRecord(
        agentKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 800L, 300L, 3, 4);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    final AgentInstanceDto agent =
        databaseIntegrationTestExtension.getAllProcessInstances().stream()
            .filter(p -> String.valueOf(PROCESS_INSTANCE_KEY).equals(p.getProcessInstanceId()))
            .findFirst()
            .orElseThrow()
            .getAgentInstances()
            .get(0);

    assertThat(agent.getStatus()).isEqualTo(AgentInstanceStatus.COMPLETED.name());
    assertThat(agent.getEndDate()).isNotNull();
    assertThat(agent.getTotalDurationInMs()).isNotNull().isPositive();
    // Metrics reflect final cumulative totals from the COMPLETED event
    assertThat(agent.getMetrics().getInputTokens()).isEqualTo(800L);
    assertThat(agent.getMetrics().getOutputTokens()).isEqualTo(300L);
    assertThat(agent.getMetrics().getModelCalls()).isEqualTo(3L);
    assertThat(agent.getMetrics().getToolCalls()).isEqualTo(4L);
  }

  @Test
  void shouldImportMultipleAgentInstancesForSameProcessInstance() {
    // given — two distinct agent instances on the same process instance
    final long agentKey1 = keyCounter.getAndIncrement();
    final long agentKey2 = keyCounter.getAndIncrement();
    seedRecord(
        agentKey1, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);
    seedRecord(
        agentKey2, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0L, 0L, 0, 0);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .anySatisfy(
            pi -> {
              assertThat(pi.getProcessInstanceId()).isEqualTo(String.valueOf(PROCESS_INSTANCE_KEY));
              assertThat(pi.getAgentInstances()).hasSize(2);
            });
  }

  /** Seeds one {@link ZeebeAgentInstanceRecordDto} record into the Zeebe export index. */
  private void seedRecord(
      final long agentKey,
      final AgentInstanceIntent intent,
      final AgentInstanceStatus status,
      final long inputTokens,
      final long outputTokens,
      final int modelCalls,
      final int toolCalls) {

    final ZeebeAgentInstanceDataDto value = new ZeebeAgentInstanceDataDto();
    value.setAgentInstanceKey(agentKey);
    value.setElementInstanceKey(agentKey + 1000);
    value.setElementId(ELEMENT_ID);
    value.setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    value.setBpmnProcessId(BPMN_PROCESS_ID);
    value.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    value.setProcessDefinitionVersion(1);
    value.setStatus(status);

    final AgentDefinitionValueDto def = new AgentDefinitionValueDto();
    def.setModel(MODEL);
    def.setProvider(PROVIDER);
    def.setSystemPrompt(SYSTEM_PROMPT);
    value.setDefinition(def);

    final AgentMetricsValueDto metrics = new AgentMetricsValueDto();
    metrics.setInputTokens(inputTokens);
    metrics.setOutputTokens(outputTokens);
    metrics.setModelCalls(modelCalls);
    metrics.setToolCalls(toolCalls);
    value.setMetrics(metrics);

    final long position = positionCounter.getAndIncrement();
    final ZeebeAgentInstanceRecordDto record = new ZeebeAgentInstanceRecordDto();
    record.setPosition(position);
    record.setKey(agentKey);
    record.setPartitionId(1);
    record.setTimestamp(System.currentTimeMillis() + position * 1000);
    record.setRecordType(RecordType.EVENT);
    record.setValueType(ValueType.AGENT_INSTANCE);
    record.setIntent(intent);
    record.setValue(value);

    databaseIntegrationTestExtension.addEntryWithRawIndex(
        agentInstanceIndex, String.valueOf(position), record);
  }
}
