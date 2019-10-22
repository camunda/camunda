/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.decision;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionEvaluationDateFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByFixedEvaluationDateStartFrom() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(OffsetDateTime.now(), null)));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(0L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(0));
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
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(null, OffsetDateTime.now())));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(5));
  }

  @Test
  public void resultFilterByFixedEvaluationDateRange() throws SQLException {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    // this one is from before the filter StartDate
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    OffsetDateTime evaluationTimeOfFirstRun = OffsetDateTime.now().minusSeconds(2L);
    engineDatabaseExtensionRule.changeDecisionInstanceEvaluationDate(decisionDefinitionDto.getId(), evaluationTimeOfFirstRun);
    OffsetDateTime evaluationTimeAfterFirstRun = evaluationTimeOfFirstRun.plusSeconds(1L);

    decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());


    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(
      evaluationTimeAfterFirstRun,
      OffsetDateTime.now()
    )));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(5));
  }

  @Test
  public void resultFilterByRollingEvaluationDateStartFrom() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, RelativeDateFilterUnit.DAYS)));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
  }

  @Test
  public void resultFilterByRollingEvaluationDateOutOfRange() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtensionRule.deployDecisionDefinition();
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = createReportWithAllVersion(decisionDefinitionDto);
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, RelativeDateFilterUnit.DAYS)));

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    RawDataDecisionReportResultDto result = evaluateReportWithNewAuthToken(reportData).getResult();

    // then
    assertThat(result.getInstanceCount(), is(0L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(0));
  }

  @Test
  public void resultLimited_onTooBroadRelativeEvaluationDateFilter() throws SQLException {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseExtensionRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseExtensionRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    embeddedOptimizeExtensionRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(createRollingEvaluationDateFilter(3L, RelativeDateFilterUnit.DAYS))
      .build();
    final AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluationResult = evaluateMapReport(reportData);

    // then
    final ReportMapResultDto result = evaluationResult.getResult();
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(result.getIsComplete(), is(false));
    assertThat(resultData.size(), is(2));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtensionRule.formatToHistogramBucketKey(lastEvaluationDateFilter, ChronoUnit.DAYS))
    );

    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtensionRule.formatToHistogramBucketKey(secondBucketEvaluationDate, ChronoUnit.DAYS))
    );
  }

  @Test
  public void resultLimited_onTooBroadFixedEvaluationDateFilter() throws SQLException {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseExtensionRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseExtensionRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtensionRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    embeddedOptimizeExtensionRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(createFixedEvaluationDateFilter(beforeStart.minusDays(3L), beforeStart))
      .build();
    final AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluationResult = evaluateMapReport(reportData);

    // then
    final ReportMapResultDto result = evaluationResult.getResult();
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(result.getIsComplete(), is(false));
    assertThat(resultData.size(), is(1));
  }

  private AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto> evaluateReportWithNewAuthToken(final DecisionReportDataDto reportData) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withGivenAuthToken(embeddedOptimizeExtensionRule.getNewAuthenticationToken())
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<AuthorizedDecisionReportEvaluationResultDto<RawDataDecisionReportResultDto>>() {});
      // @formatter:on
  }

}
