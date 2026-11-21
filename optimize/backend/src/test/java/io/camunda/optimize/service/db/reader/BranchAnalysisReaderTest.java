/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisOutcomeDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import io.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BranchAnalysisReaderTest {

  private static final String PROCESS_KEY = "branchAnalysisProcess";
  private static final long FLOW_NODE_COUNT = 10L;
  private static final long REACHED_END_COUNT = 4L;

  private DefinitionService definitionService;
  private RecordingBranchAnalysisReader reader;

  @BeforeEach
  void setUp() {
    definitionService = mock(DefinitionService.class);
    reader = new RecordingBranchAnalysisReader(definitionService);
  }

  @Test
  void shouldExcludeTheOtherBranchesWhenAnalysingEachGatewayOutcome() {
    // given
    givenProcess(
        Bpmn.createExecutableProcess(PROCESS_KEY)
            .startEvent("start")
            .exclusiveGateway("split")
            .manualTask("taskA")
            .exclusiveGateway("merge")
            .endEvent("end")
            .moveToNode("split")
            .manualTask("taskB")
            .connectTo("merge")
            .done());

    // when
    final BranchAnalysisResponseDto response = analyse("split", "end");

    // then
    assertThat(response.getEndEvent()).isEqualTo("end");
    assertThat(response.getTotal()).isEqualTo(FLOW_NODE_COUNT);
    assertThat(response.getFollowingNodes())
        .containsOnlyKeys("taskA", "taskB")
        .allSatisfy((activityId, outcome) -> assertOutcome(outcome, activityId, REACHED_END_COUNT));
    assertThat(reader.excludedActivitiesByFlowNode)
        .containsOnly(
            Map.entry("taskA", Set.of("taskB")),
            Map.entry("taskB", Set.of("taskA")),
            Map.entry("end", Set.of()));
    assertThat(reader.reachedEndQueries).containsOnly("taskA", "taskB");
  }

  @Test
  void shouldNotExcludeAGatewayOutcomeThatOtherFlowsAlsoLeadTo() {
    // given
    givenProcess(
        Bpmn.createExecutableProcess(PROCESS_KEY)
            .startEvent("start")
            .manualTask("retry")
            .exclusiveGateway("split")
            .manualTask("taskB")
            .endEvent("end")
            .moveToNode("split")
            .connectTo("retry")
            .done());

    // when
    final BranchAnalysisResponseDto response = analyse("split", "end");

    // then
    assertThat(response.getFollowingNodes())
        .containsOnlyKeys("retry", "taskB")
        .allSatisfy((activityId, outcome) -> assertOutcome(outcome, activityId, REACHED_END_COUNT));
    assertThat(reader.excludedActivitiesByFlowNode)
        .containsOnly(
            Map.entry("retry", Set.of("taskB")),
            Map.entry("taskB", Set.of()),
            Map.entry("end", Set.of()));
  }

  @Test
  void shouldReportNoReachedEndWhenTheEndCannotBeReachedFromTheGateway() {
    // given
    givenProcess(
        Bpmn.createExecutableProcess(PROCESS_KEY)
            .startEvent("start")
            .manualTask("beforeSplit")
            .exclusiveGateway("split")
            .manualTask("taskA")
            .endEvent("endA")
            .moveToNode("split")
            .manualTask("taskB")
            .endEvent("endB")
            .done());

    // when
    final BranchAnalysisResponseDto response = analyse("split", "beforeSplit");

    // then
    assertThat(response.getEndEvent()).isEqualTo("beforeSplit");
    assertThat(response.getFollowingNodes())
        .containsOnlyKeys("taskA", "taskB")
        .allSatisfy((activityId, outcome) -> assertOutcome(outcome, activityId, 0L));
    assertThat(reader.reachedEndQueries).isEmpty();
  }

  @Test
  void shouldReturnNoOutcomesWhenTheDefinitionIsNotFound() {
    // given
    when(definitionService.<ProcessDefinitionOptimizeDto>getDefinitionWithXmlAsService(
            eq(DefinitionType.PROCESS), eq(PROCESS_KEY), any(), any()))
        .thenReturn(Optional.empty());

    // when
    final BranchAnalysisResponseDto response = analyse("split", "end");

    // then
    assertThat(response.getFollowingNodes()).isEmpty();
    assertThat(reader.excludedActivitiesByFlowNode).isEmpty();
  }

  private void givenProcess(final BpmnModelInstance process) {
    final ProcessDefinitionOptimizeDto definition =
        ProcessDefinitionOptimizeDto.builder()
            .key(PROCESS_KEY)
            .bpmn20Xml(Bpmn.convertToString(process))
            .build();
    when(definitionService.<ProcessDefinitionOptimizeDto>getDefinitionWithXmlAsService(
            eq(DefinitionType.PROCESS), eq(PROCESS_KEY), any(), any()))
        .thenReturn(Optional.of(definition));
  }

  private BranchAnalysisResponseDto analyse(final String gateway, final String end) {
    final BranchAnalysisRequestDto request = new BranchAnalysisRequestDto();
    request.setProcessDefinitionKey(PROCESS_KEY);
    request.setProcessDefinitionVersion("1");
    request.setGateway(gateway);
    request.setEnd(end);
    return reader.branchAnalysis(request, ZoneId.of("UTC"));
  }

  private static void assertOutcome(
      final BranchAnalysisOutcomeDto outcome, final String activityId, final long reached) {
    assertThat(outcome.getActivityId()).isEqualTo(activityId);
    assertThat(outcome.getActivityCount()).isEqualTo(FLOW_NODE_COUNT);
    assertThat(outcome.getActivitiesReached()).isEqualTo(reached);
  }

  /** Records what the reader asks the database for, instead of querying one. */
  private static final class RecordingBranchAnalysisReader extends BranchAnalysisReader {

    private final Map<String, Set<String>> excludedActivitiesByFlowNode = new HashMap<>();
    private final Set<String> reachedEndQueries = new HashSet<>();

    private RecordingBranchAnalysisReader(final DefinitionService definitionService) {
      super(definitionService);
    }

    @Override
    protected long calculateFlowNodeCount(
        final String flowNodeId,
        final BranchAnalysisRequestDto request,
        final Set<String> activitiesToExclude,
        final ZoneId timezone) {
      excludedActivitiesByFlowNode.put(flowNodeId, Set.copyOf(activitiesToExclude));
      return FLOW_NODE_COUNT;
    }

    @Override
    protected long calculateReachedEndEventFlowNodeCount(
        final String flowNodeId,
        final BranchAnalysisRequestDto request,
        final Set<String> activitiesToExclude,
        final ZoneId timezone) {
      reachedEndQueries.add(flowNodeId);
      return REACHED_END_COUNT;
    }
  }
}
