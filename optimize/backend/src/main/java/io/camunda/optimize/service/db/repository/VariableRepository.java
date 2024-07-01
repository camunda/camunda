/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueField;

import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableUpdateInstanceDto;
import io.camunda.optimize.service.db.schema.ScriptData;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface VariableRepository {

  String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  String VALUE_AGGREGATION = "values";
  String VARIABLE_VALUE_NGRAM = "nGramField";
  String VARIABLE_VALUE_LOWERCASE = "lowercaseField";

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

  List<VariableUpdateInstanceDto> getVariableInstanceUpdatesForProcessInstanceIds(
      Set<String> processInstanceIds);

  void writeExternalProcessVariables(List<ExternalProcessVariableDto> variables, String itemName);

  void deleteExternalVariablesIngestedBefore(
      OffsetDateTime timestamp, String deletedItemIdentifier);

  List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(Long ingestTimestamp, int limit);

  List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(Long ingestTimestamp);

  List<String> getDecisionVariableValues(
      DecisionVariableValueRequestDto requestDto, String variablesPath);

  default String getValueSearchField(final String variablePath, final String searchFieldName) {
    return getVariableValueField(variablePath) + "." + searchFieldName;
  }

  default String buildWildcardQuery(final String valueFilter) {
    return "*" + valueFilter + "*";
  }
}
