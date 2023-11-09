/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.writer.variable;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;

import java.util.List;

public interface VariableUpdateInstanceWriter {

  List<ImportRequestDto> generateVariableUpdateImports(final List<ProcessVariableDto> variableUpdates);

  void deleteByProcessInstanceIds(final List<String> processInstanceIds);

}
