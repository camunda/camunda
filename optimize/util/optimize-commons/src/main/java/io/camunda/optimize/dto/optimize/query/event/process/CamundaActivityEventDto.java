/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants
@Builder(toBuilder = true)
@Getter
@ToString
public class CamundaActivityEventDto implements OptimizeDto, EventProcessEventDto {

  private String activityId;
  private String activityName;
  private String activityType;
  private String activityInstanceId;
  private String processDefinitionKey;
  private String processInstanceId;
  private String processDefinitionVersion;
  private String processDefinitionName;
  private String engine;
  private String tenantId;
  private OffsetDateTime timestamp;
  private Long orderCounter;
  private boolean canceled;
}
