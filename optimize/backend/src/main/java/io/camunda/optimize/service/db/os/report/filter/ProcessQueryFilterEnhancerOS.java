/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.AssigneeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CanceledInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CandidateGroupFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.CompletedOrCanceledFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.DeletedIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.DurationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutedFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ExecutingFlowNodeFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeDurationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.FlowNodeStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceEndDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.InstanceStartDateFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.MultipleVariableFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.NoIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.NonCanceledInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.NonSuspendedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.OpenIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.ResolvedIncidentFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.RunningInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.SuspendedInstancesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.UserTaskFlowNodesOnlyFilterDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.filter.VariableFilterDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.os.report.filter.util.IncidentFilterQueryUtilOS;
import io.camunda.optimize.service.db.os.report.filter.util.ModelElementFilterQueryUtilOS;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.ListUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessQueryFilterEnhancerOS implements QueryFilterEnhancerOS<ProcessFilterDto<?>> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessQueryFilterEnhancerOS.class);
  private final ConfigurationService configurationService;
  private final Environment environment;
  private final InstanceStartDateQueryFilterOS instanceStartDateQueryFilter;
  private final InstanceEndDateQueryFilterOS instanceEndDateQueryFilter;
  private final ProcessVariableQueryFilterOS variableQueryFilter;
  private final ProcessMultiVariableQueryFilterOS multiVariableQueryFilter;
  private final ExecutedFlowNodeQueryFilterOS executedFlowNodeQueryFilter;
  private final ExecutingFlowNodeQueryFilterOS executingFlowNodeQueryFilter;
  private final CanceledFlowNodeQueryFilterOS canceledFlowNodeQueryFilter;
  private final DurationQueryFilterOS durationQueryFilter;
  private final RunningInstancesOnlyQueryFilterOS runningInstancesOnlyQueryFilter;
  private final CompletedInstancesOnlyQueryFilterOS completedInstancesOnlyQueryFilter;
  private final CanceledInstancesOnlyQueryFilterOS canceledInstancesOnlyQueryFilter;
  private final NonCanceledInstancesOnlyQueryFilterOS nonCanceledInstancesOnlyQueryFilter;
  private final SuspendedInstancesOnlyQueryFilterOS suspendedInstancesOnlyQueryFilter;
  private final NonSuspendedInstancesOnlyQueryFilterOS nonSuspendedInstancesOnlyQueryFilter;
  private final FlowNodeDurationQueryFilterOS flowNodeDurationQueryFilter;
  private final AssigneeQueryFilterOS assigneeQueryFilter;
  private final CandidateGroupQueryFilterOS candidateGroupQueryFilter;
  private final OpenIncidentQueryFilterOS openIncidentQueryFilter;
  private final DeletedIncidentQueryFilterOS deletedIncidentQueryFilter;
  private final ResolvedIncidentQueryFilterOS resolvedIncidentQueryFilter;
  private final NoIncidentQueryFilterOS noIncidentQueryFilter;
  private final RunningFlowNodesOnlyQueryFilterOS runningFlowNodesOnlyQueryFilter;
  private final CompletedFlowNodesOnlyQueryFilterOS completedFlowNodesOnlyQueryFilter;
  private final CanceledFlowNodesOnlyQueryFilterOS canceledFlowNodesOnlyQueryFilter;
  private final CompletedOrCanceledFlowNodesOnlyQueryFilterOS
      completedOrCanceledFlowNodesOnlyQueryFilter;
  private final InstancesContainingUserTasksFilterOS instancesContainingUserTasksFilter;
  private final FlowNodeStartDateQueryFilterOS flowNodeStartDateQueryFilter;
  private final FlowNodeEndDateQueryFilterOS flowNodeEndDateQueryFilter;

  public ProcessQueryFilterEnhancerOS(
      final ConfigurationService configurationService,
      final Environment environment,
      final InstanceStartDateQueryFilterOS instanceStartDateQueryFilter,
      final InstanceEndDateQueryFilterOS instanceEndDateQueryFilter,
      final ProcessVariableQueryFilterOS variableQueryFilter,
      final ProcessMultiVariableQueryFilterOS multiVariableQueryFilter,
      final ExecutedFlowNodeQueryFilterOS executedFlowNodeQueryFilter,
      final ExecutingFlowNodeQueryFilterOS executingFlowNodeQueryFilter,
      final CanceledFlowNodeQueryFilterOS canceledFlowNodeQueryFilter,
      final DurationQueryFilterOS durationQueryFilter,
      final RunningInstancesOnlyQueryFilterOS runningInstancesOnlyQueryFilter,
      final CompletedInstancesOnlyQueryFilterOS completedInstancesOnlyQueryFilter,
      final CanceledInstancesOnlyQueryFilterOS canceledInstancesOnlyQueryFilter,
      final NonCanceledInstancesOnlyQueryFilterOS nonCanceledInstancesOnlyQueryFilter,
      final SuspendedInstancesOnlyQueryFilterOS suspendedInstancesOnlyQueryFilter,
      final NonSuspendedInstancesOnlyQueryFilterOS nonSuspendedInstancesOnlyQueryFilter,
      final FlowNodeDurationQueryFilterOS flowNodeDurationQueryFilter,
      final AssigneeQueryFilterOS assigneeQueryFilter,
      final CandidateGroupQueryFilterOS candidateGroupQueryFilter,
      final OpenIncidentQueryFilterOS openIncidentQueryFilter,
      final DeletedIncidentQueryFilterOS deletedIncidentQueryFilter,
      final ResolvedIncidentQueryFilterOS resolvedIncidentQueryFilter,
      final NoIncidentQueryFilterOS noIncidentQueryFilter,
      final RunningFlowNodesOnlyQueryFilterOS runningFlowNodesOnlyQueryFilter,
      final CompletedFlowNodesOnlyQueryFilterOS completedFlowNodesOnlyQueryFilter,
      final CanceledFlowNodesOnlyQueryFilterOS canceledFlowNodesOnlyQueryFilter,
      final CompletedOrCanceledFlowNodesOnlyQueryFilterOS
          completedOrCanceledFlowNodesOnlyQueryFilter,
      final InstancesContainingUserTasksFilterOS instancesContainingUserTasksFilter,
      final FlowNodeStartDateQueryFilterOS flowNodeStartDateQueryFilter,
      final FlowNodeEndDateQueryFilterOS flowNodeEndDateQueryFilter) {
    this.configurationService = configurationService;
    this.environment = environment;
    this.instanceStartDateQueryFilter = instanceStartDateQueryFilter;
    this.instanceEndDateQueryFilter = instanceEndDateQueryFilter;
    this.variableQueryFilter = variableQueryFilter;
    this.multiVariableQueryFilter = multiVariableQueryFilter;
    this.executedFlowNodeQueryFilter = executedFlowNodeQueryFilter;
    this.executingFlowNodeQueryFilter = executingFlowNodeQueryFilter;
    this.canceledFlowNodeQueryFilter = canceledFlowNodeQueryFilter;
    this.durationQueryFilter = durationQueryFilter;
    this.runningInstancesOnlyQueryFilter = runningInstancesOnlyQueryFilter;
    this.completedInstancesOnlyQueryFilter = completedInstancesOnlyQueryFilter;
    this.canceledInstancesOnlyQueryFilter = canceledInstancesOnlyQueryFilter;
    this.nonCanceledInstancesOnlyQueryFilter = nonCanceledInstancesOnlyQueryFilter;
    this.suspendedInstancesOnlyQueryFilter = suspendedInstancesOnlyQueryFilter;
    this.nonSuspendedInstancesOnlyQueryFilter = nonSuspendedInstancesOnlyQueryFilter;
    this.flowNodeDurationQueryFilter = flowNodeDurationQueryFilter;
    this.assigneeQueryFilter = assigneeQueryFilter;
    this.candidateGroupQueryFilter = candidateGroupQueryFilter;
    this.openIncidentQueryFilter = openIncidentQueryFilter;
    this.deletedIncidentQueryFilter = deletedIncidentQueryFilter;
    this.resolvedIncidentQueryFilter = resolvedIncidentQueryFilter;
    this.noIncidentQueryFilter = noIncidentQueryFilter;
    this.runningFlowNodesOnlyQueryFilter = runningFlowNodesOnlyQueryFilter;
    this.completedFlowNodesOnlyQueryFilter = completedFlowNodesOnlyQueryFilter;
    this.canceledFlowNodesOnlyQueryFilter = canceledFlowNodesOnlyQueryFilter;
    this.completedOrCanceledFlowNodesOnlyQueryFilter = completedOrCanceledFlowNodesOnlyQueryFilter;
    this.instancesContainingUserTasksFilter = instancesContainingUserTasksFilter;
    this.flowNodeStartDateQueryFilter = flowNodeStartDateQueryFilter;
    this.flowNodeEndDateQueryFilter = flowNodeEndDateQueryFilter;
  }

  @Override
  public List<Query> filterQueries(
      final List<ProcessFilterDto<?>> filters, final FilterContext filterContext) {
    final List<Query> queries = new ArrayList<>();
    if (!CollectionUtils.isEmpty(filters)) {
      queries.addAll(allQueries(filters, filterContext));
      if (isAssigneeFiltersEnabled()) {
        queries.addAll(
            assigneeQueryFilter.filterQueries(
                extractInstanceFilters(filters, AssigneeFilterDto.class), filterContext));
      }
    }
    queries.addAll(instanceFilterForViewLevelMatchingQueries(filters, filterContext));
    return queries;
  }

  private List<Query> allQueries(
      final List<ProcessFilterDto<?>> filters, final FilterContext filterContext) {
    return ListUtil.concat(
        instanceStartDateQueryFilter.filterQueries(
            extractInstanceFilters(filters, InstanceStartDateFilterDto.class), filterContext),
        instanceEndDateQueryFilter.filterQueries(
            extractInstanceFilters(filters, InstanceEndDateFilterDto.class), filterContext),
        variableQueryFilter.filterQueries(
            extractInstanceFilters(filters, VariableFilterDto.class), filterContext),
        multiVariableQueryFilter.filterQueries(
            extractInstanceFilters(filters, MultipleVariableFilterDto.class), filterContext),
        executedFlowNodeQueryFilter.filterQueries(
            extractInstanceFilters(filters, ExecutedFlowNodeFilterDto.class), filterContext),
        executingFlowNodeQueryFilter.filterQueries(
            extractInstanceFilters(filters, ExecutingFlowNodeFilterDto.class), filterContext),
        canceledFlowNodeQueryFilter.filterQueries(
            extractInstanceFilters(filters, CanceledFlowNodeFilterDto.class), filterContext),
        durationQueryFilter.filterQueries(
            extractInstanceFilters(filters, DurationFilterDto.class), filterContext),
        runningInstancesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, RunningInstancesOnlyFilterDto.class), filterContext),
        completedInstancesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, CompletedInstancesOnlyFilterDto.class), filterContext),
        canceledInstancesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, CanceledInstancesOnlyFilterDto.class), filterContext),
        nonCanceledInstancesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, NonCanceledInstancesOnlyFilterDto.class),
            filterContext),
        suspendedInstancesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, SuspendedInstancesOnlyFilterDto.class), filterContext),
        nonSuspendedInstancesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, NonSuspendedInstancesOnlyFilterDto.class),
            filterContext),
        flowNodeDurationQueryFilter.filterQueries(
            extractInstanceFilters(filters, FlowNodeDurationFilterDto.class), filterContext),
        candidateGroupQueryFilter.filterQueries(
            extractInstanceFilters(filters, CandidateGroupFilterDto.class), filterContext),
        openIncidentQueryFilter.filterQueries(
            extractInstanceFilters(filters, OpenIncidentFilterDto.class), filterContext),
        deletedIncidentQueryFilter.filterQueries(
            extractInstanceFilters(filters, DeletedIncidentFilterDto.class), filterContext),
        resolvedIncidentQueryFilter.filterQueries(
            extractInstanceFilters(filters, ResolvedIncidentFilterDto.class), filterContext),
        noIncidentQueryFilter.filterQueries(
            extractInstanceFilters(filters, NoIncidentFilterDto.class), filterContext),
        runningFlowNodesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, RunningFlowNodesOnlyFilterDto.class), filterContext),
        completedFlowNodesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, CompletedFlowNodesOnlyFilterDto.class), filterContext),
        canceledFlowNodesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, CanceledFlowNodesOnlyFilterDto.class), filterContext),
        completedOrCanceledFlowNodesOnlyQueryFilter.filterQueries(
            extractInstanceFilters(filters, CompletedOrCanceledFlowNodesOnlyFilterDto.class),
            filterContext),
        instancesContainingUserTasksFilter.filterQueries(
            extractInstanceFilters(filters, UserTaskFlowNodesOnlyFilterDto.class), filterContext),
        flowNodeStartDateQueryFilter.filterQueries(
            extractInstanceFilters(filters, FlowNodeStartDateFilterDto.class), filterContext),
        flowNodeEndDateQueryFilter.filterQueries(
            extractInstanceFilters(filters, FlowNodeEndDateFilterDto.class), filterContext));
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends FilterDataDto> List<T> extractInstanceFilters(
      final List<ProcessFilterDto<?>> filter, final Class<? extends ProcessFilterDto<T>> clazz) {
    return filter.stream()
        .filter(clazz::isInstance)
        .filter(f -> FilterApplicationLevel.INSTANCE.equals(f.getFilterLevel()))
        .map(dateFilter -> (T) dateFilter.getData())
        .collect(Collectors.toList());
  }

  private List<Query> instanceFilterForViewLevelMatchingQueries(
      final List<ProcessFilterDto<?>> filters, final FilterContext filterContext) {
    return Stream.of(
            ModelElementFilterQueryUtilOS.instanceFilterForRelevantViewLevelFiltersQuery(
                filters, filterContext),
            IncidentFilterQueryUtilOS.instanceFilterForRelevantViewLevelFiltersQuery(filters))
        .flatMap(Optional::stream)
        .toList();
  }

  private boolean isAssigneeFiltersEnabled() {
    return configurationService.getUiConfiguration().isUserTaskAssigneeAnalyticsEnabled();
  }

  public InstanceStartDateQueryFilterOS getInstanceStartDateQueryFilter() {
    return this.instanceStartDateQueryFilter;
  }

  public InstanceEndDateQueryFilterOS getInstanceEndDateQueryFilter() {
    return this.instanceEndDateQueryFilter;
  }
}
