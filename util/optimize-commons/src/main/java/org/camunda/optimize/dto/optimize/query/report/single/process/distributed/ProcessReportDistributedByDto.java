/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.process.distributed;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.report.Combinable;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.value.ProcessReportDistributedByValueDto;

import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_ASSIGNEE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_CANDIDATE_GROUP;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_END_DATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_FLOW_NODE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_NONE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_PROCESS;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_START_DATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_USER_TASK;
import static org.camunda.optimize.dto.optimize.ReportConstants.DISTRIBUTED_BY_VARIABLE;

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
  @JsonSubTypes.Type(value = CandidateGroupDistributedByDto.class, name = DISTRIBUTED_BY_CANDIDATE_GROUP),
  @JsonSubTypes.Type(value = VariableDistributedByDto.class, name = DISTRIBUTED_BY_VARIABLE),
  @JsonSubTypes.Type(value = StartDateDistributedByDto.class, name = DISTRIBUTED_BY_START_DATE),
  @JsonSubTypes.Type(value = EndDateDistributedByDto.class, name = DISTRIBUTED_BY_END_DATE),
  @JsonSubTypes.Type(value = ProcessDistributedByDto.class, name = DISTRIBUTED_BY_PROCESS)
})
@Data
@FieldNameConstants
public class ProcessReportDistributedByDto<VALUE extends ProcessReportDistributedByValueDto> implements Combinable {

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
      && DistributedByType.NONE.equals(((ProcessReportDistributedByDto<?>) o).getType());
  }
}
