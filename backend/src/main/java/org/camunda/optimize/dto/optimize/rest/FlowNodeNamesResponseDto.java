/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class FlowNodeNamesResponseDto {
  private Map<String, String> flowNodeNames = new HashMap<>();
}
