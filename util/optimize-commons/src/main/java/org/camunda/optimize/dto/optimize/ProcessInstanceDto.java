/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.importing.EngineConstants.FLOW_NODE_TYPE_USER_TASK;

@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@FieldNameConstants
public class ProcessInstanceDto implements OptimizeDto {

  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String processDefinitionId;
  private String processInstanceId;
  private String businessKey;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long duration; // duration in ms
  private String state;
  @Builder.Default
  private List<FlowNodeInstanceDto> flowNodeInstances = new ArrayList<>();
  @Builder.Default
  private List<SimpleProcessVariableDto> variables = new ArrayList<>();
  @Builder.Default
  private List<IncidentDto> incidents = new ArrayList<>();
  private DataSourceDto dataSource;
  private String tenantId;

  @JsonIgnore
  public List<FlowNodeInstanceDto> getUserTasks() {
    return flowNodeInstances.stream()
      .filter(flowNode -> FLOW_NODE_TYPE_USER_TASK.equalsIgnoreCase(flowNode.getFlowNodeType()))
      .collect(toList());
  }
}



