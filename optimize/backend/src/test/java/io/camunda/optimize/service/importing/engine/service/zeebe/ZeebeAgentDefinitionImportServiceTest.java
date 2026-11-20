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

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionDataDto;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionRecordDto;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZeebeAgentDefinitionImportServiceTest {

  private static final String PROCESS_ID = "loan";
  private static final String DEFAULT_TENANT = "<default>";

  @Test
  void shouldFoldRecordsOfAllVersionsIntoOneAgentWithNameOfHighestVersion() {
    // given
    final var v1 = created("review", 1, "Old name", AgentDefinitionType.AI_AGENT_TASK, null);
    final var v2 =
        created("review", 2, "Review agent", AgentDefinitionType.AI_AGENT_SUB_PROCESS, null);

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(v2, v1));

    // then
    assertThat(result)
        .singleElement()
        .satisfies(
            agent -> {
              assertThat(agent.getAgentId()).isEqualTo("loan:review");
              assertThat(agent.getProcessDefinitionKey()).isEqualTo(PROCESS_ID);
              assertThat(agent.getFlowNodeId()).isEqualTo("review");
              assertThat(agent.getTenantId()).isEqualTo(DEFAULT_TENANT);
              assertThat(agent.getName()).isEqualTo("Review agent");
              assertThat(agent.getAgentType()).isEqualTo("AI_AGENT_SUB_PROCESS");
              assertThat(agent.getProcessDefinitionVersions()).containsExactlyInAnyOrder("1", "2");
            });
  }

  @Test
  void shouldFoldTheSameRecordFromSeveralPartitionsIntoOneVersion() {
    // given the engine repeats the agent definition on every partition the deployment reaches
    final var partition1 = created("review", 1, "Review", AgentDefinitionType.AI_AGENT_TASK, null);
    final var partition2 = created("review", 1, "Review", AgentDefinitionType.AI_AGENT_TASK, null);

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(partition1, partition2));

    // then
    assertThat(result)
        .singleElement()
        .satisfies(agent -> assertThat(agent.getProcessDefinitionVersions()).containsExactly("1"));
  }

  @Test
  void shouldKeepAgentsOfDifferentElementsApart() {
    // given
    final var review = created("review", 1, "Review", AgentDefinitionType.AI_AGENT_TASK, null);
    final var risk = created("risk", 1, "Risk", AgentDefinitionType.EXTERNAL_AGENT, null);

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(review, risk));

    // then
    assertThat(result)
        .extracting(AgentDefinitionDto::getAgentId, AgentDefinitionDto::getAgentType)
        .containsExactlyInAnyOrder(
            tuple("loan:review", "AI_AGENT_TASK"), tuple("loan:risk", "EXTERNAL_AGENT"));
  }

  @Test
  void shouldKeepAgentsOfDifferentTenantsApart() {
    // given
    final var tenantA = created("review", 1, "Review A", AgentDefinitionType.AI_AGENT_TASK, "a");
    final var tenantB = created("review", 1, "Review B", AgentDefinitionType.AI_AGENT_TASK, "b");

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(tenantA, tenantB));

    // then
    assertThat(result)
        .extracting(AgentDefinitionDto::getTenantId, AgentDefinitionDto::getName)
        .containsExactlyInAnyOrder(tuple("a", "Review A"), tuple("b", "Review B"));
  }

  @Test
  void shouldIgnoreDeletedRecords() {
    // given
    final var deleted =
        record(
            AgentDefinitionIntent.DELETED,
            "review",
            1,
            "Review",
            AgentDefinitionType.AI_AGENT_TASK,
            null);

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.filterAndMapZeebeRecordsToOptimizeEntities(
            List.of(deleted));

    // then
    assertThat(result).isEmpty();
  }

  private static ZeebeAgentDefinitionRecordDto created(
      final String elementId,
      final int version,
      final String name,
      final AgentDefinitionType agentType,
      final String tenantId) {
    return record(AgentDefinitionIntent.CREATED, elementId, version, name, agentType, tenantId);
  }

  private static ZeebeAgentDefinitionRecordDto record(
      final AgentDefinitionIntent intent,
      final String elementId,
      final int version,
      final String name,
      final AgentDefinitionType agentType,
      final String tenantId) {
    final ZeebeAgentDefinitionDataDto value = new ZeebeAgentDefinitionDataDto();
    value.setAgentDefinitionKey(100L * version + elementId.hashCode());
    value.setAgentType(agentType);
    value.setName(name);
    value.setElementId(elementId);
    value.setBpmnProcessId(PROCESS_ID);
    value.setProcessDefinitionKey(1000L + version);
    value.setProcessDefinitionVersion(version);
    value.setTenantId(tenantId);
    final ZeebeAgentDefinitionRecordDto record = new ZeebeAgentDefinitionRecordDto();
    record.setIntent(intent);
    record.setValue(value);
    return record;
  }
}
