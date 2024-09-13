/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.group;

import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_ASSIGNEE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_DURATION;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_END_DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_FLOW_NODES_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_NONE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_RUNNING_DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_START_DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_USER_TASKS_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_VARIABLE_TYPE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.ProcessGroupByValueDto;
import lombok.Data;

/**
 * Abstract class that contains a hidden "type" field to distinguish which group by type the jackson
 * object mapper should transform the object to.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StartDateGroupByDto.class, name = GROUP_BY_START_DATE_TYPE),
  @JsonSubTypes.Type(value = EndDateGroupByDto.class, name = GROUP_BY_END_DATE_TYPE),
  @JsonSubTypes.Type(value = RunningDateGroupByDto.class, name = GROUP_BY_RUNNING_DATE_TYPE),
  @JsonSubTypes.Type(value = FlowNodesGroupByDto.class, name = GROUP_BY_FLOW_NODES_TYPE),
  @JsonSubTypes.Type(value = UserTasksGroupByDto.class, name = GROUP_BY_USER_TASKS_TYPE),
  @JsonSubTypes.Type(value = NoneGroupByDto.class, name = GROUP_BY_NONE_TYPE),
  @JsonSubTypes.Type(value = VariableGroupByDto.class, name = GROUP_BY_VARIABLE_TYPE),
  @JsonSubTypes.Type(value = AssigneeGroupByDto.class, name = GROUP_BY_ASSIGNEE),
  @JsonSubTypes.Type(value = CandidateGroupGroupByDto.class, name = GROUP_BY_CANDIDATE_GROUP),
  @JsonSubTypes.Type(value = DurationGroupByDto.class, name = GROUP_BY_DURATION),
})
@Data
public abstract class ProcessGroupByDto<VALUE extends ProcessGroupByValueDto> {

  @JsonProperty protected ProcessGroupByType type;
  protected VALUE value;

  @Override
  public String toString() {
    return type.getId();
  }

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }
}
