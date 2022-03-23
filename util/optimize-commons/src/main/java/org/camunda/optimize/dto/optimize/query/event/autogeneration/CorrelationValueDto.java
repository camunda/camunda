/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.autogeneration;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Data
public class CorrelationValueDto {
  private String businessKey;
  private List<SimpleProcessVariableDto> variables = new ArrayList<>();
}
