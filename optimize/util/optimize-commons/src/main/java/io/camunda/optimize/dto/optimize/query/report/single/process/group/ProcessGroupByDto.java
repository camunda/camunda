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
import io.camunda.optimize.dto.optimize.query.report.Combinable;
import io.camunda.optimize.dto.optimize.query.report.single.process.group.value.ProcessGroupByValueDto;
import java.util.Objects;

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
public abstract class ProcessGroupByDto<VALUE extends ProcessGroupByValueDto>
    implements Combinable {

  @JsonProperty protected ProcessGroupByType type;
  protected VALUE value;

  public ProcessGroupByDto() {}

  @Override
  public boolean isCombinable(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessGroupByDto)) {
      return false;
    }
    final ProcessGroupByDto<?> that = (ProcessGroupByDto<?>) o;
    return isTypeCombinable(that) && Combinable.isCombinable(value, that.value);
  }

  protected boolean isTypeCombinable(final ProcessGroupByDto<?> that) {
    return Objects.equals(type, that.type);
  }

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }

  public ProcessGroupByType getType() {
    return type;
  }

  @JsonProperty
  public void setType(final ProcessGroupByType type) {
    this.type = type;
  }

  public VALUE getValue() {
    return value;
  }

  public void setValue(final VALUE value) {
    this.value = value;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessGroupByDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    final Object $value = getValue();
    result = result * PRIME + ($value == null ? 43 : $value.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ProcessGroupByDto)) {
      return false;
    }
    final ProcessGroupByDto<?> other = (ProcessGroupByDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    final Object this$value = getValue();
    final Object other$value = other.getValue();
    if (this$value == null ? other$value != null : !this$value.equals(other$value)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return type.getId();
  }
}
