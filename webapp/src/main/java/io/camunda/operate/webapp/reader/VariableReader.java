/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.reader;

import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import java.util.List;

public interface VariableReader {
  List<VariableDto> getVariables(String processInstanceId, VariableRequestDto request);

  VariableDto getVariable(String id);

  VariableDto getVariableByName(String processInstanceId, String scopeId, String variableName);
}
