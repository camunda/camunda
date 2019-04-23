/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.value.ProcessGroupByValueDto;
import org.camunda.optimize.service.es.report.command.util.ReportUtil;

import java.util.Objects;

import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_FLOW_NODES_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_NONE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_START_DATE_TYPE;
import static org.camunda.optimize.dto.optimize.ReportConstants.GROUP_BY_VARIABLE_TYPE;


/**
 * Abstract class that contains a hidden "type" field to distinguish, which
 * group by type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StartDateGroupByDto.class, name = GROUP_BY_START_DATE_TYPE),
    @JsonSubTypes.Type(value = FlowNodesGroupByDto.class, name = GROUP_BY_FLOW_NODES_TYPE),
    @JsonSubTypes.Type(value = NoneGroupByDto.class, name = GROUP_BY_NONE_TYPE),
    @JsonSubTypes.Type(value = VariableGroupByDto.class, name = GROUP_BY_VARIABLE_TYPE)
}
)
@Getter
@Setter
public abstract class ProcessGroupByDto<VALUE extends ProcessGroupByValueDto> implements Combinable {

  @JsonProperty
  protected ProcessGroupByType type;
  protected VALUE value;

  @Override
  public String toString() {
    return type.getId();
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ProcessGroupByDto)) {
      return false;
    }
    ProcessGroupByDto<?> that = (ProcessGroupByDto<?>) o;
    return Objects.equals(type, that.type) &&
      ReportUtil.isCombinable(value, that.value);
  }

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }


}
