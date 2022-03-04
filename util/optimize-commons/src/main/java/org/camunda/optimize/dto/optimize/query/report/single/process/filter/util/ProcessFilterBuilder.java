/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter.util;

import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

public class ProcessFilterBuilder {

  private final List<ProcessFilterDto<?>> filters = new ArrayList<>();

  public static ProcessFilterBuilder filter() {
    return new ProcessFilterBuilder();
  }

  public ExecutedFlowNodeFilterBuilder executedFlowNodes() {
    return ExecutedFlowNodeFilterBuilder.construct(this);
  }

  public ExecutingFlowNodeFilterBuilder executingFlowNodes() {
    return ExecutingFlowNodeFilterBuilder.construct(this);
  }

  public CanceledFlowNodeFilterBuilder canceledFlowNodes() {
    return CanceledFlowNodeFilterBuilder.construct(this);
  }

  public CanceledInstancesOnlyFilterBuilder canceledInstancesOnly() {
    return CanceledInstancesOnlyFilterBuilder.construct(this);
  }

  public NonCanceledInstancesOnlyFilterBuilder nonCanceledInstancesOnly() {
    return NonCanceledInstancesOnlyFilterBuilder.construct(this);
  }

  public SuspendedInstancesOnlyFilterBuilder suspendedInstancesOnly() {
    return SuspendedInstancesOnlyFilterBuilder.construct(this);
  }

  public NonSuspendedInstancesOnlyFilterBuilder nonSuspendedInstancesOnly() {
    return NonSuspendedInstancesOnlyFilterBuilder.construct(this);
  }

  public CompletedInstancesOnlyFilterBuilder completedInstancesOnly() {
    return CompletedInstancesOnlyFilterBuilder.construct(this);
  }

  public RunningInstancesOnlyFilterBuilder runningInstancesOnly() {
    return RunningInstancesOnlyFilterBuilder.construct(this);
  }

  public DeletedIncidentFilterBuilder withDeletedIncident() {
    return DeletedIncidentFilterBuilder.construct(this);
  }

  public OpenIncidentFilterBuilder withOpenIncident() {
    return OpenIncidentFilterBuilder.construct(this);
  }

  public ResolvedIncidentFilterBuilder withResolvedIncident() {
    return ResolvedIncidentFilterBuilder.construct(this);
  }

  public NoIncidentFilterBuilder noIncidents() {
    return NoIncidentFilterBuilder.construct(this);
  }

  public RollingInstanceDateFilterBuilder rollingInstanceEndDate() {
    return RollingInstanceDateFilterBuilder.endDate(this);
  }

  public RollingInstanceDateFilterBuilder rollingInstanceStartDate() {
    return RollingInstanceDateFilterBuilder.startDate(this);
  }

  public RelativeInstanceDateFilterBuilder relativeInstanceStartDate() {
    return RelativeInstanceDateFilterBuilder.startDate(this);
  }

  public FixedInstanceDateFilterBuilder fixedInstanceEndDate() {
    return FixedInstanceDateFilterBuilder.endDate(this);
  }

  public FixedInstanceDateFilterBuilder fixedInstanceStartDate() {
    return FixedInstanceDateFilterBuilder.startDate(this);
  }

  public FixedFlowNodeDateFilterBuilder fixedFlowNodeStartDate() {
    return FixedFlowNodeDateFilterBuilder.startDate(this);
  }

  public FixedFlowNodeDateFilterBuilder fixedFlowNodeEndDate() {
    return FixedFlowNodeDateFilterBuilder.endDate(this);
  }

  public RollingFlowNodeDateFilterBuilder rollingFlowNodeStartDate() {
    return RollingFlowNodeDateFilterBuilder.startDate(this);
  }

  public RollingFlowNodeDateFilterBuilder rollingFlowNodeEndDate() {
    return RollingFlowNodeDateFilterBuilder.endDate(this);
  }

  public RelativeFlowNodeDateFilterBuilder relativeFlowNodeStartDate() {
    return RelativeFlowNodeDateFilterBuilder.startDate(this);
  }

  public RelativeFlowNodeDateFilterBuilder relativeFlowNodeEndDate() {
    return RelativeFlowNodeDateFilterBuilder.endDate(this);
  }

  public DurationFilterBuilder duration() {
    return DurationFilterBuilder.construct(this);
  }

  public MultipleVariableFilterBuilder multipleVariable() {
    return MultipleVariableFilterBuilder.construct(this);
  }

  public VariableFilterBuilder variable() {
    return VariableFilterBuilder.construct(this);
  }

  public FlowNodeDurationFilterBuilder flowNodeDuration() {
    return FlowNodeDurationFilterBuilder.construct(this);
  }

  public IdentityLinkFilterBuilder assignee() {
    return IdentityLinkFilterBuilder.constructAssigneeFilterBuilder(this);
  }

  public IdentityLinkFilterBuilder candidateGroups() {
    return IdentityLinkFilterBuilder.constructCandidateGroupFilterBuilder(this);
  }

  public RunningFlowNodesOnlyFilterBuilder runningFlowNodesOnly() {
    return RunningFlowNodesOnlyFilterBuilder.construct(this);
  }

  public CompletedFlowNodesOnlyFilterBuilder completedFlowNodesOnly() {
    return CompletedFlowNodesOnlyFilterBuilder.construct(this);
  }

  public CanceledFlowNodesOnlyFilterBuilder canceledFlowNodesOnly() {
    return CanceledFlowNodesOnlyFilterBuilder.construct(this);
  }

  public CompletedOrCanceledFlowNodesOnlyFilterBuilder completedOrCanceledFlowNodesOnly() {
    return CompletedOrCanceledFlowNodesOnlyFilterBuilder.construct(this);
  }

  public InstancesContainingUserTasksFilterBuilder userTaskFlowNodesOnly() {
    return InstancesContainingUserTasksFilterBuilder.construct(this);
  }

  void addFilter(ProcessFilterDto<?> result) {
    filters.add(result);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public <T extends ProcessFilterDto<?>> List<T> buildList() {
    return (List<T>) filters;
  }
}
