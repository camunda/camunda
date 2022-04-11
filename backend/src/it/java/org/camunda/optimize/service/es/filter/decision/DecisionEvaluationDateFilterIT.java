/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
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

    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      reportClient.evaluateDecisionRawReport(reportData)
        .getResult();

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

    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      reportClient.evaluateDecisionRawReport(reportData)
      .getResult();

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

    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      reportClient.evaluateDecisionRawReport(reportData)
        .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(5);
  }

  @ParameterizedTest
  @MethodSource("supportedRollingDateFilterUnits")
  @SneakyThrows
  public void resultFilterByRollingEvaluationDateStartFrom(DateUnit dateUnit) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, dateUnit)));

    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      reportClient.evaluateDecisionRawReport(reportData)
        .getResult();

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
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, DateUnit.QUARTERS)));

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
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, DateUnit.DAYS)));

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      evaluateReportWithNewAuthToken(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(0L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("dateFilterUnits")
  @SneakyThrows
  public void resultFilterByRelativeEvaluationDateCurrentInterval(DateUnit dateUnit) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRelativeEvaluationDateFilter(0L, dateUnit)));

    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      reportClient.evaluateDecisionRawReport(reportData)
        .getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
  }

  @ParameterizedTest
  @MethodSource("dateFilterUnits")
  @SneakyThrows
  public void resultFilterByRelativeEvaluationDatePreviousInterval(DateUnit dateUnit) {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    freezeCurrentTimeAndStartDecisionInstance(decisionDefinitionDto);

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRelativeEvaluationDateFilter(1L, dateUnit)));

    ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result =
      reportClient.evaluateDecisionRawReport(reportData)
        .getResult();

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

  private static Stream<DateUnit> supportedRollingDateFilterUnits() {
    return Stream.of(
      DateUnit.MINUTES,
      DateUnit.HOURS,
      DateUnit.DAYS,
      DateUnit.WEEKS,
      DateUnit.MONTHS,
      DateUnit.YEARS
    );
  }

  private static Stream<DateUnit> dateFilterUnits() {
    return Stream.concat(
      supportedRollingDateFilterUnits(),
      Stream.of(DateUnit.QUARTERS)
    );
  }


  private AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>> evaluateReportWithNewAuthToken(final DecisionReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withGivenAuthToken(embeddedOptimizeExtension.getNewAuthenticationToken())
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResponseDto<List<RawDataDecisionInstanceDto>>>() {});
      // @formatter:on
  }

}
