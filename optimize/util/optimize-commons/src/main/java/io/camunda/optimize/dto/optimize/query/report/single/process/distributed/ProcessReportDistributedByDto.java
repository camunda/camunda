/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_ASSIGNEE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_CANDIDATE_GROUP;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_END_DATE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_FLOW_NODE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_NONE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_PROCESS;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_START_DATE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_USER_TASK;
import static io.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_VARIABLE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.optimize.dto.optimize.query.report.Combinable;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import io.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.ProcessReportDistributedByValueDto;

/**
 * Abstract class that contains a hidden "type" field to distinguish which distributed by type the
 * jackson object mapper should transform the object to.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NoneDistributedByDto.class, name = DISTRIBUTED_BY_NONE),
  @JsonSubTypes.Type(value = UserTaskDistributedByDto.class, name = DISTRIBUTED_BY_USER_TASK),
  @JsonSubTypes.Type(value = FlowNodeDistributedByDto.class, name = DISTRIBUTED_BY_FLOW_NODE),
  @JsonSubTypes.Type(value = AssigneeDistributedByDto.class, name = DISTRIBUTED_BY_ASSIGNEE),
  @JsonSubTypes.Type(
      value = CandidateGroupDistributedByDto.class,
      name = DISTRIBUTED_BY_CANDIDATE_GROUP),
  @JsonSubTypes.Type(value = VariableDistributedByDto.class, name = DISTRIBUTED_BY_VARIABLE),
  @JsonSubTypes.Type(value = StartDateDistributedByDto.class, name = DISTRIBUTED_BY_START_DATE),
  @JsonSubTypes.Type(value = EndDateDistributedByDto.class, name = DISTRIBUTED_BY_END_DATE),
  @JsonSubTypes.Type(value = ProcessDistributedByDto.class, name = DISTRIBUTED_BY_PROCESS)
})
public class ProcessReportDistributedByDto<VALUE extends ProcessReportDistributedByValueDto>
    implements Combinable {

  @JsonProperty protected DistributedByType type = DistributedByType.NONE;
  protected VALUE value;

  public ProcessReportDistributedByDto() {}

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }

  @Override
  public boolean isCombinable(final Object o) {
    return DistributedByType.NONE.equals(type)
        && DistributedByType.NONE.equals(((ProcessReportDistributedByDto<?>) o).getType());
  }

  public DistributedByType getType() {
    return type;
  }

  @JsonProperty
  public void setType(final DistributedByType type) {
    this.type = type;
  }

  public VALUE getValue() {
    return value;
  }

  public void setValue(final VALUE value) {
    this.value = value;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ProcessReportDistributedByDto;
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
    if (!(o instanceof ProcessReportDistributedByDto)) {
      return false;
    }
    final ProcessReportDistributedByDto<?> other = (ProcessReportDistributedByDto<?>) o;
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

  public static final class Fields {

    public static final String type = "type";
    public static final String value = "value";
  }
}
