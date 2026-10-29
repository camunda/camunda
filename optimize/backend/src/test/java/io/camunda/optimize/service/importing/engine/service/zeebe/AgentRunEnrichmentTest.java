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
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto;
import io.camunda.optimize.dto.optimize.query.agent.AgentRunDto.FailureReasonDto;
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

  @Test
  void shouldSumEndedAgentElementInstancesWithoutTimeSpentInUserTaskTools() {
    // given a chat agent entered twice, waiting 30s on a user-task tool in the first activation
    final ProcessInstanceDto processInstance = processInstance();
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("124", "review", "adHocSubProcess"), 0, 100));
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("125", "review", "adHocSubProcess"), 500, 520));
    processInstance.getFlowNodeInstances().add(timed(node("126", "askHuman", "userTask"), 10, 40));
    // a user task outside the agent that the person needed 300s for
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("127", "feedback", "userTask"), 150, 450));

    final AgentRunDto run = runWithTools();
    run.setFlowNodeInstanceIds(List.of("124", "125"));

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(run, processInstance, NOW);

    // then
    assertThat(enriched.getActiveDurationMs()).isEqualTo(90_000L);
    assertThat(enriched.getHumanWaitDurationMs()).isEqualTo(30_000L);
    assertThat(enriched.getHumanStepCount()).isEqualTo(1L);
  }

  @Test
  void shouldLeaveActiveDurationEmptyUntilAnElementInstanceEnded() {
    // given
    final ProcessInstanceDto processInstance = processInstance();
    final FlowNodeInstanceDto running = node("124", "review", "adHocSubProcess");
    running.setStartDate(NOW);
    processInstance.getFlowNodeInstances().add(running);

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(runWithTools(), processInstance, NOW);

    // then
    assertThat(enriched.getActiveDurationMs()).isNull();
  }

  @Test
  void shouldCountIncidentsOnToolElementsAndKeepOneReasonPerElementAndErrorType() {
    // given
    final ProcessInstanceDto processInstance = processInstance();
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("124", "review", "adHocSubProcess"), 0, 100));
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("201", "searchTask", "serviceTask"), 5, 6));
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("202", "searchTask", "serviceTask"), 7, 8));
    processInstance
        .getIncidents()
        .add(incident("201", "JOB_NO_RETRIES", "bearer token expired", 6));
    processInstance.getIncidents().add(incident("202", "JOB_NO_RETRIES", "timeout", 8));
    processInstance.getIncidents().add(incident("124", "EXTRACT_VALUE_ERROR", "bad output", 9));

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(runWithTools(), processInstance, NOW);

    // then
    assertThat(enriched.getToolIncidentCount()).isEqualTo(2L);
    assertThat(enriched.getToolIncidentsByTool())
        .extracting(
            ToolCallsDto::getToolFlowNodeId, ToolCallsDto::getToolName, ToolCallsDto::getCount)
        .containsExactly(tuple("searchTask", "search", 2L));
    assertThat(enriched.getFailureReasons())
        .extracting(
            FailureReasonDto::getFlowNodeId,
            FailureReasonDto::getToolName,
            FailureReasonDto::getErrorType,
            FailureReasonDto::getMessage,
            FailureReasonDto::getCount)
        .containsExactly(
            tuple("searchTask", "search", "JOB_NO_RETRIES", "bearer token expired", 2L),
            tuple("review", null, "EXTRACT_VALUE_ERROR", "bad output", 1L));
  }

  @Test
  void shouldIgnoreToolIncidentsOutsideTheRun() {
    // given a second run of the same agent element used the tool later
    final ProcessInstanceDto processInstance = processInstance();
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("124", "review", "adHocSubProcess"), 0, 100));
    processInstance
        .getFlowNodeInstances()
        .add(timed(node("301", "searchTask", "serviceTask"), 200, 210));
    processInstance.getIncidents().add(incident("301", "JOB_NO_RETRIES", "later", 210));

    // when
    final AgentRunDto enriched = AgentRunEnrichment.compute(runWithTools(), processInstance, NOW);

    // then
    assertThat(enriched.getToolIncidentCount()).isZero();
    assertThat(enriched.getFailureReasons()).isEmpty();
  }

  @Test
  void shouldTakeTheRootProcessFromItsOwnInstanceOrTheLookedUpKey() {
    // given
    final ProcessInstanceDto processInstance = processInstance();
    processInstance.setProcessDefinitionKey("child-process");
    final AgentRunDto ownRoot = runWithTools();
    ownRoot.setProcessInstanceId("123");
    ownRoot.setRootProcessInstanceId("123");
    final AgentRunDto calledRun = runWithTools();
    calledRun.setProcessInstanceId("123");
    calledRun.setRootProcessInstanceId("99");

    // when
    final AgentRunDto own = AgentRunEnrichment.compute(ownRoot, processInstance, "ignored", NOW);
    final AgentRunDto called =
        AgentRunEnrichment.compute(calledRun, processInstance, "parent-process", NOW);

    // then
    assertThat(own.getRootProcessDefinitionKey()).isEqualTo("child-process");
    assertThat(called.getRootProcessDefinitionKey()).isEqualTo("parent-process");
  }

  private static AgentRunDto runWithTools() {
    final AgentRunDto run = run();
    run.setStartDate(at(0));
    run.setToolFlowNodeIds(new ArrayList<>(List.of("searchTask", "scoreTask", "askHuman")));
    run.getToolCallsByTool().add(tool("askHuman", "ask a person"));
    return run;
  }

  private static OffsetDateTime at(final long seconds) {
    return NOW.plusSeconds(seconds);
  }

  private static FlowNodeInstanceDto timed(
      final FlowNodeInstanceDto node, final long startSeconds, final long endSeconds) {
    node.setStartDate(at(startSeconds));
    node.setEndDate(at(endSeconds));
    node.setTotalDurationInMs((endSeconds - startSeconds) * 1000);
    return node;
  }

  private static IncidentDto incident(
      final String activityId, final String errorType, final String message, final long atSeconds) {
    final IncidentDto incident = incident(activityId);
    incident.setIncidentType(IncidentType.valueOfId(errorType));
    incident.setIncidentMessage(message);
    incident.setCreateTime(at(atSeconds));
    return incident;
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
