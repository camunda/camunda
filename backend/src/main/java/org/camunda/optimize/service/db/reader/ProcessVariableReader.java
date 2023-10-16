/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.List;
import java.util.Map;

public interface ProcessVariableReader {

  String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  String NAME_AGGREGATION = "variableNameAggregation";
  String TYPE_AGGREGATION = "variableTypeAggregation";
  String VALUE_AGGREGATION = "values";
  String VAR_NAME_AND_TYPE_COMPOSITE_AGG = "varNameAndTypeCompositeAgg";
  String INDEX_AGGREGATION = "_index";
  String PROCESS_INSTANCE_INDEX_NAME_SUBSECTION =
    "-" + ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;

  List<ProcessVariableNameResponseDto> getVariableNames(ProcessVariableNameRequestDto requestDto);

  List<ProcessVariableNameResponseDto> getVariableNames(final List<ProcessVariableNameRequestDto> variableNameRequests);

  List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(final BoolQueryBuilder baseQuery,
                                                                                 final Map<String,
                                                                                   DefinitionVariableLabelsDto> definitionLabelsDtos);
  String extractProcessDefinitionKeyFromIndexName(final String indexName);

  List<String> getVariableValues(final ProcessVariableValuesQueryDto requestDto);

}
