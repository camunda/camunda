/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.time.OffsetDateTime;

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
