/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.event.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

import java.time.OffsetDateTime;

@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Builder(toBuilder = true)
@Getter
@ToString
public class CamundaActivityEventDto {

  private String activityId;
  private String activityName;
  private String activityType;
  private String processDefinitionKey;
  private String processInstanceId;
  private String processDefinitionVersion;
  private String processDefinitionName;
  private String engine;
  private String tenantId;
  private OffsetDateTime timestamp;

}
