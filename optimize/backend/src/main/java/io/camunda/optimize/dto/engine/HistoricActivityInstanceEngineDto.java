/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.Data;

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

  @Override
  public Optional<String> getTenantId() {
    return Optional.ofNullable(tenantId);
  }
}
