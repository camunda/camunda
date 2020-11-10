/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.client.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class MessageCorrelationDto {

  private String messageName;
  private boolean all;
  Map<String, VariableValueDto> processVariables = new HashMap<>();

}
