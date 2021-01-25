/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NoIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonSuspendedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.StartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.SuspendedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.service.es.filter.util.modelelement.FlowNodeFilterQueryUtil;
import org.camunda.optimize.service.es.filter.util.IncidentFilterQueryUtil;
import org.camunda.optimize.service.es.filter.util.modelelement.UserTaskFilterQueryUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessQueryFilterEnhancer implements QueryFilterEnhancer<ProcessFilterDto<?>> {

  @Getter
  private final StartDateQueryFilter startDateQueryFilter;
  @Getter
  private final EndDateQueryFilter endDateQueryFilter;
  private final ProcessVariableQueryFilter variableQueryFilter;
  private final ExecutedFlowNodeQueryFilter executedFlowNodeQueryFilter;
  private final ExecutingFlowNodeQueryFilter executingFlowNodeQueryFilter;
  private final CanceledFlowNodeQueryFilter canceledFlowNodeQueryFilter;
  private final DurationQueryFilter durationQueryFilter;
  private final RunningInstancesOnlyQueryFilter runningInstancesOnlyQueryFilter;
  private final CompletedInstancesOnlyQueryFilter completedInstancesOnlyQueryFilter;
  private final CanceledInstancesOnlyQueryFilter canceledInstancesOnlyQueryFilter;
  private final NonCanceledInstancesOnlyQueryFilter nonCanceledInstancesOnlyQueryFilter;
  private final SuspendedInstancesOnlyQueryFilter suspendedInstancesOnlyQueryFilter;
  private final NonSuspendedInstancesOnlyQueryFilter nonSuspendedInstancesOnlyQueryFilter;
  private final FlowNodeDurationQueryFilter flowNodeDurationQueryFilter;
  private final AssigneeQueryFilter assigneeQueryFilter;
  private final CandidateGroupQueryFilter candidateGroupQueryFilter;
  private final OpenIncidentQueryFilter openIncidentQueryFilter;
  private final ResolvedIncidentQueryFilter resolvedIncidentQueryFilter;
  private final NoIncidentQueryFilter noIncidentQueryFilter;
  private final RunningFlowNodesOnlyQueryFilter runningFlowNodesOnlyQueryFilter;
  private final CompletedFlowNodesOnlyQueryFilter completedFlowNodesOnlyQueryFilter;
  private final CanceledFlowNodesOnlyQueryFilter canceledFlowNodesOnlyQueryFilter;
  private final CompletedOrCanceledFlowNodesOnlyQueryFilter completedOrCanceledFlowNodesOnlyQueryFilter;

  @Override
  public void addFilterToQuery(BoolQueryBuilder query, List<ProcessFilterDto<?>> filters, final ZoneId timezone) {
    if (!CollectionUtils.isEmpty(filters)) {
      startDateQueryFilter.addFilters(query, extractFilters(filters, StartDateFilterDto.class), timezone);
      endDateQueryFilter.addFilters(query, extractFilters(filters, EndDateFilterDto.class), timezone);
      variableQueryFilter.addFilters(query, extractFilters(filters, VariableFilterDto.class), timezone);
      executedFlowNodeQueryFilter.addFilters(query, extractFilters(filters, ExecutedFlowNodeFilterDto.class), timezone);
      executingFlowNodeQueryFilter.addFilters(
        query, extractFilters(filters, ExecutingFlowNodeFilterDto.class), timezone);
      canceledFlowNodeQueryFilter.addFilters(query, extractFilters(filters, CanceledFlowNodeFilterDto.class), timezone);
      durationQueryFilter.addFilters(query, extractFilters(filters, DurationFilterDto.class), timezone);
      runningInstancesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, RunningInstancesOnlyFilterDto.class), timezone);
      completedInstancesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, CompletedInstancesOnlyFilterDto.class), timezone);
      canceledInstancesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, CanceledInstancesOnlyFilterDto.class), timezone);
      nonCanceledInstancesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, NonCanceledInstancesOnlyFilterDto.class), timezone);
      suspendedInstancesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, SuspendedInstancesOnlyFilterDto.class), timezone);
      nonSuspendedInstancesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, NonSuspendedInstancesOnlyFilterDto.class), timezone);
      flowNodeDurationQueryFilter.addFilters(query, extractFilters(filters, FlowNodeDurationFilterDto.class), timezone);
      assigneeQueryFilter.addFilters(query, extractFilters(filters, AssigneeFilterDto.class), timezone);
      candidateGroupQueryFilter.addFilters(query, extractFilters(filters, CandidateGroupFilterDto.class), timezone);
      openIncidentQueryFilter.addFilters(query, extractFilters(filters, OpenIncidentFilterDto.class), timezone);
      resolvedIncidentQueryFilter.addFilters(query, extractFilters(filters, ResolvedIncidentFilterDto.class), timezone);
      noIncidentQueryFilter.addFilters(query, extractFilters(filters, NoIncidentFilterDto.class), timezone);
      runningFlowNodesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, RunningFlowNodesOnlyFilterDto.class), timezone);
      completedFlowNodesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, CompletedFlowNodesOnlyFilterDto.class), timezone);
      canceledFlowNodesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, CanceledFlowNodesOnlyFilterDto.class), timezone);
      completedOrCanceledFlowNodesOnlyQueryFilter.addFilters(
        query, extractFilters(filters, CompletedOrCanceledFlowNodesOnlyFilterDto.class), timezone);
    }
    addInstanceFilterForViewLevelMatching(query, filters);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends FilterDataDto> List<T> extractFilters(final List<ProcessFilterDto<?>> filter,
                                                          final Class<? extends ProcessFilterDto<T>> clazz) {
    return filter
      .stream()
      .filter(clazz::isInstance)
      .map(dateFilter -> (T) dateFilter.getData())
      .collect(Collectors.toList());
  }

  private void addInstanceFilterForViewLevelMatching(final BoolQueryBuilder query,
                                                     final List<ProcessFilterDto<?>> filters) {
    UserTaskFilterQueryUtil.addInstanceFilterForRelevantViewLevelFilters(filters).ifPresent(query::filter);
    FlowNodeFilterQueryUtil.addInstanceFilterForRelevantViewLevelFilters(filters).ifPresent(query::filter);
    IncidentFilterQueryUtil.addInstanceFilterForRelevantViewLevelFilters(filters).ifPresent(query::filter);
  }

}
