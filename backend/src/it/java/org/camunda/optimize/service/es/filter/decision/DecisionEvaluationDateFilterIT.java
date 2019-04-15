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
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.rest.report.DecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.DecisionReportDataType;
import org.junit.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createRollingEvaluationDateFilter;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class DecisionEvaluationDateFilterIT extends AbstractDecisionDefinitionIT {

  @Test
  public void resultFilterByFixedEvaluationDateStartFrom() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(OffsetDateTime.now(), null)));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(0L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(0));
  }

  @Test
  public void resultFilterByFixedEvaluationDateEndWith() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(null, OffsetDateTime.now())));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(5));
  }

  @Test
  public void resultFilterByFixedEvaluationDateRange() throws SQLException {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    // this one is from before the filter StartDate
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    OffsetDateTime evaluationTimeOfFirstRun = OffsetDateTime.now().minusSeconds(2L);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(decisionDefinitionDto.getId(), evaluationTimeOfFirstRun);
    OffsetDateTime evaluationTimeAfterFirstRun = evaluationTimeOfFirstRun.plusSeconds(1L);

    decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());


    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(
      evaluationTimeAfterFirstRun,
      OffsetDateTime.now()
    )));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(5));
  }

  @Test
  public void resultFilterByRollingEvaluationDateStartFrom() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, RelativeDateFilterUnit.DAYS)));

    RawDataDecisionReportResultDto result = evaluateRawReport(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(1L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
  }

  @Test
  public void resultFilterByRollingEvaluationDateOutOfRange() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    engineRule.startDecisionInstance(decisionDefinitionDto.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.createDecisionReportDataViewRawAsTable(
      decisionDefinitionDto.getKey(), ALL_VERSIONS
    );
    reportData.setFilter(Lists.newArrayList(createRollingEvaluationDateFilter(1L, RelativeDateFilterUnit.DAYS)));

    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    RawDataDecisionReportResultDto result = evaluateReportWithNewAuthToken(reportData).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(0L));
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
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(createRollingEvaluationDateFilter(3L, RelativeDateFilterUnit.DAYS))
      .build();
    final DecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluationResult = evaluateMapReport(reportData);

    // then
    final DecisionReportMapResultDto result = evaluationResult.getResult();
    final Map<String, Long> resultMap = result.getData();
    assertThat(result.getIsComplete(), is(false));
    assertThat(resultMap.size(), is(1));
  }

  @Test
  public void resultLimited_onTooBroadFixedEvaluationDateFilter() throws SQLException {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    embeddedOptimizeRule.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(GroupByDateUnit.DAY)
      .setFilter(createFixedEvaluationDateFilter(beforeStart.minusDays(3L), beforeStart))
      .build();
    final DecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluationResult = evaluateMapReport(reportData);

    // then
    final DecisionReportMapResultDto result = evaluationResult.getResult();
    final Map<String, Long> resultMap = result.getData();
    assertThat(result.getIsComplete(), is(false));
    assertThat(resultMap.size(), is(1));
  }

  private DecisionReportEvaluationResultDto<RawDataDecisionReportResultDto> evaluateReportWithNewAuthToken(final DecisionReportDataDto reportData) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .withGivenAuthToken(embeddedOptimizeRule.getNewAuthenticationToken())
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      // @formatter:off
      .execute(new TypeReference<DecisionReportEvaluationResultDto<RawDataDecisionReportResultDto>>() {});
      // @formatter:on
  }

}
