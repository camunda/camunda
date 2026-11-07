/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionDataDto;
import io.camunda.optimize.dto.zeebe.agentdefinition.ZeebeAgentDefinitionRecordDto;
import io.camunda.zeebe.protocol.record.intent.AgentDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.AgentDefinitionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZeebeAgentDefinitionImportServiceTest {

  @Test
  void shouldFoldDefinitionsOfAllVersionsIntoOneAgent() {
    // given
    final var v1 = record(AgentDefinitionIntent.CREATED, 11L, "review", 1, "Old name");
    final var v2 = record(AgentDefinitionIntent.CREATED, 12L, "review", 2, "Review agent");

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.mapToAgentDefinitions(List.of(v2, v1));

    // then
    assertThat(result).hasSize(1);
    final AgentDefinitionDto agent = result.getFirst();
    assertThat(agent.getAgentId()).isEqualTo("loan:review");
    assertThat(agent.getProcessDefinitionKey()).isEqualTo("loan");
    assertThat(agent.getFlowNodeId()).isEqualTo("review");
    assertThat(agent.getTenantId()).isEqualTo("<default>");
    assertThat(agent.getName()).isEqualTo("Review agent");
    assertThat(agent.getAgentType()).isEqualTo("AI_AGENT_TASK");
    assertThat(agent.getAgentDefinitionIds()).containsExactlyInAnyOrder("11", "12");
    assertThat(agent.getProcessDefinitionVersions()).containsExactlyInAnyOrder("1", "2");
  }

  @Test
  void shouldKeepAgentsOfDifferentElementsApart() {
    // given
    final var review = record(AgentDefinitionIntent.CREATED, 11L, "review", 1, "Review");
    final var risk = record(AgentDefinitionIntent.CREATED, 13L, "risk", 1, "Risk");

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.mapToAgentDefinitions(List.of(review, risk));

    // then
    assertThat(result)
        .extracting(AgentDefinitionDto::getAgentId)
        .containsExactlyInAnyOrder("loan:review", "loan:risk");
  }

  @Test
  void shouldIgnoreDeletedDefinitions() {
    // given
    final var deleted = record(AgentDefinitionIntent.DELETED, 11L, "review", 1, "Review");

    // when
    final List<AgentDefinitionDto> result =
        ZeebeAgentDefinitionImportService.mapToAgentDefinitions(List.of(deleted));

    // then
    assertThat(result).isEmpty();
  }

  private static ZeebeAgentDefinitionRecordDto record(
      final AgentDefinitionIntent intent,
      final long agentDefinitionKey,
      final String elementId,
      final int version,
      final String name) {
    final ZeebeAgentDefinitionDataDto value = new ZeebeAgentDefinitionDataDto();
    value.setAgentDefinitionKey(agentDefinitionKey);
    value.setAgentType(AgentDefinitionType.AI_AGENT_TASK);
    value.setName(name);
    value.setElementId(elementId);
    value.setBpmnProcessId("loan");
    value.setProcessDefinitionKey(1000L + version);
    value.setProcessDefinitionVersion(version);
    final ZeebeAgentDefinitionRecordDto record = new ZeebeAgentDefinitionRecordDto();
    record.setIntent(intent);
    record.setValue(value);
    return record;
  }
}
