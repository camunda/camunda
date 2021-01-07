/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRelativeEvaluationDateFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;

public class DecisionEvaluationDateFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByFixedEvaluationDateStartFrom() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Collections.singletonList(createFixedEvaluationDateFilter(
      OffsetDateTime.now().plusDays(1),
      null
    )));

    RawDataDecisionReportResultDto result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(0L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).isEmpty();
  }

  private DecisionReportDataDto createReportWithAllVersion(DecisionDefinitionEngineDto decisionDefinitionDto) {
    return DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
  }

  @Test
  public void resultFilterByFixedEvaluationDateEndWith() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Collections.singletonList(createFixedEvaluationDateFilter(
      null,
      OffsetDateTime.now().plusDays(1)
    )));

    RawDataDecisionReportResultDto result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(5);
  }

  @Test
  public void resultFilterByFixedEvaluationDateRange() throws SQLException {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    // this one is from before the filter StartDate
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    OffsetDateTime evaluationTimeOfFirstRun = OffsetDateTime.now().minusDays(2L);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(
      decisionDefinitionDto.getId(),
      evaluationTimeOfFirstRun
    );
    OffsetDateTime evaluationTimeAfterFirstRun = evaluationTimeOfFirstRun.plusDays(1L);

    decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());


    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(
      evaluationTimeAfterFirstRun,
      OffsetDateTime.now().plusDays(1)
    )));

    RawDataDecisionReportResultDto result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(5);
  }

  @Test
  public void resultLimited_onTooBroadFixedEvaluationDateFilter() throws SQLException {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS);
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(AggregateByDateUnit.DAY)
      .setFilter(createFixedEvaluationDateFilter(beforeStart.minusDays(3L), beforeStart))
      .build();
    final AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluationResult =
      reportClient.evaluateMapReport(
        reportData);

    // then
    final ReportMapResultDto result = evaluationResult.getResult();
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(result.getIsComplete()).isFalse();
    assertThat(resultData).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("supportedRollingDateFilterUnits")
  @SneakyThrows
  public void resultFilterByRollingEvaluationDateStartFrom(DateFilterUnit dateFilterUnit) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, dateFilterUnit)));

    RawDataDecisionReportResultDto result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
  }

  @Test
  public void resultFilterByRollingEvaluationDateStartFrom_unsupportedQuarterUnit() {
    // when
    DecisionReportDataDto reportData =
      createReportWithAllVersion(engineIntegrationExtension.deployDecisionDefinition());
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, DateFilterUnit.QUARTERS)));

    // then
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void resultFilterByRollingEvaluationDateOutOfRange() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, DateFilterUnit.DAYS)));

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    RawDataDecisionReportResultDto result = evaluateReportWithNewAuthToken(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(0L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void resultLimited_onTooBroadRollingEvaluationDateFilter() throws SQLException {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now().minusDays(1);
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = beforeStart.plusDays(1);
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart;
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(AggregateByDateUnit.DAY)
      .setFilter(createRollingEvaluationDateFilter(4L, DateFilterUnit.DAYS))
      .build();
    final AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluationResult =
      reportClient.evaluateMapReport(
        reportData);

    // then
    final ReportMapResultDto result = evaluationResult.getResult();
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(result.getIsComplete()).isFalse();
    assertThat(resultData).hasSize(2);

    assertThat(resultData.get(0).getKey())
      .isEqualTo((embeddedOptimizeExtension.formatToHistogramBucketKey(lastEvaluationDateFilter, ChronoUnit.DAYS))
      );

    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(secondBucketEvaluationDate, ChronoUnit.DAYS));
  }

  @ParameterizedTest
  @MethodSource("dateFilterUnits")
  @SneakyThrows
  public void resultFilterByRelativeEvaluationDateCurrentInterval(DateFilterUnit dateFilterUnit) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRelativeEvaluationDateFilter(0L, dateFilterUnit)));

    RawDataDecisionReportResultDto result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("dateFilterUnits")
  @SneakyThrows
  public void resultFilterByRelativeEvaluationDatePreviousInterval(DateFilterUnit dateFilterUnit) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRelativeEvaluationDateFilter(1L, dateFilterUnit)));

    RawDataDecisionReportResultDto result = reportClient.evaluateDecisionRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(0L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).isEmpty();
  }

  @SneakyThrows
  private void freezeCurrentTimeAndStartDecisionInstance(final DecisionDefinitionEngineDto decisionDefinitionDto) {
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    final OffsetDateTime frozenTime = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(decisionDefinitionDto.getId(), frozenTime);
  }

  private static Stream<DateFilterUnit> supportedRollingDateFilterUnits() {
    return Stream.of(
      DateFilterUnit.MINUTES,
      DateFilterUnit.HOURS,
      DateFilterUnit.DAYS,
      DateFilterUnit.WEEKS,
      DateFilterUnit.MONTHS,
      DateFilterUnit.YEARS
    );
  }

  private static Stream<DateFilterUnit> dateFilterUnits() {
    return Stream.concat(
      supportedRollingDateFilterUnits(),
      Stream.of(DateFilterUnit.QUARTERS)
    );
  }


  private AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto> evaluateReportWithNewAuthToken(final DecisionReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withGivenAuthToken(embeddedOptimizeExtension.getNewAuthenticationToken())
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto>>() {});
      // @formatter:on
  }

}
