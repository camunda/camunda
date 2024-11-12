/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.ReportConstants;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Abstract class that contains a hidden "type" field to distinguish, which filter type the jackson
 * object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = InstanceStartDateFilterDto.class, name = "instanceStartDate"),
  @JsonSubTypes.Type(value = FlowNodeStartDateFilterDto.class, name = "flowNodeStartDate"),
  @JsonSubTypes.Type(value = FlowNodeEndDateFilterDto.class, name = "flowNodeEndDate"),
  @JsonSubTypes.Type(value = InstanceEndDateFilterDto.class, name = "instanceEndDate"),
  @JsonSubTypes.Type(value = DurationFilterDto.class, name = "processInstanceDuration"),
  @JsonSubTypes.Type(value = VariableFilterDto.class, name = "variable"),
  @JsonSubTypes.Type(value = MultipleVariableFilterDto.class, name = "multipleVariable"),
  @JsonSubTypes.Type(value = ExecutedFlowNodeFilterDto.class, name = "executedFlowNodes"),
  @JsonSubTypes.Type(value = ExecutingFlowNodeFilterDto.class, name = "executingFlowNodes"),
  @JsonSubTypes.Type(value = CanceledFlowNodeFilterDto.class, name = "canceledFlowNodes"),
  @JsonSubTypes.Type(value = RunningInstancesOnlyFilterDto.class, name = "runningInstancesOnly"),
  @JsonSubTypes.Type(
      value = CompletedInstancesOnlyFilterDto.class,
      name = "completedInstancesOnly"),
  @JsonSubTypes.Type(value = CanceledInstancesOnlyFilterDto.class, name = "canceledInstancesOnly"),
  @JsonSubTypes.Type(
      value = NonCanceledInstancesOnlyFilterDto.class,
      name = "nonCanceledInstancesOnly"),
  @JsonSubTypes.Type(
      value = SuspendedInstancesOnlyFilterDto.class,
      name = "suspendedInstancesOnly"),
  @JsonSubTypes.Type(
      value = NonSuspendedInstancesOnlyFilterDto.class,
      name = "nonSuspendedInstancesOnly"),
  @JsonSubTypes.Type(value = FlowNodeDurationFilterDto.class, name = "flowNodeDuration"),
  @JsonSubTypes.Type(value = AssigneeFilterDto.class, name = "assignee"),
  @JsonSubTypes.Type(value = CandidateGroupFilterDto.class, name = "candidateGroup"),
  @JsonSubTypes.Type(value = OpenIncidentFilterDto.class, name = "includesOpenIncident"),
  @JsonSubTypes.Type(value = DeletedIncidentFilterDto.class, name = "includesClosedIncident"),
  @JsonSubTypes.Type(value = ResolvedIncidentFilterDto.class, name = "includesResolvedIncident"),
  @JsonSubTypes.Type(value = NoIncidentFilterDto.class, name = "doesNotIncludeIncident"),
  @JsonSubTypes.Type(value = RunningFlowNodesOnlyFilterDto.class, name = "runningFlowNodesOnly"),
  @JsonSubTypes.Type(
      value = CompletedFlowNodesOnlyFilterDto.class,
      name = "completedFlowNodesOnly"),
  @JsonSubTypes.Type(value = CanceledFlowNodesOnlyFilterDto.class, name = "canceledFlowNodesOnly"),
  @JsonSubTypes.Type(
      value = CompletedOrCanceledFlowNodesOnlyFilterDto.class,
      name = "completedOrCanceledFlowNodesOnly")
})
public abstract class ProcessFilterDto<DATA extends FilterDataDto> {

  protected DATA data;
  @NotNull protected FilterApplicationLevel filterLevel;

  @NotEmpty protected List<String> appliedTo = List.of(ReportConstants.APPLIED_TO_ALL_DEFINITIONS);

  protected ProcessFilterDto(final DATA data, final FilterApplicationLevel filterLevel) {
    this.data = data;
    setFilterLevel(filterLevel);
  }

  public ProcessFilterDto() {}

  public abstract List<FilterApplicationLevel> validApplicationLevels();

  public DATA getData() {
    return data;
  }

  public void setData(final DATA data) {
    this.data = data;
  }

  public @NotNull FilterApplicationLevel getFilterLevel() {
    return filterLevel;
  }

  public void setFilterLevel(@NotNull final FilterApplicationLevel filterLevel) {
    this.filterLevel = filterLevel;
  }

  public @NotEmpty List<String> getAppliedTo() {
    return appliedTo;
  }

  public void setAppliedTo(@NotEmpty final List<String> appliedTo) {
    this.appliedTo = appliedTo;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessFilterDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "ProcessFilter=" + getClass().getSimpleName();
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String data = "data";
    public static final String filterLevel = "filterLevel";
    public static final String appliedTo = "appliedTo";
  }
}
