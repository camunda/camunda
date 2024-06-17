/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import java.util.List;
import java.util.Map;
import org.elasticsearch.index.query.BoolQueryBuilder;

public interface ProcessVariableReader {

  String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  String NAME_AGGREGATION = "variableNameAggregation";
  String TYPE_AGGREGATION = "variableTypeAggregation";
  String VALUE_AGGREGATION = "values";
  String VAR_NAME_AND_TYPE_COMPOSITE_AGG = "varNameAndTypeCompositeAgg";
  String INDEX_AGGREGATION = "_index";
  String PROCESS_INSTANCE_INDEX_NAME_SUBSECTION =
      "-" + DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

  List<ProcessVariableNameResponseDto> getVariableNames(
      final ProcessVariableNameRequestDto variableNameRequest);

  List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(
      final List<String> processDefinitionKeysToTarget,
      final BoolQueryBuilder baseQuery,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos);

  String extractProcessDefinitionKeyFromIndexName(final String indexName);

  List<String> getVariableValues(final ProcessVariableValuesQueryDto requestDto);
}
