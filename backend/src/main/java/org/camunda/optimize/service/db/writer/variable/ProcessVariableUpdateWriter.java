/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.variable;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;

import java.util.List;

public interface ProcessVariableUpdateWriter {

  String VARIABLE_UPDATES_FROM_ENGINE = "variableUpdatesFromEngine";

  List<ImportRequestDto> generateVariableUpdateImports(List<ProcessVariableDto> variables);

  void deleteVariableDataByProcessInstanceIds(final String processDefinitionKey,
                                              final List<String> processInstanceIds);

}
