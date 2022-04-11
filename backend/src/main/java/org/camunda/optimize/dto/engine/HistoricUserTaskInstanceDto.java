/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class HistoricUserTaskInstanceDto implements TenantSpecificEngineDto {
  private String id;  // == FlowNodeInstanceDto.userTaskId
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private String executionId;
  private String caseDefinitionKey;
  private String caseDefinitionId;
  private String caseInstanceId;
  private String caseExecutionId;
  private String activityInstanceId; // == FlowNodeInstanceDto.flowNodeInstanceId
  private String name;
  private String description;
  private String deleteReason;
  private String owner;
  private String assignee;
  private OffsetDateTime startTime;
  private OffsetDateTime endTime;
  private Long duration;
  private String taskDefinitionKey; // == FlowNodeInstanceDto.flowNodeId
  private int priority;
  private OffsetDateTime due;
  private String parentTaskId;
  private OffsetDateTime followUp;
  private String tenantId;
  private OffsetDateTime removalTime;
  private String rootProcessInstanceId;

  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}
