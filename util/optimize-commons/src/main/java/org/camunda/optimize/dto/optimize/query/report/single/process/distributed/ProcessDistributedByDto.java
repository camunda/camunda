/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.ProcessDistributedByValueDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_ASSIGNEE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_CANDIDATE_GROUP;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_FLOW_NODE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_NONE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_USER_TASK;

/**
 * Abstract class that contains a hidden "type" field to distinguish which
 * distributed by type the jackson object mapper should transform the object to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NoneDistributedByDto.class, name = DISTRIBUTED_BY_NONE),
  @JsonSubTypes.Type(value = UserTaskDistributedByDto.class, name = DISTRIBUTED_BY_USER_TASK),
  @JsonSubTypes.Type(value = FlowNodeDistributedByDto.class, name = DISTRIBUTED_BY_FLOW_NODE),
  @JsonSubTypes.Type(value = AssigneeDistributedByDto.class, name = DISTRIBUTED_BY_ASSIGNEE),
  @JsonSubTypes.Type(value = CandidateGroupDistributedByDto.class, name = DISTRIBUTED_BY_CANDIDATE_GROUP)
})
@Data
public class ProcessDistributedByDto<VALUE extends ProcessDistributedByValueDto> implements Combinable {

  @JsonProperty
  protected DistributedByType type = DistributedByType.NONE;
  protected VALUE value;

  @Override
  public String toString() {
    return type.getId();
  }

  @JsonIgnore
  public String createCommandKey() {
    return type.getId();
  }

  @Override
  public boolean isCombinable(final Object o) {
    return DistributedByType.NONE.equals(type)
      && DistributedByType.NONE.equals(o);
  }
}
