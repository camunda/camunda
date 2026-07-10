/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.groupby.flownode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.groupby.flownode.ProcessGroupByFlowNodeInterpreterHelper.AdHocSubProcessStructure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessGroupByFlowNodeInterpreterHelperTest {

  @Mock private DefinitionService definitionService;

  @SuppressWarnings("rawtypes")
  @Mock
  private ExecutionContext context;

  private ProcessGroupByFlowNodeInterpreterHelper underTest;

  private ProcessGroupByFlowNodeInterpreterHelper helper() {
    return new ProcessGroupByFlowNodeInterpreterHelper(definitionService);
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldMapKnownBucketsAndBackfillUnexecutedFlowNodes() {
    // given three modelled flow nodes but only two of them appear as buckets
    underTest = helper();
    stubFlowNodeNames("task-a", "Task A", "task-b", "Task B", "task-c", "Task C");
    when(context.getReportData()).thenReturn(new ProcessReportDataDto());

    // when
    final List<GroupByResult> groups =
        underTest.mapFlowNodeBucketsToGroupByResults(
            List.of("task-a", "task-b"),
            Function.identity(),
            bucket -> List.of(),
            context,
            List.of());

    // then matched buckets are labelled from the lookup and the missing node is backfilled
    assertThat(groups)
        .extracting(GroupByResult::getKey)
        .containsExactlyInAnyOrder("task-a", "task-b", "task-c");
    assertThat(groups)
        .extracting(GroupByResult::getKey, GroupByResult::getLabel)
        .contains(
            org.assertj.core.groups.Tuple.tuple("task-a", "Task A"),
            org.assertj.core.groups.Tuple.tuple("task-b", "Task B"),
            org.assertj.core.groups.Tuple.tuple("task-c", "Task C"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldOmitBucketsWithoutMatchingFlowNodeName() {
    // given no modelled flow nodes match the bucket key (e.g. a deleted element)
    underTest = helper();
    when(definitionService.extractFlowNodeIdAndNames(any())).thenReturn(new LinkedHashMap<>());
    when(context.getReportData()).thenReturn(new ProcessReportDataDto());
    final Function<String, List<DistributedByResult>> resultExtractor = mock(Function.class);

    // when
    final List<GroupByResult> groups =
        underTest.mapFlowNodeBucketsToGroupByResults(
            List.of("unknown-flow-node"), Function.identity(), resultExtractor, context, List.of());

    // then the unmatched bucket is dropped and its distributed-by result is never resolved
    assertThat(groups).isEmpty();
    verify(resultExtractor, never()).apply(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldRemoveHiddenModelElements() {
    // given two matched flow nodes, one of which is hidden on the diagram
    underTest = helper();
    stubFlowNodeNames("task-a", "Task A", "task-b", "Task B");
    when(context.getReportData()).thenReturn(new ProcessReportDataDto());
    when(context.getHiddenFlowNodeIds()).thenReturn(Set.of("task-b"));

    // when
    final List<GroupByResult> groups =
        underTest.mapFlowNodeBucketsToGroupByResults(
            List.of("task-a", "task-b"),
            Function.identity(),
            bucket -> List.of(),
            context,
            List.of());

    // then the hidden node is stripped from the result
    assertThat(groups).extracting(GroupByResult::getKey).containsExactly("task-a");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldNotBackfillWhenAViewLevelFilterExists() {
    // given a modelled flow node that never appears as a bucket, but a view-level filter is set
    underTest = helper();
    stubFlowNodeNames("task-a", "Task A");
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    final ProcessFilterDto<?> viewFilter = mock(ProcessFilterDto.class);
    when(viewFilter.getFilterLevel()).thenReturn(FilterApplicationLevel.VIEW);
    reportData.setFilter(List.of(viewFilter));
    when(context.getReportData()).thenReturn(reportData);

    // when
    final List<GroupByResult> groups =
        underTest.mapFlowNodeBucketsToGroupByResults(
            List.of(), Function.identity(), bucket -> List.of(), context, List.of());

    // then no enrichment happens because the filter may legitimately exclude the missing node
    assertThat(groups).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldSkipAdHocSubProcessContainerAndEmitInnerToolsFromFlowNodeBuckets() {
    // given an AI Agent service task, an ad-hoc subprocess container and its two inner tool nodes
    underTest = helper();
    stubFlowNodeNames(
        "agent-task", "Agent Task", "ahsp", "AI Agent", "tool-a", "Tool A", "tool-b", "Tool B");
    when(context.getReportData()).thenReturn(new ProcessReportDataDto());
    final AdHocSubProcessStructure structure =
        new AdHocSubProcessStructure(Set.of("ahsp"), Set.of("tool-a", "tool-b"));
    final Function<String, List<DistributedByResult>> agentResultExtractor = mock(Function.class);
    when(agentResultExtractor.apply(any())).thenReturn(List.of());
    final Function<String, List<DistributedByResult>> innerToolResultExtractor =
        mock(Function.class);
    when(innerToolResultExtractor.apply(any())).thenReturn(List.of());

    // when
    final List<GroupByResult> groups =
        underTest.mapAgentFlowNodeBucketsToGroupByResults(
            List.of("agent-task", "ahsp"),
            Function.identity(),
            agentResultExtractor,
            List.of("tool-a", "tool-b"),
            Function.identity(),
            innerToolResultExtractor,
            structure,
            context,
            List.of());

    // then the non-container agent node keeps its aggregated value, the inner tools are emitted,
    // and
    // the container is not valued from the agent bucket (only backfilled empty)
    assertThat(groups)
        .extracting(GroupByResult::getKey)
        .containsExactlyInAnyOrder("agent-task", "tool-a", "tool-b", "ahsp");
    verify(agentResultExtractor).apply("agent-task");
    verify(agentResultExtractor, never()).apply("ahsp");
    verify(innerToolResultExtractor).apply("tool-a");
    verify(innerToolResultExtractor).apply("tool-b");
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldOnlyEmitInnerToolBucketsThatAreAdHocSubProcessChildren() {
    // given an inner-tool bucket that is not a child of any ad-hoc subprocess (e.g. an unrelated
    // flow node instance)
    underTest = helper();
    stubFlowNodeNames("ahsp", "AI Agent", "tool-a", "Tool A", "other", "Other Task");
    when(context.getReportData()).thenReturn(new ProcessReportDataDto());
    final AdHocSubProcessStructure structure =
        new AdHocSubProcessStructure(Set.of("ahsp"), Set.of("tool-a"));
    final Function<String, List<DistributedByResult>> innerToolResultExtractor =
        mock(Function.class);
    when(innerToolResultExtractor.apply(any())).thenReturn(List.of());

    // when
    final List<GroupByResult> groups =
        underTest.mapAgentFlowNodeBucketsToGroupByResults(
            List.of("ahsp"),
            Function.identity(),
            bucket -> List.of(),
            List.of("tool-a", "other"),
            Function.identity(),
            innerToolResultExtractor,
            structure,
            context,
            List.of());

    // then only the ad-hoc subprocess child tool is valued from the flow-node bucket; the unrelated
    // node is not (though it is still backfilled as a modelled node)
    verify(innerToolResultExtractor).apply("tool-a");
    verify(innerToolResultExtractor, never()).apply("other");
    assertThat(groups).extracting(GroupByResult::getKey).contains("tool-a", "other", "ahsp");
  }

  @Test
  void shouldSetEveryViewMeasureToDocCountInFrequencyResult() {
    // given a distributed-by result with two (unset) view measures
    underTest = helper();
    final ViewMeasure measureA = ViewMeasure.builder().value(null).build();
    final ViewMeasure measureB = ViewMeasure.builder().value(null).build();
    final DistributedByResult result =
        DistributedByResult.createDistributedByNoneResult(
            ViewResult.builder().viewMeasures(List.of(measureA, measureB)).build());

    // when
    final List<DistributedByResult> frequencyResult =
        underTest.toFrequencyResult(new ArrayList<>(List.of(result)), 7L);

    // then every view measure carries the bucket's document count as its value
    assertThat(frequencyResult).hasSize(1);
    assertThat(measureA.getValue()).isEqualTo(7.0);
    assertThat(measureB.getValue()).isEqualTo(7.0);
  }

  @Test
  void shouldNotFailFrequencyResultWhenViewResultOrMeasuresAreNull() {
    // given distributed-by results with a null view result and a null measures list
    underTest = helper();
    final DistributedByResult nullViewResult =
        DistributedByResult.createDistributedByNoneResult(null);
    final DistributedByResult nullMeasures =
        DistributedByResult.createDistributedByNoneResult(ViewResult.builder().build());

    // when / then no exception is raised and the same list is returned
    final List<DistributedByResult> input = new ArrayList<>(List.of(nullViewResult, nullMeasures));
    assertThat(underTest.toFrequencyResult(input, 3L)).isSameAs(input);
  }

  private void stubFlowNodeNames(final String... keyLabelPairs) {
    final Map<String, String> names = new LinkedHashMap<>();
    for (int i = 0; i < keyLabelPairs.length; i += 2) {
      names.put(keyLabelPairs[i], keyLabelPairs[i + 1]);
    }
    when(definitionService.extractFlowNodeIdAndNames(any())).thenReturn(names);
  }
}
