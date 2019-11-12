/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Getter
@Setter
public class SimpleEventDto implements Serializable, OptimizeDto {

  protected String id;
  protected String activityId;
  protected String activityType;
  protected Long durationInMs;
  protected OffsetDateTime startDate;
  protected OffsetDateTime endDate;
}
