/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_AGENT_INSTANCE_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentDefinitionValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentLimitsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceDataDto.AgentMetricsValueDto;
import io.camunda.optimize.dto.zeebe.agentinstance.ZeebeAgentInstanceRecordDto;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentInstanceStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZeebeAgentRunImportIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final long BASE_TIMESTAMP_MS = 1_704_103_200_000L;

  // Instance fields: JUnit creates a new test instance per method, so each test starts fresh.
  private final AtomicLong positionCounter = new AtomicLong(1);
  private final AtomicLong keyCounter = new AtomicLong(700);
  private final String processId = "agentProcess-" + UUID.randomUUID();
  private final long processInstanceKey = keyCounter.getAndIncrement();
  // The deletion filter matches the engine's process definition key, so each test needs its own.
  private final long processDefinitionKey =
      ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE / 2);

  private String agentInstanceRecordIndex;

  @BeforeEach
  void setUp() {
    agentInstanceRecordIndex = ZEEBE_RECORD_PREFIX + "-" + ZEEBE_AGENT_INSTANCE_INDEX_NAME;
    setAgenticPhase2Enabled(true);
  }

  @Test
  void shouldImportOneRunWithTotalsModelAndLimitsOfTheNewestRecord() {
    // given
    final long runKey = keyCounter.getAndIncrement();
    final long createdPosition =
        seed(runKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0, 0, 0);
    seed(runKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 300, 100, 1);
    final long completedPosition =
        seed(runKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 900, 300, 3);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentRuns())
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.getAgentInstanceId()).isEqualTo(String.valueOf(runKey));
              assertThat(run.getAgentId()).isEqualTo(processId + ":agent");
              assertThat(run.getProcessDefinitionKey()).isEqualTo(processId);
              assertThat(run.getProcessInstanceId()).isEqualTo(String.valueOf(processInstanceKey));
              assertThat(run.getModel()).isEqualTo("claude");
              assertThat(run.getProvider()).isEqualTo("anthropic");
              assertThat(run.getStatus()).isEqualTo("COMPLETED");
              assertThat(run.getInputTokens()).isEqualTo(900L);
              assertThat(run.getOutputTokens()).isEqualTo(300L);
              assertThat(run.getTotalTokens()).isEqualTo(1200L);
              assertThat(run.getModelCalls()).isEqualTo(3L);
              assertThat(run.getMaxModelCalls()).isEqualTo(3L);
              assertThat(run.getLimitHit()).containsExactly("MODEL_CALLS");
              assertThat(run.getStartDate()).isAtSameInstantAs(date(createdPosition));
              assertThat(run.getEndDate()).isAtSameInstantAs(date(completedPosition));
              assertThat(run.getLastRecordPosition()).isEqualTo(completedPosition);
            });
  }

  @Test
  void shouldMergeRecordsOfOneRunImportedInSeveralPages() {
    // given
    final long runKey = keyCounter.getAndIncrement();
    final long createdPosition =
        seed(runKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0, 0, 0);
    final long updatedPosition =
        seed(runKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 300, 100, 1);
    importAllZeebeEntitiesFromScratch();
    final long completedPosition =
        seed(runKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 300, 100, 1);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    assertThat(getAllAgentRuns())
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.getStatus()).isEqualTo("COMPLETED");
              assertThat(run.getTotalTokens()).isEqualTo(400L);
              assertThat(run.getStartDate()).isAtSameInstantAs(date(createdPosition));
              assertThat(run.getEndDate()).isAtSameInstantAs(date(completedPosition));
              // the COMPLETED record did not change the usage
              assertThat(run.getLastActivityDate()).isAtSameInstantAs(date(updatedPosition));
            });
  }

  @Test
  void shouldImportRunFromItsCompletedRecordAlone() {
    // given only the COMPLETED record, as for a run whose earlier records Zeebe removed
    final long runKey = keyCounter.getAndIncrement();
    final long completedPosition =
        seed(runKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 900, 300, 2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentRuns())
        .singleElement()
        .satisfies(
            run -> {
              assertThat(run.getTotalTokens()).isEqualTo(1200L);
              assertThat(run.getStartDate()).isAtSameInstantAs(date(completedPosition));
              assertThat(run.getEndDate()).isAtSameInstantAs(date(completedPosition));
            });
  }

  @Test
  void shouldNotChangeRunWhenTheSameRecordsAreImportedAgain() {
    // given
    final long runKey = keyCounter.getAndIncrement();
    seed(runKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0, 0, 0);
    seed(runKey, AgentInstanceIntent.UPDATED, AgentInstanceStatus.THINKING, 300, 100, 1);
    importAllZeebeEntitiesFromScratch();
    seed(runKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 900, 300, 2);
    importAllZeebeEntitiesFromLastIndex();
    final List<AgentRunDto> afterFirstImport = getAllAgentRuns();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentRuns()).containsExactlyInAnyOrderElementsOf(afterFirstImport);
  }

  @Test
  void shouldKeepWritingTheNestedAgentInstancesOfTheProcessInstance() {
    // given
    final long runKey = keyCounter.getAndIncrement();
    seed(runKey, AgentInstanceIntent.CREATED, AgentInstanceStatus.INITIALIZING, 0, 0, 0);
    seed(runKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 900, 300, 2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            processInstance -> {
              assertThat(processInstance.getAgentInstances())
                  .singleElement()
                  .satisfies(
                      agent ->
                          assertThat(agent.getAgentInstanceId()).isEqualTo(String.valueOf(runKey)));
              assertThat(processInstance.getAgentTotalTokens()).isEqualTo(1200L);
            });
  }

  @Test
  void shouldNotImportRunsOfDeletedProcessDefinitions() {
    // given a user removed the version of one run, and Optimize reads its records again, as on a
    // replay
    final long deletedDefinitionKey = processDefinitionKey + 1;
    final long runOfDeletedDefinition = keyCounter.getAndIncrement();
    final long keptRun = keyCounter.getAndIncrement();
    seed(
        deletedDefinitionKey,
        runOfDeletedDefinition,
        AgentInstanceIntent.COMPLETED,
        AgentInstanceStatus.COMPLETED,
        900,
        300,
        2);
    seed(keptRun, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 900, 300, 2);
    embeddedOptimizeExtension
        .getBean(JobRegistryWriter.class)
        .createJobEntry(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, String.valueOf(deletedDefinitionKey));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentRuns())
        .extracting(AgentRunDto::getAgentInstanceId)
        .containsExactly(String.valueOf(keptRun));
  }

  @Test
  void shouldNotImportRunsWhenAgenticPhase2IsDisabled() {
    // given
    setAgenticPhase2Enabled(false);
    final long runKey = keyCounter.getAndIncrement();
    seed(runKey, AgentInstanceIntent.COMPLETED, AgentInstanceStatus.COMPLETED, 900, 300, 2);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentRuns()).isEmpty();
  }

  private void setAgenticPhase2Enabled(final boolean enabled) {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAgenticPhase2Configuration()
        .setEnabled(enabled);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private List<AgentRunDto> getAllAgentRuns() {
    return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
        AGENT_INSTANCE_INDEX_NAME, AgentRunDto.class);
  }

  private long seed(
      final long runKey,
      final AgentInstanceIntent intent,
      final AgentInstanceStatus status,
      final long inputTokens,
      final long outputTokens,
      final int modelCalls) {
    return seed(
        processDefinitionKey, runKey, intent, status, inputTokens, outputTokens, modelCalls);
  }

  private long seed(
      final long definitionKey,
      final long runKey,
      final AgentInstanceIntent intent,
      final AgentInstanceStatus status,
      final long inputTokens,
      final long outputTokens,
      final int modelCalls) {
    final ZeebeAgentInstanceDataDto value = new ZeebeAgentInstanceDataDto();
    value.setAgentInstanceKey(runKey);
    value.setElementId("agent");
    value.setElementInstanceKey(runKey + 1);
    value.setElementInstanceKeys(List.of(runKey + 1));
    value.setBpmnProcessId(processId);
    value.setProcessDefinitionKey(definitionKey);
    value.setProcessDefinitionVersion(1);
    value.setProcessInstanceKey(processInstanceKey);
    value.setRootProcessInstanceKey(processInstanceKey);
    value.setStatus(status);
    final AgentDefinitionValueDto definition = new AgentDefinitionValueDto();
    definition.setModel("claude");
    definition.setProvider("anthropic");
    value.setDefinition(definition);
    final AgentLimitsValueDto limits = new AgentLimitsValueDto();
    limits.setMaxModelCalls(3);
    value.setLimits(limits);
    final AgentMetricsValueDto metrics = new AgentMetricsValueDto();
    metrics.setInputTokens(inputTokens);
    metrics.setOutputTokens(outputTokens);
    metrics.setModelCalls(modelCalls);
    value.setMetrics(metrics);

    final long position = positionCounter.getAndIncrement();
    final ZeebeAgentInstanceRecordDto record = new ZeebeAgentInstanceRecordDto();
    record.setPosition(position);
    record.setKey(runKey);
    record.setPartitionId(1);
    record.setTimestamp(timestamp(position));
    record.setRecordType(RecordType.EVENT);
    record.setValueType(ValueType.AGENT_INSTANCE);
    record.setIntent(intent);
    record.setValue(value);

    databaseIntegrationTestExtension.addEntryWithRawIndex(
        agentInstanceRecordIndex, String.valueOf(position), record);
    return position;
  }

  private static long timestamp(final long position) {
    return BASE_TIMESTAMP_MS + position * 1000;
  }

  private static OffsetDateTime date(final long position) {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(timestamp(position)), ZoneId.systemDefault());
  }
}
