/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.engine;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;
import lombok.Data;

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
