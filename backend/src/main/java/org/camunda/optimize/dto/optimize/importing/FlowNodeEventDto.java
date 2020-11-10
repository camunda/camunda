/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class FlowNodeEventDto implements Serializable, OptimizeDto {
  private String id;
  private String activityId;
  private String activityName;
  private OffsetDateTime timestamp;
  private String processDefinitionKey;
  private String processDefinitionId;
  private String processInstanceId;
  private OffsetDateTime startDate;
  private OffsetDateTime endDate;
  private Long durationInMs;
  private String activityType;
  private String engineAlias;
  private String tenantId;
  private Long orderCounter;
  private Boolean canceled;
  private String taskId;
}
