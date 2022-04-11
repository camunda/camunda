/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricUserOperationLogDto implements EngineDto {
  private String id;
  private String userId;
  private OffsetDateTime timestamp;
  private String operationId;
  private String operationType;
  private String entityType;
  private String category;
  private String annotation;
  private String property;
  private String orgValue;
  private String newValue;
  private String deploymentId;
  private String processDefinitionId;
  private String processDefinitionKey;
  private String processInstanceId;
  private String executionId;
  private String caseDefinitionId;
  private String caseInstanceId;
  private String caseExecutionId;
  private String taskId;
  private String jobId;
  private String jobDefinitionId;
  private OffsetDateTime removalTime;
  private String rootProcessInstanceId;
}
