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
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.DistributedByResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
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

  private void stubFlowNodeNames(final String... keyLabelPairs) {
    final Map<String, String> names = new LinkedHashMap<>();
    for (int i = 0; i < keyLabelPairs.length; i += 2) {
      names.put(keyLabelPairs[i], keyLabelPairs[i + 1]);
    }
    when(definitionService.extractFlowNodeIdAndNames(any())).thenReturn(names);
  }
}
