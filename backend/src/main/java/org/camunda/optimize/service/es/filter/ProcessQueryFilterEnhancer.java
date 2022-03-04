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
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DeletedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.MultipleVariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NoIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.NonSuspendedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.SuspendedInstancesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.UserTaskFlowNodesOnlyFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import org.camunda.optimize.service.es.filter.util.IncidentFilterQueryUtil;
import org.camunda.optimize.service.es.filter.util.ModelElementFilterQueryUtil;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@RequiredArgsConstructor
@Component
@Slf4j
public class ProcessQueryFilterEnhancer implements QueryFilterEnhancer<ProcessFilterDto<?>> {

  @Getter
  private final InstanceStartDateQueryFilter instanceStartDateQueryFilter;
  @Getter
  private final InstanceEndDateQueryFilter instanceEndDateQueryFilter;
  private final ProcessVariableQueryFilter variableQueryFilter;
  private final ProcessMultiVariableQueryFilter multiVariableQueryFilter;
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
  private final DeletedIncidentQueryFilter deletedIncidentQueryFilter;
  private final ResolvedIncidentQueryFilter resolvedIncidentQueryFilter;
  private final NoIncidentQueryFilter noIncidentQueryFilter;
  private final RunningFlowNodesOnlyQueryFilter runningFlowNodesOnlyQueryFilter;
  private final CompletedFlowNodesOnlyQueryFilter completedFlowNodesOnlyQueryFilter;
  private final CanceledFlowNodesOnlyQueryFilter canceledFlowNodesOnlyQueryFilter;
  private final CompletedOrCanceledFlowNodesOnlyQueryFilter completedOrCanceledFlowNodesOnlyQueryFilter;
  private final InstancesContainingUserTasksFilter instancesContainingUserTasksFilter;
  private final FlowNodeStartDateQueryFilter flowNodeStartDateQueryFilter;
  private final FlowNodeEndDateQueryFilter flowNodeEndDateQueryFilter;

  @Override
  public void addFilterToQuery(final BoolQueryBuilder query,
                               final List<ProcessFilterDto<?>> filters,
                               final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(filters)) {
      instanceStartDateQueryFilter.addFilters(
        query, extractInstanceFilters(filters, InstanceStartDateFilterDto.class), filterContext
      );
      instanceEndDateQueryFilter.addFilters(
        query, extractInstanceFilters(filters, InstanceEndDateFilterDto.class), filterContext
      );
      variableQueryFilter.addFilters(
        query, extractInstanceFilters(filters, VariableFilterDto.class), filterContext
      );
      multiVariableQueryFilter.addFilters(
        query, extractInstanceFilters(filters, MultipleVariableFilterDto.class), filterContext
      );
      executedFlowNodeQueryFilter.addFilters(
        query, extractInstanceFilters(filters, ExecutedFlowNodeFilterDto.class), filterContext
      );
      executingFlowNodeQueryFilter.addFilters(
        query, extractInstanceFilters(filters, ExecutingFlowNodeFilterDto.class), filterContext
      );
      canceledFlowNodeQueryFilter.addFilters(
        query, extractInstanceFilters(filters, CanceledFlowNodeFilterDto.class), filterContext
      );
      durationQueryFilter.addFilters(
        query, extractInstanceFilters(filters, DurationFilterDto.class), filterContext
      );
      runningInstancesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, RunningInstancesOnlyFilterDto.class), filterContext
      );
      completedInstancesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, CompletedInstancesOnlyFilterDto.class), filterContext
      );
      canceledInstancesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, CanceledInstancesOnlyFilterDto.class), filterContext
      );
      nonCanceledInstancesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, NonCanceledInstancesOnlyFilterDto.class), filterContext
      );
      suspendedInstancesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, SuspendedInstancesOnlyFilterDto.class), filterContext
      );
      nonSuspendedInstancesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, NonSuspendedInstancesOnlyFilterDto.class), filterContext
      );
      flowNodeDurationQueryFilter.addFilters(
        query, extractInstanceFilters(filters, FlowNodeDurationFilterDto.class), filterContext
      );
      assigneeQueryFilter.addFilters(
        query, extractInstanceFilters(filters, AssigneeFilterDto.class), filterContext
      );
      candidateGroupQueryFilter.addFilters(
        query, extractInstanceFilters(filters, CandidateGroupFilterDto.class), filterContext
      );
      openIncidentQueryFilter.addFilters(
        query, extractInstanceFilters(filters, OpenIncidentFilterDto.class), filterContext
      );
      deletedIncidentQueryFilter.addFilters(
        query, extractInstanceFilters(filters, DeletedIncidentFilterDto.class), filterContext
      );
      resolvedIncidentQueryFilter.addFilters(
        query, extractInstanceFilters(filters, ResolvedIncidentFilterDto.class), filterContext
      );
      noIncidentQueryFilter.addFilters(
        query, extractInstanceFilters(filters, NoIncidentFilterDto.class), filterContext
      );
      runningFlowNodesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, RunningFlowNodesOnlyFilterDto.class), filterContext
      );
      completedFlowNodesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, CompletedFlowNodesOnlyFilterDto.class), filterContext
      );
      canceledFlowNodesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, CanceledFlowNodesOnlyFilterDto.class), filterContext
      );
      completedOrCanceledFlowNodesOnlyQueryFilter.addFilters(
        query, extractInstanceFilters(filters, CompletedOrCanceledFlowNodesOnlyFilterDto.class), filterContext
      );
      instancesContainingUserTasksFilter.addFilters(
        query, extractInstanceFilters(filters, UserTaskFlowNodesOnlyFilterDto.class), filterContext
      );
      flowNodeStartDateQueryFilter.addFilters(
        query, extractInstanceFilters(filters, FlowNodeStartDateFilterDto.class), filterContext
      );
      flowNodeEndDateQueryFilter.addFilters(
        query, extractInstanceFilters(filters, FlowNodeEndDateFilterDto.class), filterContext
      );
    }
    addInstanceFilterForViewLevelMatching(query, filters, filterContext);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends FilterDataDto> List<T> extractInstanceFilters(final List<ProcessFilterDto<?>> filter,
                                                                  final Class<? extends ProcessFilterDto<T>> clazz) {
    return filter
      .stream()
      .filter(clazz::isInstance)
      .filter(f -> FilterApplicationLevel.INSTANCE.equals(f.getFilterLevel()))
      .map(dateFilter -> (T) dateFilter.getData())
      .collect(Collectors.toList());
  }

  private void addInstanceFilterForViewLevelMatching(final BoolQueryBuilder query,
                                                     final List<ProcessFilterDto<?>> filters,
                                                     final FilterContext filterContext) {
    ModelElementFilterQueryUtil.addInstanceFilterForRelevantViewLevelFilters(filters, filterContext)
      .ifPresent(query::filter);
    IncidentFilterQueryUtil.addInstanceFilterForRelevantViewLevelFilters(filters).ifPresent(query::filter);
  }

}
