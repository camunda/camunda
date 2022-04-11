/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Optional;

@Data
public class HistoricActivityInstanceEngineDto implements TenantSpecificEngineDto {

  protected String id; // aka FlowNodeInstanceDto.flowNodeInstanceId
  protected String parentActivityInstanceId;
  protected String activityId; // aka FlowNodeInstanceDto.flowNodeId
  protected String activityName;
  protected String activityType; // aka FlowNodeInstanceDto.flowNodeType
  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected String executionId;
  protected String taskId; // aka FlowNodeInstanceDto.userTaskInstanceId
  protected String calledProcessInstanceId;
  protected String calledCaseInstanceId;
  protected String assignee;
  protected OffsetDateTime startTime;
  protected OffsetDateTime endTime;
  protected Long durationInMillis;
  protected Boolean canceled;
  protected Boolean completeScope;
  protected String tenantId;
  protected Long sequenceCounter;

  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }

}
