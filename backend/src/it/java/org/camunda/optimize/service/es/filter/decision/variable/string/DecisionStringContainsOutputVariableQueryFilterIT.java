/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.decision.variable.string;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.CONTAINS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringOutputVariableFilter;
import static org.camunda.optimize.util.DmnModels.STRING_OUTPUT_ID;

public class DecisionStringContainsOutputVariableQueryFilterIT extends AbstractDecisionStringContainsVariableQueryFilterIT {

  private static final String OUTPUT_VARIABLE_ID_TO_FILTER_ON = STRING_OUTPUT_ID;

  @Override
  protected void assertThatResultContainsVariables(final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result,
                                                   final String... shouldMatch) {
    // for outputs where the value is null the result has just an empty list so we need to filter them out
    List<String> shouldMatchWithoutNullResults =
      Arrays.stream(shouldMatch).filter(s -> !s.isEmpty()).collect(Collectors.toList());

    assertThat(result.getData())
      .hasSize(shouldMatch.length)
      .extracting(RawDataDecisionInstanceDto::getOutputVariables)
      .flatExtracting(Map::values)
      .filteredOn(var -> var.getId().equals(OUTPUT_VARIABLE_ID_TO_FILTER_ON))
      .flatExtracting(OutputVariableEntry::getValues)
      .containsExactlyInAnyOrderElementsOf(shouldMatchWithoutNullResults);
  }

  @Override
  protected List<DecisionFilterDto<?>> createContainsFilterForValues(final String... variableValues) {
    return Lists.newArrayList(createStringOutputVariableFilter(
      OUTPUT_VARIABLE_ID_TO_FILTER_ON, CONTAINS, variableValues
    ));
  }
}
