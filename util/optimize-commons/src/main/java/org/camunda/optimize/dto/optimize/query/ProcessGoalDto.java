/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@Builder(toBuilder = true)
@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
public class ProcessGoalDto {
  private String processDefinitionKey;
  private String processName;
  private List<String> timeGoals;
  private String owner;
}
