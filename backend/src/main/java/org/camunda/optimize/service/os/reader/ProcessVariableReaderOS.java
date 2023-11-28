/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import org.camunda.optimize.service.db.reader.ProcessVariableReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class ProcessVariableReaderOS implements ProcessVariableReader {

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNames(final ProcessVariableNameRequestDto requestDto) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNames(final List<ProcessVariableNameRequestDto> variableNameRequests) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(final BoolQueryBuilder baseQuery,
                                                                                        final Map<String,
                                                                                          DefinitionVariableLabelsDto> definitionLabelsDtos) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public String extractProcessDefinitionKeyFromIndexName(final String indexName) {
    //todo will be handled in the OPT-7230
    return "";
  }

  @Override
  public List<String> getVariableValues(final ProcessVariableValuesQueryDto requestDto) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

}
