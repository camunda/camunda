/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
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
}
