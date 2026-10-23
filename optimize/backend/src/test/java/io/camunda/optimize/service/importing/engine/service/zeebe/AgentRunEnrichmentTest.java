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

import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.ToolCallsDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRunEnrichmentTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-23T10:00:00Z");

  @Test
  void shouldCopyProcessStateAndCountUserTasksOfTheWholeProcessInstance() {
    // given
    final ProcessInstanceDto processInstance = processInstance();
    processInstance.setState("COMPLETED");
    processInstance.setEndDate(NOW);
    processInstance.getFlowNodeInstances().add(node("10", "approve", "userTask"));
    processInstance.getFlowNodeInstances().add(node("11", "review-by-hand", "userTask"));
    processInstance.getFlowNodeInstances().add(node("12", "notify", "serviceTask"));

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(run(), processInstance, NOW);

    // then
    assertThat(enriched.getAgentInstanceId()).isEqualTo("700");
    assertThat(enriched.getProcessInstanceState()).isEqualTo("COMPLETED");
    assertThat(enriched.getProcessInstanceEndDate()).isEqualTo(NOW);
    assertThat(enriched.getProcessUserTaskCount()).isEqualTo(2L);
    assertThat(enriched.getEnrichedAt()).isEqualTo(NOW);
  }

  @Test
  void shouldCountIncidentsOnTheAgentsElementInstancesOnly() {
    // given
    final ProcessInstanceDto processInstance = processInstance();
    processInstance.getFlowNodeInstances().add(node("124", "review", "adHocSubProcess"));
    processInstance.getFlowNodeInstances().add(node("130", "notify", "serviceTask"));
    processInstance.getIncidents().add(incident("124"));
    processInstance.getIncidents().add(incident("130"));

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(run(), processInstance, NOW);

    // then
    assertThat(enriched.getHasIncident()).isTrue();
    assertThat(enriched.getIncidentCount()).isEqualTo(1L);
    assertThat(enriched.getIncidentFlowNodeIds()).containsExactly("review");
  }

  @Test
  void shouldMatchIncidentsWhoseActivityIdWasRewrittenToTheElementId() {
    // given
    final ProcessInstanceDto processInstance = processInstance();
    processInstance.getIncidents().add(incident("review"));

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(run(), processInstance, NOW);

    // then
    assertThat(enriched.getIncidentCount()).isEqualTo(1L);
    assertThat(enriched.getIncidentFlowNodeIds()).containsExactly("review");
  }

  @Test
  void shouldReportNoIncidentAsFalseRatherThanNull() {
    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(run(), processInstance(), NOW);

    // then
    assertThat(enriched.getHasIncident()).isFalse();
    assertThat(enriched.getIncidentCount()).isZero();
  }

  @Test
  void shouldCountToolCallsAsFlowNodeInstancesOfEachToolElement() {
    // given
    final ProcessInstanceDto processInstance = processInstance();
    processInstance.getFlowNodeInstances().add(node("201", "searchTask", "serviceTask"));
    processInstance.getFlowNodeInstances().add(node("202", "searchTask", "serviceTask"));
    processInstance.getFlowNodeInstances().add(node("203", "scoreTask", "serviceTask"));

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(run(), processInstance, NOW);

    // then
    assertThat(enriched.getToolCallsByTool())
        .extracting(
            ToolCallsDto::getToolFlowNodeId, ToolCallsDto::getToolName, ToolCallsDto::getCount)
        .containsExactly(
            tuple("searchTask", "search", 2L),
            tuple("scoreTask", "score", 1L),
            tuple("unusedTask", "unused", 0L));
  }

  private static AgentRunDto run() {
    final AgentRunDto run = new AgentRunDto();
    run.setAgentInstanceId("700");
    run.setFlowNodeId("review");
    run.setFlowNodeInstanceIds(List.of("124"));
    run.setToolCallsByTool(
        new ArrayList<>(
            List.of(
                tool("searchTask", "search"),
                tool("scoreTask", "score"),
                tool("unusedTask", "unused"))));
    return run;
  }

  private static ToolCallsDto tool(final String flowNodeId, final String name) {
    final ToolCallsDto tool = new ToolCallsDto();
    tool.setToolFlowNodeId(flowNodeId);
    tool.setToolName(name);
    return tool;
  }

  private static ProcessInstanceDto processInstance() {
    final ProcessInstanceDto processInstance = new ProcessInstanceDto();
    processInstance.setProcessInstanceId("123");
    processInstance.setFlowNodeInstances(new ArrayList<>());
    processInstance.setIncidents(new ArrayList<>());
    return processInstance;
  }

  private static FlowNodeInstanceDto node(
      final String flowNodeInstanceId, final String flowNodeId, final String type) {
    final FlowNodeInstanceDto node = new FlowNodeInstanceDto();
    node.setFlowNodeInstanceId(flowNodeInstanceId);
    node.setFlowNodeId(flowNodeId);
    node.setFlowNodeType(type);
    return node;
  }

  private static IncidentDto incident(final String activityId) {
    final IncidentDto incident = new IncidentDto();
    incident.setActivityId(activityId);
    return incident;
  }
}
