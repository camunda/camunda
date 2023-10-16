/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;

import java.util.List;

public interface DecisionVariableReader {

  String FILTERED_VARIABLES_AGGREGATION = "filteredVariables";
  String VALUE_AGGREGATION = "values";
  String VARIABLE_VALUE_NGRAM = "nGramField";
  String VARIABLE_VALUE_LOWERCASE = "lowercaseField";

  List<DecisionVariableNameResponseDto> getInputVariableNames(final String decisionDefinitionKey,
                                                              final List<String> decisionDefinitionVersions,
                                                              final List<String> tenantIds);

  List<DecisionVariableNameResponseDto> getOutputVariableNames(final String decisionDefinitionKey,
                                                               final List<String> decisionDefinitionVersions,
                                                               final List<String> tenantIds);

  List<String> getInputVariableValues(final DecisionVariableValueRequestDto requestDto);

  List<String> getOutputVariableValues(final DecisionVariableValueRequestDto requestDto);

}
