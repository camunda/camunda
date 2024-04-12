/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository;

import java.util.List;
import java.util.Map;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.service.db.schema.ScriptData;

public interface VariableRepository {
  void deleteVariableDataByProcessInstanceIds(
      String processDefinitionKey, List<String> processInstanceIds);

  void upsertVariableLabel(
      String variableLabelIndexName,
      DefinitionVariableLabelsDto definitionVariableLabelsDto,
      ScriptData scriptData);

  void deleteVariablesForDefinition(String variableLabelIndexName, String processDefinitionKey);

  void deleteByProcessInstanceIds(List<String> processInstanceIds);

  Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      List<String> processDefinitionKeys);
}
