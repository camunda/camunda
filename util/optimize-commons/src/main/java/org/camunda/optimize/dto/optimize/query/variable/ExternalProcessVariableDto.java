/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.Data;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.OptimizeDto;

@Data
@Accessors(chain = true)
public class ExternalProcessVariableDto implements OptimizeDto {
  private String variableId;
  private String variableName;
  private String variableValue;
  private Long ingestionTimestamp;
  private String processInstanceId;
  private String processDefinitionKey;
}
