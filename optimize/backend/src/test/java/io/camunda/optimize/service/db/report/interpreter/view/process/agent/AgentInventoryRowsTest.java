/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process.agent;

import static io.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.optimize.dto.optimize.query.agent.AgentDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.RawDataInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataAgentDto;
import io.camunda.optimize.service.db.report.interpreter.view.process.agent.AgentInventoryRows.AgentRunStats;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentInventoryRowsTest {

  private static final long END_MILLIS =
      OffsetDateTime.of(2026, 11, 12, 6, 7, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();

  @Test
  void shouldFillEveryColumnFromTheRunsAndTheDefinitions() {
    // given
    final List<AgentRunStats> buckets =
        List.of(new AgentRunStats("onboarding:kyc", 4, 1000, (double) END_MILLIS));
    final List<AgentDefinitionDto> definitions =
        List.of(definition("onboarding", "kyc", "tenant-a", "KYC review agent", "1"));

    // when
    final List<RawDataAgentDto> rows =
        AgentInventoryRows.toPage(
            buckets, definitions, List.of(process("onboarding", "Customer onboarding")), 0, 20);

    // then
    assertThat(rows)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.getAgentId()).isEqualTo("onboarding:kyc");
              assertThat(row.getAgentName()).isEqualTo("KYC review agent");
              assertThat(row.getAgentType()).isEqualTo("AI_AGENT_TASK");
              assertThat(row.getProcessDefinitionKey()).isEqualTo("onboarding");
              assertThat(row.getProcessName()).isEqualTo("Customer onboarding");
              assertThat(row.getAgentInstances()).isEqualTo(4);
              assertThat(row.getTotalTokens()).isEqualTo(1000);
              assertThat(row.getAverageTokensPerAgentInstance()).isEqualTo(250.0);
              assertThat(row.getLastAgentInstanceEndDate())
                  .isEqualTo(OffsetDateTime.of(2026, 11, 12, 6, 7, 0, 0, ZoneOffset.UTC));
            });
  }

  @Test
  void shouldTakeNameAndTypeFromTheDefinitionWithTheHighestVersion() {
    // given the same agent on two tenants, renamed and retyped in tenant B's newer version
    final AgentDefinitionDto tenantA = definition("onboarding", "kyc", "tenant-a", "Old", "1", "2");
    final AgentDefinitionDto tenantB = definition("onboarding", "kyc", "tenant-b", "New", "3");
    tenantB.setAgentType("EXTERNAL_AGENT");

    // when
    final List<RawDataAgentDto> rows =
        AgentInventoryRows.toPage(
            List.of(new AgentRunStats("onboarding:kyc", 1, 10, null)),
            List.of(tenantA, tenantB),
            List.of(),
            0,
            20);

    // then
    assertThat(rows)
        .extracting(RawDataAgentDto::getAgentName, RawDataAgentDto::getAgentType)
        .containsExactly(tuple("New", "EXTERNAL_AGENT"));
  }

  @Test
  void shouldBreakAVersionTieByTheLowestTenantId() {
    // given
    final AgentDefinitionDto tenantB = definition("onboarding", "kyc", "tenant-b", "From B", "1");
    final AgentDefinitionDto tenantA = definition("onboarding", "kyc", "tenant-a", "From A", "1");

    // when
    final List<RawDataAgentDto> rows =
        AgentInventoryRows.toPage(
            List.of(new AgentRunStats("onboarding:kyc", 1, 10, null)),
            List.of(tenantB, tenantA),
            List.of(),
            0,
            20);

    // then
    assertThat(rows).extracting(RawDataAgentDto::getAgentName).containsExactly("From A");
  }

  @Test
  void shouldFallBackToTheElementIdAndProcessKeyWithoutNames() {
    // given an agent definition and a process definition without names
    final AgentDefinitionDto unnamed = definition("onboarding", "kyc", "tenant-a", " ", "1");

    // when
    final List<RawDataAgentDto> rows =
        AgentInventoryRows.toPage(
            List.of(new AgentRunStats("onboarding:kyc", 1, 10, null)),
            List.of(unnamed),
            List.of(process("onboarding", null)),
            0,
            20);

    // then
    assertThat(rows)
        .extracting(RawDataAgentDto::getAgentName, RawDataAgentDto::getProcessName)
        .containsExactly(tuple("kyc", "onboarding"));
  }

  @Test
  void shouldReturnOnlyTheRowsOfTheRequestedPage() {
    // given buckets already sorted by agent instances
    final List<AgentRunStats> buckets =
        List.of(
            new AgentRunStats("p:a", 30, 0, null),
            new AgentRunStats("p:b", 20, 0, null),
            new AgentRunStats("p:c", 10, 0, null));

    // when
    final List<RawDataAgentDto> rows =
        AgentInventoryRows.toPage(buckets, List.of(), List.of(), 1, 1);

    // then
    assertThat(rows).extracting(RawDataAgentDto::getAgentId).containsExactly("p:b");
  }

  @Test
  void shouldAggregateBucketsUpToTheEndOfThePageWithinTheResponseLimit() {
    // when / then
    assertThat(AgentInventoryRows.bucketCount(20, 10)).isEqualTo(30);
    assertThat(AgentInventoryRows.bucketCount(0, Integer.MAX_VALUE))
        .isEqualTo(MAX_RESPONSE_SIZE_LIMIT);
  }

  @Test
  void shouldDeserializeAnAgentRowAsRawDataAgentDto() throws Exception {
    // given
    final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final String json =
        """
        {"agentId": "onboarding:kyc", "agentName": "KYC", "agentType": "AI_AGENT_TASK",
         "processDefinitionKey": "onboarding", "processName": "Onboarding",
         "agentInstances": 2, "averageTokensPerAgentInstance": 5.0, "totalTokens": 10,
         "lastAgentInstanceEndDate": null}
        """;

    // when
    final RawDataInstanceDto row = objectMapper.readValue(json, RawDataInstanceDto.class);

    // then
    assertThat(row).isInstanceOf(RawDataAgentDto.class);
  }

  private static AgentDefinitionDto definition(
      final String processKey,
      final String flowNodeId,
      final String tenantId,
      final String name,
      final String... versions) {
    final AgentDefinitionDto definition = new AgentDefinitionDto();
    definition.setAgentId(processKey + ":" + flowNodeId);
    definition.setProcessDefinitionKey(processKey);
    definition.setFlowNodeId(flowNodeId);
    definition.setTenantId(tenantId);
    definition.setName(name);
    definition.setAgentType("AI_AGENT_TASK");
    definition.setProcessDefinitionVersions(List.of(versions));
    return definition;
  }

  private static ReportDataDefinitionDto process(final String key, final String name) {
    final ReportDataDefinitionDto definition = new ReportDataDefinitionDto(key);
    definition.setName(name);
    return definition;
  }
}
