/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.report.decision;
//
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
// import
// io.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.measure.MeasureResponseDto;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import io.camunda.optimize.util.SuppressionConstants;
// import java.util.Arrays;
// import java.util.List;
// import java.util.stream.Stream;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.Arguments;
// import org.junit.jupiter.params.provider.MethodSource;
//
// public class MultiViewPropertyDecisionReportIT extends AbstractDecisionDefinitionIT {
//
//   @ParameterizedTest
//   @MethodSource("multiViewPropertyScenarios")
//   public void onlyReturnsOneResultForFirstViewProperty(final List<ViewProperty> viewProperties) {
//     // given
//     final DecisionDefinitionEngineDto decisionDefinition =
//         deployAndStartSimpleDecisionDefinition("key");
//     importAllEngineEntitiesFromScratch();
//
//     final DecisionReportDataDto reportData =
//         DecisionReportDataBuilder.create()
//             .setDecisionDefinitionKey(decisionDefinition.getKey())
//             .setDecisionDefinitionVersion(decisionDefinition.getVersionAsString())
//             .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
//             .build();
//     reportData.getView().setProperties(viewProperties);
//
//     // when
//     AuthorizedDecisionReportEvaluationResponseDto<Double> evaluationResponse =
//         reportClient.evaluateNumberReport(reportData);
//
//     // then
//     final ReportResultResponseDto<Double> resultDto = evaluationResponse.getResult();
//     assertThat(resultDto.getMeasures())
//         .extracting(MeasureResponseDto::getProperty)
//         .containsExactly(viewProperties.get(0));
//
// assertThat(resultDto.getMeasures()).extracting(MeasureResponseDto::getData).containsExactly(1.);
//     assertThat(resultDto.getMeasures())
//         .extracting(MeasureResponseDto::getAggregationType)
//         .containsOnlyNulls();
//     assertThat(resultDto.getMeasures())
//         .extracting(MeasureResponseDto::getUserTaskDurationTime)
//         .containsOnlyNulls();
//     assertThat(resultDto.getMeasures())
//         .extracting(MeasureResponseDto::getType)
//         .containsOnly(ResultType.NUMBER);
//   }
//
//   @SuppressWarnings(SuppressionConstants.UNUSED)
//   private static Stream<Arguments> multiViewPropertyScenarios() {
//     return Stream.of(
//         Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.DURATION)),
//         Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.RAW_DATA)),
//         Arguments.of(
//             Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.RAW_DATA, ViewProperty.DURATION)),
//         Arguments.of(Arrays.asList(ViewProperty.FREQUENCY, ViewProperty.FREQUENCY)));
//   }
// }
