/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import org.camunda.optimize.service.db.reader.DecisionVariableReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DecisionVariableReaderOS implements DecisionVariableReader {

  @Override
  public List<DecisionVariableNameResponseDto> getInputVariableNames(final String decisionDefinitionKey,
                                                                     final List<String> decisionDefinitionVersions,
                                                                     final List<String> tenantIds) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<DecisionVariableNameResponseDto> getOutputVariableNames(final String decisionDefinitionKey,
                                                                      final List<String> decisionDefinitionVersions,
                                                                      final List<String> tenantIds) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<String> getInputVariableValues(final DecisionVariableValueRequestDto requestDto) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

  @Override
  public List<String> getOutputVariableValues(final DecisionVariableValueRequestDto requestDto) {
    //todo will be handled in the OPT-7230
    return new ArrayList<>();
  }

}
