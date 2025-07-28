/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository;

import static io.camunda.optimize.service.util.DecisionVariableHelper.getVariableValueField;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ExternalProcessVariableDto;
import io.camunda.optimize.dto.optimize.query.variable.LabelDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableSourceDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public interface VariableRepository {

  String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  String VALUE_AGGREGATION = "values";
  String VARIABLE_VALUE_NGRAM = "nGramField";
  String VARIABLE_VALUE_LOWERCASE = "lowercaseField";

  String NAME_AGGREGATION = "variableNameAggregation";
  String TYPE_AGGREGATION = "variableTypeAggregation";
  String VAR_NAME_AND_TYPE_COMPOSITE_AGG = "varNameAndTypeCompositeAgg";
  String INDEX_AGGREGATION = "_index";
  String PROCESS_INSTANCE_INDEX_NAME_SUBSECTION =
      "-" + DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

  void deleteVariableDataByProcessInstanceIds(
      String processDefinitionKey, List<String> processInstanceIds);

  void upsertVariableLabel(
      String variableLabelIndexName,
      DefinitionVariableLabelsDto definitionVariableLabelsDto,
      ScriptData scriptData);

  void deleteVariablesForDefinition(String variableLabelIndexName, String processDefinitionKey);

  Map<String, DefinitionVariableLabelsDto> getVariableLabelsByKey(
      List<String> processDefinitionKeys);

  void writeExternalProcessVariables(List<ExternalProcessVariableDto> variables, String itemName);

  void deleteExternalVariablesIngestedBefore(
      OffsetDateTime timestamp, String deletedItemIdentifier);

  List<ExternalProcessVariableDto> getVariableUpdatesIngestedAfter(Long ingestTimestamp, int limit);

  List<ExternalProcessVariableDto> getVariableUpdatesIngestedAt(Long ingestTimestamp);

  List<String> getDecisionVariableValues(
      DecisionVariableValueRequestDto requestDto, String variablesPath);

  List<ProcessVariableNameResponseDto> getVariableNames(
      ProcessVariableNameRequestDto variableNameRequest,
      List<ProcessToQueryDto> validNameRequests,
      List<String> processDefinitionKeys,
      Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos);

  List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(
      final List<String> processDefinitionKeysToTarget,
      final Supplier<BoolQuery.Builder> baseQueryBuilderSupplier,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos);

  List<String> getVariableValues(
      ProcessVariableValuesQueryDto requestDto,
      List<ProcessVariableSourceDto> processVariableSources);

  default String getValueSearchField(final String variablePath, final String searchFieldName) {
    return getVariableValueField(variablePath) + "." + searchFieldName;
  }

  default String buildWildcardQuery(final String valueFilter) {
    return "*" + valueFilter + "*";
  }

  default String extractProcessDefinitionKeyFromIndexName(final String indexName) {
    final int firstIndex = indexName.indexOf(PROCESS_INSTANCE_INDEX_NAME_SUBSECTION);
    final int lastIndex = indexName.lastIndexOf(PROCESS_INSTANCE_INDEX_NAME_SUBSECTION);
    if (firstIndex != lastIndex) {
      return null;
    }

    final int processDefKeyStartIndex =
        firstIndex + PROCESS_INSTANCE_INDEX_NAME_SUBSECTION.length();
    final int processDefKeyEndIndex = indexName.lastIndexOf("_v" + ProcessInstanceIndex.VERSION);
    return indexName.substring(processDefKeyStartIndex, processDefKeyEndIndex);
  }

  default ProcessVariableNameResponseDto processVariableNameResponseDtoFrom(
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsByKey,
      final String processDefinitionKey,
      final String variableName,
      final String variableType) {
    String labelValue = null;
    if (processDefinitionKey != null && definitionLabelsByKey.containsKey(processDefinitionKey)) {
      final List<LabelDto> labels = definitionLabelsByKey.get(processDefinitionKey).getLabels();
      for (final LabelDto label : labels) {
        if (label.getVariableName().equals(variableName)
            && label.getVariableType().toString().equalsIgnoreCase(variableType)) {
          labelValue = label.getVariableLabel();
        }
      }
    }
    return new ProcessVariableNameResponseDto(
        variableName, VariableType.getTypeForId(variableType), labelValue);
  }

  default List<ProcessVariableNameResponseDto> filterVariableNameResults(
      final List<ProcessVariableNameResponseDto> variableNames) {
    // Exclude object variables from this result as they are only visible in raw data reports.
    // Additionally, in case variables have the same name, type and label across definitions
    // then we eliminate duplicates and we display the variable as one.
    return variableNames.stream()
        .distinct()
        .filter(varName -> !VariableType.OBJECT.equals(varName.getType()))
        .collect(Collectors.toList());
  }
}
