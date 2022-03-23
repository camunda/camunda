/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision;

import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.util.SuppressionConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiViewPropertyDecisionReportIT extends AbstractDecisionDefinitionIT {

  @ParameterizedTest
  @MethodSource("multiViewPropertyScenarios")
  public void onlyReturnsOneResultForFirstViewProperty(final List<ViewProperty> viewProperties) {
    // given
    final DecisionDefinitionEngineDto decisionDefinition = deployAndStartSimpleDecisionDefinition("key");
    importAllEngineEntitiesFromScratch();

    final DecisionReportDataDto reportData = DecisionReportDataBuilder
      .create()
      .setDecisionDefinitionKey(decisionDefinition.getKey())
      .setDecisionDefinitionVersion(decisionDefinition.getVersionAsString())
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();
    reportData.getView().setProperties(viewProperties);

    // when
    AuthorizedDecisionReportEvaluationResponseDto<Double> evaluationResponse = reportClient
      .evaluateNumberReport(reportData);

    // then
    final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getMeasures())
      .extracting(MeasureResponseDto::getProperty)
      .containsExactly(viewProperties.get(0));
    assertThat(resultDto.getMeasures()).extracting(MeasureResponseDto::getData).containsExactly(1.);
    assertThat(resultDto.getMeasures()).extracting(MeasureResponseDto::getAggregationType).containsOnlyNulls();
    assertThat(resultDto.getMeasures()).extracting(MeasureResponseDto::getUserTaskDurationTime).containsOnlyNulls();
    assertThat(resultDto.getMeasures()).extracting(MeasureResponseDto::getType).containsOnly(ResultType.NUMBER);
  }

  @SuppressWarnings(SuppressionConstants.UNUSED)
  private static Stream<Arguments> multiViewPropertyScenarios() {
    return Stream.of(
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION)),
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.RAW_DATA)),
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.RAW_DATA, ViewProperty.DURATION)),
      Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.FREQUENCY))
    );
  }

}
