/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
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
import io.camunda.optimize.service.db.es.filter.util.IncidentFilterQueryUtilES;
import io.camunda.optimize.service.db.es.filter.util.ModelElementFilterQueryUtilES;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessQueryFilterEnhancerES implements QueryFilterEnhancerES<ProcessFilterDto<?>> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessQueryFilterEnhancerES.class);
  private final ConfigurationService configurationService;
  private final Environment environment;
  private final InstanceStartDateQueryFilterES instanceStartDateQueryFilter;
  private final InstanceEndDateQueryFilterES instanceEndDateQueryFilter;
  private final ProcessVariableQueryFilterES variableQueryFilter;
  private final ProcessMultiVariableQueryFilterES multiVariableQueryFilter;
  private final ExecutedFlowNodeQueryFilterES executedFlowNodeQueryFilter;
  private final ExecutingFlowNodeQueryFilterES executingFlowNodeQueryFilter;
  private final CanceledFlowNodeQueryFilterES canceledFlowNodeQueryFilter;
  private final DurationQueryFilterES durationQueryFilter;
  private final RunningInstancesOnlyQueryFilterES runningInstancesOnlyQueryFilter;
  private final CompletedInstancesOnlyQueryFilterES completedInstancesOnlyQueryFilter;
  private final CanceledInstancesOnlyQueryFilterES canceledInstancesOnlyQueryFilter;
  private final NonCanceledInstancesOnlyQueryFilterES nonCanceledInstancesOnlyQueryFilter;
  private final SuspendedInstancesOnlyQueryFilterES suspendedInstancesOnlyQueryFilter;
  private final NonSuspendedInstancesOnlyQueryFilterES nonSuspendedInstancesOnlyQueryFilter;
  private final FlowNodeDurationQueryFilterES flowNodeDurationQueryFilter;
  private final AssigneeQueryFilterES assigneeQueryFilter;
  private final CandidateGroupQueryFilterES candidateGroupQueryFilter;
  private final OpenIncidentQueryFilterES openIncidentQueryFilter;
  private final DeletedIncidentQueryFilterES deletedIncidentQueryFilter;
  private final ResolvedIncidentQueryFilterES resolvedIncidentQueryFilter;
  private final NoIncidentQueryFilterES noIncidentQueryFilter;
  private final RunningFlowNodesOnlyQueryFilterES runningFlowNodesOnlyQueryFilter;
  private final CompletedFlowNodesOnlyQueryFilterES completedFlowNodesOnlyQueryFilter;
  private final CanceledFlowNodesOnlyQueryFilterES canceledFlowNodesOnlyQueryFilter;
  private final CompletedOrCanceledFlowNodesOnlyQueryFilterES
      completedOrCanceledFlowNodesOnlyQueryFilter;
  private final InstancesContainingUserTasksFilterES instancesContainingUserTasksFilter;
  private final FlowNodeStartDateQueryFilterES flowNodeStartDateQueryFilter;
  private final FlowNodeEndDateQueryFilterES flowNodeEndDateQueryFilter;

  public ProcessQueryFilterEnhancerES(
      final ConfigurationService configurationService,
      final Environment environment,
      final InstanceStartDateQueryFilterES instanceStartDateQueryFilter,
      final InstanceEndDateQueryFilterES instanceEndDateQueryFilter,
      final ProcessVariableQueryFilterES variableQueryFilter,
      final ProcessMultiVariableQueryFilterES multiVariableQueryFilter,
      final ExecutedFlowNodeQueryFilterES executedFlowNodeQueryFilter,
      final ExecutingFlowNodeQueryFilterES executingFlowNodeQueryFilter,
      final CanceledFlowNodeQueryFilterES canceledFlowNodeQueryFilter,
      final DurationQueryFilterES durationQueryFilter,
      final RunningInstancesOnlyQueryFilterES runningInstancesOnlyQueryFilter,
      final CompletedInstancesOnlyQueryFilterES completedInstancesOnlyQueryFilter,
      final CanceledInstancesOnlyQueryFilterES canceledInstancesOnlyQueryFilter,
      final NonCanceledInstancesOnlyQueryFilterES nonCanceledInstancesOnlyQueryFilter,
      final SuspendedInstancesOnlyQueryFilterES suspendedInstancesOnlyQueryFilter,
      final NonSuspendedInstancesOnlyQueryFilterES nonSuspendedInstancesOnlyQueryFilter,
      final FlowNodeDurationQueryFilterES flowNodeDurationQueryFilter,
      final AssigneeQueryFilterES assigneeQueryFilter,
      final CandidateGroupQueryFilterES candidateGroupQueryFilter,
      final OpenIncidentQueryFilterES openIncidentQueryFilter,
      final DeletedIncidentQueryFilterES deletedIncidentQueryFilter,
      final ResolvedIncidentQueryFilterES resolvedIncidentQueryFilter,
      final NoIncidentQueryFilterES noIncidentQueryFilter,
      final RunningFlowNodesOnlyQueryFilterES runningFlowNodesOnlyQueryFilter,
      final CompletedFlowNodesOnlyQueryFilterES completedFlowNodesOnlyQueryFilter,
      final CanceledFlowNodesOnlyQueryFilterES canceledFlowNodesOnlyQueryFilter,
      final CompletedOrCanceledFlowNodesOnlyQueryFilterES
          completedOrCanceledFlowNodesOnlyQueryFilter,
      final InstancesContainingUserTasksFilterES instancesContainingUserTasksFilter,
      final FlowNodeStartDateQueryFilterES flowNodeStartDateQueryFilter,
      final FlowNodeEndDateQueryFilterES flowNodeEndDateQueryFilter) {
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
  public void addFilterToQuery(
      final BoolQuery.Builder query,
      final List<ProcessFilterDto<?>> filters,
      final FilterContext filterContext) {
    if (!CollectionUtils.isEmpty(filters)) {
      instanceStartDateQueryFilter.addFilters(
          query, extractInstanceFilters(filters, InstanceStartDateFilterDto.class), filterContext);
      instanceEndDateQueryFilter.addFilters(
          query, extractInstanceFilters(filters, InstanceEndDateFilterDto.class), filterContext);
      variableQueryFilter.addFilters(
          query, extractInstanceFilters(filters, VariableFilterDto.class), filterContext);
      multiVariableQueryFilter.addFilters(
          query, extractInstanceFilters(filters, MultipleVariableFilterDto.class), filterContext);
      executedFlowNodeQueryFilter.addFilters(
          query, extractInstanceFilters(filters, ExecutedFlowNodeFilterDto.class), filterContext);
      executingFlowNodeQueryFilter.addFilters(
          query, extractInstanceFilters(filters, ExecutingFlowNodeFilterDto.class), filterContext);
      canceledFlowNodeQueryFilter.addFilters(
          query, extractInstanceFilters(filters, CanceledFlowNodeFilterDto.class), filterContext);
      durationQueryFilter.addFilters(
          query, extractInstanceFilters(filters, DurationFilterDto.class), filterContext);
      runningInstancesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, RunningInstancesOnlyFilterDto.class),
          filterContext);
      completedInstancesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, CompletedInstancesOnlyFilterDto.class),
          filterContext);
      canceledInstancesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, CanceledInstancesOnlyFilterDto.class),
          filterContext);
      nonCanceledInstancesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, NonCanceledInstancesOnlyFilterDto.class),
          filterContext);
      suspendedInstancesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, SuspendedInstancesOnlyFilterDto.class),
          filterContext);
      nonSuspendedInstancesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, NonSuspendedInstancesOnlyFilterDto.class),
          filterContext);
      flowNodeDurationQueryFilter.addFilters(
          query, extractInstanceFilters(filters, FlowNodeDurationFilterDto.class), filterContext);
      if (isAssigneeFiltersEnabled()) {
        assigneeQueryFilter.addFilters(
            query, extractInstanceFilters(filters, AssigneeFilterDto.class), filterContext);
      }
      candidateGroupQueryFilter.addFilters(
          query, extractInstanceFilters(filters, CandidateGroupFilterDto.class), filterContext);
      openIncidentQueryFilter.addFilters(
          query, extractInstanceFilters(filters, OpenIncidentFilterDto.class), filterContext);
      deletedIncidentQueryFilter.addFilters(
          query, extractInstanceFilters(filters, DeletedIncidentFilterDto.class), filterContext);
      resolvedIncidentQueryFilter.addFilters(
          query, extractInstanceFilters(filters, ResolvedIncidentFilterDto.class), filterContext);
      noIncidentQueryFilter.addFilters(
          query, extractInstanceFilters(filters, NoIncidentFilterDto.class), filterContext);
      runningFlowNodesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, RunningFlowNodesOnlyFilterDto.class),
          filterContext);
      completedFlowNodesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, CompletedFlowNodesOnlyFilterDto.class),
          filterContext);
      canceledFlowNodesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, CanceledFlowNodesOnlyFilterDto.class),
          filterContext);
      completedOrCanceledFlowNodesOnlyQueryFilter.addFilters(
          query,
          extractInstanceFilters(filters, CompletedOrCanceledFlowNodesOnlyFilterDto.class),
          filterContext);
      instancesContainingUserTasksFilter.addFilters(
          query,
          extractInstanceFilters(filters, UserTaskFlowNodesOnlyFilterDto.class),
          filterContext);
      flowNodeStartDateQueryFilter.addFilters(
          query, extractInstanceFilters(filters, FlowNodeStartDateFilterDto.class), filterContext);
      flowNodeEndDateQueryFilter.addFilters(
          query, extractInstanceFilters(filters, FlowNodeEndDateFilterDto.class), filterContext);
    }
    addInstanceFilterForViewLevelMatching(query, filters, filterContext);
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

  private void addInstanceFilterForViewLevelMatching(
      final BoolQuery.Builder query,
      final List<ProcessFilterDto<?>> filters,
      final FilterContext filterContext) {
    ModelElementFilterQueryUtilES.addInstanceFilterForRelevantViewLevelFilters(
            filters, filterContext)
        .ifPresent(n -> query.filter(f -> f.nested(n.build())));
    IncidentFilterQueryUtilES.addInstanceFilterForRelevantViewLevelFilters(filters)
        .ifPresent(n -> query.filter(f -> f.nested(n.build())));
  }

  private boolean isAssigneeFiltersEnabled() {
    return configurationService.getUiConfiguration().isUserTaskAssigneeAnalyticsEnabled();
  }

  public InstanceStartDateQueryFilterES getInstanceStartDateQueryFilter() {
    return this.instanceStartDateQueryFilter;
  }

  public InstanceEndDateQueryFilterES getInstanceEndDateQueryFilter() {
    return this.instanceEndDateQueryFilter;
  }
}
