/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterDataDto;

/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * filter type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StartDateFilterDto.class, name = "startDate"),
  @JsonSubTypes.Type(value = EndDateFilterDto.class, name = "endDate"),
  @JsonSubTypes.Type(value = DurationFilterDto.class, name = "processInstanceDuration"),
  @JsonSubTypes.Type(value = VariableFilterDto.class, name = "variable"),
  @JsonSubTypes.Type(value = ExecutedFlowNodeFilterDto.class, name = "executedFlowNodes"),
  @JsonSubTypes.Type(value = ExecutingFlowNodeFilterDto.class, name = "executingFlowNodes"),
  @JsonSubTypes.Type(value = CanceledFlowNodeFilterDto.class, name = "canceledFlowNodes"),
  @JsonSubTypes.Type(value = RunningInstancesOnlyFilterDto.class, name = "runningInstancesOnly"),
  @JsonSubTypes.Type(value = CompletedInstancesOnlyFilterDto.class, name = "completedInstancesOnly"),
  @JsonSubTypes.Type(value = CanceledInstancesOnlyFilterDto.class, name = "canceledInstancesOnly"),
  @JsonSubTypes.Type(value = NonCanceledInstancesOnlyFilterDto.class, name = "nonCanceledInstancesOnly"),
  @JsonSubTypes.Type(value = SuspendedInstancesOnlyFilterDto.class, name = "suspendedInstancesOnly"),
  @JsonSubTypes.Type(value = NonSuspendedInstancesOnlyFilterDto.class, name = "nonSuspendedInstancesOnly"),
  @JsonSubTypes.Type(value = FlowNodeDurationFilterDto.class, name = "flowNodeDuration"),
  @JsonSubTypes.Type(value = AssigneeFilterDto.class, name = "assignee"),
  @JsonSubTypes.Type(value = CandidateGroupFilterDto.class, name = "candidateGroup"),
  @JsonSubTypes.Type(value = WithOpenIncidentsOnlyFilterDto.class, name = "withOpenIncidentsOnly"),
  @JsonSubTypes.Type(value = WithResolvedIncidentsOnlyFilterDto.class, name = "withResolvedIncidentsOnly"),
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class ProcessFilterDto<DATA extends FilterDataDto> {
  protected DATA data;

  @Override
  public String toString() {
    return "ProcessFilter=" + getClass().getSimpleName();
  }
}
