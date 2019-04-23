/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.optimize.dto;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;
import java.util.Date;

public class ActivityListDto implements Serializable,OptimizeDto {

  @Getter @Setter protected String processDefinitionId;
  @Getter @Setter protected Date processInstanceStartDate;
  @Getter @Setter protected Date processInstanceEndDate;
  @Getter @Setter protected String[] activityList;
}
