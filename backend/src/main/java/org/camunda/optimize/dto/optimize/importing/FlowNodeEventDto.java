/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;


@Data
public class FlowNodeEventDto implements Serializable,OptimizeDto {

  protected String id;
  protected String activityId;
  protected String activityInstanceId;
  protected OffsetDateTime timestamp;
  protected String processDefinitionKey;
  protected String processDefinitionId;
  protected String processInstanceId;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
  protected Long durationInMs;
  protected String activityType;
  protected String engineAlias;
}
