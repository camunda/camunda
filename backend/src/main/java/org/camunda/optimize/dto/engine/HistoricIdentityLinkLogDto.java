/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;


@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class HistoricIdentityLinkLogDto implements EngineDto {
  
  private String id;
  private OffsetDateTime time;
  private String type;
  private String userId;
  private String groupId;
  private String taskId; // equivalent to FlowNodeInstanceDto.userTaskInstanceId
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processInstanceId;
  private String operationType;
  private String assignerId;
  private String tenantId;
  private OffsetDateTime removalTime;
  private String rootProcessInstanceId;
}
