/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.AGENT_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_AGENT_DEFINITION_INDEX_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionDataDto;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionRecordDto;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZeebeAgentDefinitionImportIT extends AbstractBrokerlessZeebeCCSMIT {

  private static final String DEFAULT_TENANT = "<default>";
  private static final long BASE_TIMESTAMP_MS = 1_704_103_200_000L;

  // Instance fields: JUnit creates a new test instance per method, so each test starts fresh.
  private final AtomicLong positionCounter = new AtomicLong(1);
  private final AtomicLong keyCounter = new AtomicLong(500);
  private final String processId = "agentProcess-" + UUID.randomUUID();
  // The deletion filter matches the engine's process definition key, so each test needs its own.
  private final long processDefinitionKeyBase =
      ThreadLocalRandom.current().nextLong(1_000_000L, Long.MAX_VALUE / 2);

  private String agentDefinitionRecordIndex;

  @BeforeEach
  void setUp() {
    agentDefinitionRecordIndex = ZEEBE_RECORD_PREFIX + "-" + ZEEBE_AGENT_DEFINITION_INDEX_NAME;
    setAgenticPhase2Enabled(true);
  }

  @Test
  void shouldImportOneAgentPerTenantAcrossVersionsWithNameAndTypeOfNewestVersion() {
    // given
    seedCreated("review", 1, "Old name", AgentDefinitionType.AI_AGENT_TASK, null);
    seedCreated("review", 2, "Review agent", AgentDefinitionType.AI_AGENT_SUB_PROCESS, null);
    seedCreated("review", 1, "Review for tenant a", AgentDefinitionType.EXTERNAL_AGENT, "a");

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentDefinitions())
        .extracting(
            AgentDefinitionDto::getAgentId,
            AgentDefinitionDto::getTenantId,
            AgentDefinitionDto::getProcessDefinitionKey,
            AgentDefinitionDto::getFlowNodeId,
            AgentDefinitionDto::getName,
            AgentDefinitionDto::getAgentType)
        .containsExactlyInAnyOrder(
            tuple(
                processId + ":review",
                DEFAULT_TENANT,
                processId,
                "review",
                "Review agent",
                "AI_AGENT_SUB_PROCESS"),
            tuple(
                processId + ":review",
                "a",
                processId,
                "review",
                "Review for tenant a",
                "EXTERNAL_AGENT"));
    assertThat(getAgentDefinition(DEFAULT_TENANT).getProcessDefinitionVersions())
        .containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldTakeNameOfNewerVersionImportedInALaterPage() {
    // given
    seedCreated("review", 1, "Old name", AgentDefinitionType.AI_AGENT_TASK, null);
    importAllZeebeEntitiesFromScratch();
    seedCreated("review", 2, "New name", AgentDefinitionType.AI_AGENT_TASK, null);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final AgentDefinitionDto agent = getAgentDefinition(DEFAULT_TENANT);
    assertThat(agent.getName()).isEqualTo("New name");
    assertThat(agent.getProcessDefinitionVersions()).containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldKeepNameOfNewestVersionWhenOlderVersionIsImportedLater() {
    // given
    seedCreated("review", 2, "New name", AgentDefinitionType.AI_AGENT_SUB_PROCESS, null);
    importAllZeebeEntitiesFromScratch();
    seedCreated("review", 1, "Old name", AgentDefinitionType.AI_AGENT_TASK, null);

    // when
    importAllZeebeEntitiesFromLastIndex();

    // then
    final AgentDefinitionDto agent = getAgentDefinition(DEFAULT_TENANT);
    assertThat(agent.getName()).isEqualTo("New name");
    assertThat(agent.getAgentType()).isEqualTo("AI_AGENT_SUB_PROCESS");
    assertThat(agent.getProcessDefinitionVersions()).containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldNotChangeAgentWhenTheSameRecordsAreImportedAgain() {
    // given the engine writes the same agent definition on every partition
    seedCreated("review", 1, "Review", AgentDefinitionType.AI_AGENT_TASK, null);
    seedCreated("review", 2, "Review v2", AgentDefinitionType.AI_AGENT_TASK, null);
    importAllZeebeEntitiesFromScratch();
    final List<AgentDefinitionDto> afterFirstImport = getAllAgentDefinitions();
    seedCreated("review", 2, "Review v2", AgentDefinitionType.AI_AGENT_TASK, null);
    importAllZeebeEntitiesFromLastIndex();

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentDefinitions()).containsExactlyInAnyOrderElementsOf(afterFirstImport);
    assertThat(getAgentDefinition(DEFAULT_TENANT).getProcessDefinitionVersions())
        .containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldNotImportVersionsOfDeletedProcessDefinitions() {
    // given a user removed version 1, and Optimize reads its record again, as on a replay
    seedCreated("review", 1, "Review", AgentDefinitionType.AI_AGENT_TASK, null);
    seedCreated("review", 2, "Review v2", AgentDefinitionType.AI_AGENT_TASK, null);
    seedCreated("risk", 1, "Risk", AgentDefinitionType.AI_AGENT_TASK, null);
    embeddedOptimizeExtension
        .getBean(JobRegistryWriter.class)
        .createJobEntry(JobType.DELETE, EntityType.PROCESS_DEFINITION, processDefinitionKeyOf(1));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    importAllZeebeEntitiesFromScratch();

    // then the agent keeps only the remaining version, and an agent only in version 1 is skipped
    assertThat(getAllAgentDefinitions())
        .singleElement()
        .satisfies(
            agent -> {
              assertThat(agent.getFlowNodeId()).isEqualTo("review");
              assertThat(agent.getProcessDefinitionVersions()).containsExactly("2");
            });
  }

  @Test
  void shouldNotImportAgentDefinitionsWhenAgenticPhase2IsDisabled() {
    // given
    setAgenticPhase2Enabled(false);
    seedCreated("review", 1, "Review", AgentDefinitionType.AI_AGENT_TASK, null);

    // when
    importAllZeebeEntitiesFromScratch();

    // then
    assertThat(getAllAgentDefinitions()).isEmpty();
  }

  private void setAgenticPhase2Enabled(final boolean enabled) {
    embeddedOptimizeExtension
        .getConfigurationService()
        .getAgenticPhase2Configuration()
        .setEnabled(enabled);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  private List<AgentDefinitionDto> getAllAgentDefinitions() {
    return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
        AGENT_DEFINITION_INDEX_NAME, AgentDefinitionDto.class);
  }

  private AgentDefinitionDto getAgentDefinition(final String tenantId) {
    return getAllAgentDefinitions().stream()
        .filter(agent -> agent.getTenantId().equals(tenantId))
        .findFirst()
        .orElseThrow();
  }

  private String processDefinitionKeyOf(final int processVersion) {
    return String.valueOf(processDefinitionKeyBase + processVersion);
  }

  private void seedCreated(
      final String elementId,
      final int processVersion,
      final String name,
      final AgentDefinitionType agentType,
      final String tenantId) {
    final long agentDefinitionKey = keyCounter.getAndIncrement();
    final ZeebeAgentDefinitionDataDto value = new ZeebeAgentDefinitionDataDto();
    value.setAgentDefinitionKey(agentDefinitionKey);
    value.setAgentType(agentType);
    value.setName(name);
    value.setElementId(elementId);
    value.setBpmnProcessId(processId);
    value.setProcessDefinitionKey(processDefinitionKeyBase + processVersion);
    value.setProcessDefinitionVersion(processVersion);
    value.setProcessDefinitionVersionTag("");
    value.setTenantId(tenantId);

    final long position = positionCounter.getAndIncrement();
    final ZeebeAgentDefinitionRecordDto record = new ZeebeAgentDefinitionRecordDto();
    record.setPosition(position);
    record.setKey(agentDefinitionKey);
    record.setPartitionId(1);
    record.setTimestamp(BASE_TIMESTAMP_MS + position * 1000);
    record.setRecordType(RecordType.EVENT);
    record.setValueType(ValueType.AGENT_DEFINITION);
    record.setIntent(AgentDefinitionIntent.CREATED);
    record.setValue(value);

    databaseIntegrationTestExtension.addEntryWithRawIndex(
        agentDefinitionRecordIndex, String.valueOf(position), record);
  }
}
