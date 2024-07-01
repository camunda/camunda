/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.db.es.filter.decision;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRelativeEvaluationDateFilter;
// import static
// io.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import com.fasterxml.jackson.core.type.TypeReference;
// import com.google.common.collect.Lists;
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
// import io.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
// import
// io.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
// import io.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
// import io.camunda.optimize.service.db.es.report.decision.AbstractDecisionDefinitionIT;
// import io.camunda.optimize.service.security.util.LocalDateUtil;
// import io.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
// import io.camunda.optimize.test.util.decision.DecisionReportDataType;
// import jakarta.ws.rs.core.Response;
// import java.sql.SQLException;
// import java.time.OffsetDateTime;
// import java.util.Collections;
// import java.util.List;
// import java.util.stream.Stream;
// import lombok.SneakyThrows;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class DecisionEvaluationDateFilterIT extends AbstractDecisionDefinitionIT {
//
//   @Test
//   public void resultFilterByFixedEvaluationDateStartFrom() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Collections.singletonList(
//             createFixedEvaluationDateFilter(OffsetDateTime.now().plusDays(1), null)));
//
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(0L);
//     assertThat(result.getData()).isNotNull();
//     assertThat(result.getData()).isEmpty();
//   }
//
//   private DecisionReportDataDto createReportWithAllVersion(
//       DecisionDefinitionEngineDto decisionDefinitionDto) {
//     return DecisionReportDataBuilder.create()
//         .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
//         .setDecisionDefinitionVersion(ALL_VERSIONS)
//         .setReportDataType(DecisionReportDataType.RAW_DATA)
//         .build();
//   }
//
//   @Test
//   public void resultFilterByFixedEvaluationDateEndWith() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Collections.singletonList(
//             createFixedEvaluationDateFilter(null, OffsetDateTime.now().plusDays(1))));
//
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(5L);
//     assertThat(result.getData()).isNotNull();
//     assertThat(result.getData()).hasSize(5);
//   }
//
//   @Test
//   public void resultFilterByFixedEvaluationDateRange() throws SQLException {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     // this one is from before the filter StartDate
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     OffsetDateTime evaluationTimeOfFirstRun = OffsetDateTime.now().minusDays(2L);
//     engineDatabaseExtension.changeDecisionInstanceEvaluationDate(
//         decisionDefinitionDto.getId(), evaluationTimeOfFirstRun);
//     OffsetDateTime evaluationTimeAfterFirstRun = evaluationTimeOfFirstRun.plusDays(1L);
//
//     decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(
//         Lists.newArrayList(
//             createFixedEvaluationDateFilter(
//                 evaluationTimeAfterFirstRun, OffsetDateTime.now().plusDays(1))));
//
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(5L);
//     assertThat(result.getData()).isNotNull();
//     assertThat(result.getData()).hasSize(5);
//   }
//
//   @ParameterizedTest
//   @MethodSource("supportedRollingDateFilterUnits")
//   @SneakyThrows
//   public void resultFilterByRollingEvaluationDateStartFrom(DateUnit dateUnit) {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, dateUnit)));
//
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getData()).isNotNull();
//     assertThat(result.getData()).hasSize(1);
//   }
//
//   @Test
//   public void resultFilterByRollingEvaluationDateStartFrom_unsupportedQuarterUnit() {
//     // when
//     DecisionReportDataDto reportData =
//         createReportWithAllVersion(engineIntegrationExtension.deployDecisionDefinition());
//     reportData.setFilter(
//         Lists.newArrayList(createRollingEvaluationDateFilter(1L, DateUnit.QUARTERS)));
//
//     // then
//     Response response = reportClient.evaluateReportAndReturnResponse(reportData);
//     assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
//   }
//
//   @Test
//   public void resultFilterByRollingEvaluationDateOutOfRange() {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L,
// DateUnit.DAYS)));
//
//     LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));
//
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         evaluateReportWithNewAuthToken(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(0L);
//     assertThat(result.getData()).isNotNull();
//     assertThat(result.getData()).isEmpty();
//   }
//
//   @ParameterizedTest
//   @MethodSource("dateFilterUnits")
//   @SneakyThrows
//   public void resultFilterByRelativeEvaluationDateCurrentInterval(DateUnit dateUnit) {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(Lists.newArrayList(createRelativeEvaluationDateFilter(0L, dateUnit)));
//
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(1L);
//     assertThat(result.getData()).isNotNull();
//     assertThat(result.getData()).hasSize(1);
//   }
//
//   @ParameterizedTest
//   @MethodSource("dateFilterUnits")
//   @SneakyThrows
//   public void resultFilterByRelativeEvaluationDatePreviousInterval(DateUnit dateUnit) {
//     // given
//     DecisionDefinitionEngineDto decisionDefinitionDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//     freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
//     reportData.setFilter(Lists.newArrayList(createRelativeEvaluationDateFilter(1L, dateUnit)));
//
//     ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
//         reportClient.evaluateDecisionRawReport(reportData).getResult();
//
//     // then
//     assertThat(result.getInstanceCount()).isEqualTo(0L);
//     assertThat(result.getData()).isNotNull();
//     assertThat(result.getData()).isEmpty();
//   }
//
//   @SneakyThrows
//   private void freezeCurrentTimeAndStartDecisionInstance(
//       final DecisionDefinitionEngineDto decisionDefinitionDto) {
//     LocalDateUtil.setCurrentTime(OffsetDateTime.now());
//     final OffsetDateTime frozenTime = LocalDateUtil.getCurrentDateTime();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
//     engineDatabaseExtension.changeDecisionInstanceEvaluationDate(
//         decisionDefinitionDto.getId(), frozenTime);
//   }
//
//   private static Stream<DateUnit> supportedRollingDateFilterUnits() {
//     return Stream.of(
//         DateUnit.MINUTES,
//         DateUnit.HOURS,
//         DateUnit.DAYS,
//         DateUnit.WEEKS,
//         DateUnit.MONTHS,
//         DateUnit.YEARS);
//   }
//
//   private static Stream<DateUnit> dateFilterUnits() {
//     return Stream.concat(supportedRollingDateFilterUnits(), Stream.of(DateUnit.QUARTERS));
//   }
//
//   private AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>
//       evaluateReportWithNewAuthToken(final DecisionReportDataDto reportData) {
//     return embeddedOptimizeExtension
//         .getRequestExecutor()
//         .withGivenAuthToken(embeddedOptimizeExtension.getNewAuthenticationToken())
//         .buildEvaluateSingleUnsavedReportRequest(reportData)
//         // @formatter:off
//         .execute(
//             new TypeReference<
//                 AuthorizedDecisionReportEvaluationResponseDto<
//                     List<RawDataDecisionInstanceDto>>>() {});
//     // @formatter:on
//   }
// }
