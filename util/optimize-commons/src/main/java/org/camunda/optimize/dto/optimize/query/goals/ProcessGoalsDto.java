/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.goals;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.List;

@Data
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
public class ProcessGoalsDto implements OptimizeDto {

  private String processDefinitionKey;
  private String owner;
  private List<ProcessDurationGoalDto> durationGoals;

}
