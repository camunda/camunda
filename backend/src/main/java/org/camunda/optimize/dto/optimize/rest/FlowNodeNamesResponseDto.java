/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class FlowNodeNamesResponseDto {
  private Map<String, String> flowNodeNames = new HashMap<>();
}
