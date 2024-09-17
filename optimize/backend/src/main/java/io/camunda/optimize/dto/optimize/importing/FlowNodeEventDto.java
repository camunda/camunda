/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.importing;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class FlowNodeEventDto implements Serializable, OptimizeDto {

  private String id; // == FlowNodeInstanceDto.flowNodeInstanceId
  private String activityId; // == FlowNodeInstanceDto.flowNodeID
  private String activityType;
  private String activityName;
  private OffsetDateTime timestamp;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processDefinitionVersion;
  private String tenantId;
  private String engineAlias;
  private String processInstanceId;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long durationInMs;
  private Long orderCounter;
  private Boolean canceled;
  private String taskId; // == FlowNodeInstanceDto.userTaskId (null if flowNode is not a userTask)

  public FlowNodeEventDto(
      String id,
      String activityId,
      String activityType,
      String activityName,
      OffsetDateTime timestamp,
      String processDefinitionId,
      String processDefinitionKey,
      String processDefinitionVersion,
      String tenantId,
      String engineAlias,
      String processInstanceId,
      OffsetDateTime startDate,
      OffsetDateTime endDate,
      Long durationInMs,
      Long orderCounter,
      Boolean canceled,
      String taskId) {
    this.id = id;
    this.activityId = activityId;
    this.activityType = activityType;
    this.activityName = activityName;
    this.timestamp = timestamp;
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
    this.tenantId = tenantId;
    this.engineAlias = engineAlias;
    this.processInstanceId = processInstanceId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.durationInMs = durationInMs;
    this.orderCounter = orderCounter;
    this.canceled = canceled;
    this.taskId = taskId;
  }

  public FlowNodeEventDto() {}
}
