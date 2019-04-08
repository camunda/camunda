/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.rest.report.DecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.filter.FilterOperatorConstants;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.test.util.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.DecisionReportDataType;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.report.single.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.camunda.optimize.test.util.DecisionFilterUtilHelper.createDoubleInputVariableFilter;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;

@RunWith(JUnitParamsRunner.class)
public class CountDecisionInstanceFrequencyGroupByEvaluationDateIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationSingleBucketSpecificVersionGroupedByDay() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, GroupByDateUnit.DAY
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(3L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getData().values().stream().findFirst().get(), is(3L));
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupedByDay() throws SQLException {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    engineDatabaseRule.changeDecisionInstanceEvaluationDate(beforeStart, beforeStart.minusDays(1));

    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, GroupByDateUnit.DAY
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(2));
    final Iterator<Long> resultValueIterator = result.getData().values().iterator();
    assertThat(resultValueIterator.next(), is(2L));
    assertThat(resultValueIterator.next(), is(3L));
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupedByDayResultIsSortedInDescendingOrder()
    throws Exception {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
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
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, GroupByDateUnit.DAY
    ).getResult();

    // then
    Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      contains(
        formatToHistogramBucketKey(lastEvaluationDateFilter, ChronoUnit.DAYS),
        formatToHistogramBucketKey(secondBucketEvaluationDate, ChronoUnit.DAYS),
        formatToHistogramBucketKey(thirdBucketEvaluationDate, ChronoUnit.DAYS)
      )
    );
    assertThat(new ArrayList<>(resultMap.values()), contains(2L, 2L, 3L));
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() throws SQLException {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
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
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.ASC));
    final DecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluationResult = evaluateMapReport(reportData);

    // then
    final DecisionReportMapResultDto result = evaluationResult.getResult();
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      contains(
        formatToHistogramBucketKey(thirdBucketEvaluationDate, ChronoUnit.DAYS),
        formatToHistogramBucketKey(secondBucketEvaluationDate, ChronoUnit.DAYS),
        formatToHistogramBucketKey(lastEvaluationDateFilter, ChronoUnit.DAYS)
      )
    );
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() throws SQLException {
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
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(GroupByDateUnit.DAY)
      .build();
    reportData.getParameters().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final DecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluationResult = evaluateMapReport(reportData);

    // then
    final DecisionReportMapResultDto result = evaluationResult.getResult();
    final Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    final List<Long> bucketValues = new ArrayList<>(resultMap.values());
    assertThat(
      new ArrayList<>(bucketValues),
      contains(bucketValues.stream().sorted(Comparator.naturalOrder()).toArray())
    );
  }

  @Test
  @Parameters(method = "groupByDateUnits")
  public void reportEvaluationMultiBucketsSpecificVersionGroupedByDifferentUnitsEmptyBucketBetweenTwoOthers(
    final GroupByDateUnit groupByDateUnit
  ) throws Exception {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    final ChronoUnit chronoUnit = ChronoUnit.valueOf(groupByDateUnit.name().toUpperCase() + "S");
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minus(2, chronoUnit);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second empty bucket
    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minus(1, chronoUnit);

    // first bucket
    lastEvaluationDateFilter = OffsetDateTime.now();
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, groupByDateUnit
    ).getResult();

    // then
    Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(3));
    assertThat(
      new ArrayList<>(resultMap.keySet()),
      contains(
        formatToHistogramBucketKey(lastEvaluationDateFilter, chronoUnit),
        formatToHistogramBucketKey(secondBucketEvaluationDate, chronoUnit),
        formatToHistogramBucketKey(thirdBucketEvaluationDate, chronoUnit)
      )
    );
    assertThat(new ArrayList<>(resultMap.values()), contains(2L, 0L, 3L));
  }

  @Test
  public void automaticIntervalSelectionWorks() throws Exception {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minus(5, ChronoUnit.DAYS);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(beforeStart, thirdBucketEvaluationDate);

    // second empty bucket

    // first bucket
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    final OffsetDateTime firstBucketEvaluationDate = beforeStart.minus(1, ChronoUnit.DAYS);
    engineDatabaseRule.changeDecisionInstanceEvaluationDate(beforeStart, firstBucketEvaluationDate);

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, GroupByDateUnit.AUTOMATIC
    ).getResult();

    // then
    Map<String, Long> resultMap = result.getData();
    assertThat(resultMap.size(), is(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION));
    ArrayList<Long> resultValues = new ArrayList<>(resultMap.values());
    assertThat(resultValues.get(0), is(2L));
    assertThat(resultValues.stream().mapToInt(Long::intValue).sum(), is(5));
    assertThat(resultValues.get(resultMap.size() - 1), is(3L));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByYear() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, GroupByDateUnit.YEAR
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getData().values().stream().findFirst().get(), is(5L));
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByYearOtherDefinitionsHaveNoSideEffect() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());
    engineRule.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineRule.startDecisionInstance(decisionDefinitionDto2.getId());

    // other decision definition
    deployAndStartSimpleDecisionDefinition("key2");

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    final DecisionReportMapResultDto result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, GroupByDateUnit.YEAR
    ).getResult();

    // then
    assertThat(result.getDecisionInstanceCount(), is(5L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getData().values().stream().findFirst().get(), is(5L));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredByInputValue() {
    // given
    final double inputVariableValueToFilterFor = 200.0;
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineRule.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(inputVariableValueToFilterFor, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto.getId(),
      createInputs(inputVariableValueToFilterFor + 100.0, "Misc")
    );

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(String.valueOf(decisionDefinitionDto.getVersion()))
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(GroupByDateUnit.HOUR)
      .setFilter(createDoubleInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperatorConstants.GREATER_THAN_EQUALS, String.valueOf(inputVariableValueToFilterFor)
      ))
      .build();
    final DecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluationResult = evaluateMapReport(reportData);

    // then
    final DecisionReportMapResultDto result = evaluationResult.getResult();
    assertThat(result.getDecisionInstanceCount(), is(2L));
    assertThat(result.getData(), is(notNullValue()));
    assertThat(result.getData().size(), is(1));
    assertThat(result.getData().values().stream().findFirst().get(), is(2L));
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .build();
    reportData.getView().setProperty(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private static GroupByDateUnit[] groupByDateUnits() {
    return Arrays.stream(GroupByDateUnit.values())
      .filter(v -> !v.equals(GroupByDateUnit.AUTOMATIC))
      .toArray(GroupByDateUnit[]::new);
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .build();
    reportData.getGroupBy().setType(null);

    //when
    Response response = evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus(), is(400));
  }

  private DecisionReportEvaluationResultDto<DecisionReportMapResultDto> evaluateDecisionInstanceFrequencyByEvaluationDate(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final GroupByDateUnit groupByDateUnit) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(groupByDateUnit)
      .build();
    return evaluateMapReport(reportData);
  }


  private String formatToHistogramBucketKey(final OffsetDateTime offsetDateTime, final ChronoUnit unit) {
    return embeddedOptimizeRule.getDateTimeFormatter().format(truncateToStartOfUnit(offsetDateTime, unit));
  }

}
