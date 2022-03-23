/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.EvaluationDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.instance.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createNumericInputVariableFilter;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.DmnModels.INPUT_AMOUNT_ID;

public class DecisionInstanceFrequencyGroupByEvaluationDateIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationSingleBucketSpecificVersionGroupedByDay() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, AggregateByDateUnit.DAY
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(3L);
    assertThat(result.getFirstMeasureData()).isNotNull();
    assertThat(result.getFirstMeasureData()).hasSize(1);
    assertThat(result.getFirstMeasureData().get(0).getValue()).isEqualTo(3.);
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupedByDay() {
    // given
    final OffsetDateTime beforeStart = OffsetDateTime.now();
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(beforeStart, beforeStart.minusDays(1));

    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, AggregateByDateUnit.DAY
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull().hasSize(2);
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
    assertThat(resultData.get(1).getValue()).isEqualTo(3.);
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupedByDayResultIsSortedInDescendingOrder() {
    // given
    final OffsetDateTime beforeStart = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    lastEvaluationDateFilter = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, AggregateByDateUnit.DAY
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(lastEvaluationDateFilter, ChronoUnit.DAYS));
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(secondBucketEvaluationDate, ChronoUnit.DAYS));
    assertThat(resultData.get(1).getValue()).isEqualTo(2.);
    assertThat(resultData.get(2).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(thirdBucketEvaluationDate, ChronoUnit.DAYS));
    assertThat(resultData.get(2).getValue()).isEqualTo(3.);
  }

  @Test
  public void testCustomOrderOnResultKeyIsApplied() {
    // given
    final OffsetDateTime beforeStart = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    lastEvaluationDateFilter = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(AggregateByDateUnit.DAY)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.ASC));
    final AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResult =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResult.getResult();
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(thirdBucketEvaluationDate, ChronoUnit.DAYS));
    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(secondBucketEvaluationDate, ChronoUnit.DAYS));
    assertThat(resultData.get(2).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(lastEvaluationDateFilter, ChronoUnit.DAYS));
  }

  @Test
  public void testCustomOrderOnResultValueIsApplied() {
    // given
    final OffsetDateTime beforeStart = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    OffsetDateTime lastEvaluationDateFilter = beforeStart;

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minusDays(2);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, thirdBucketEvaluationDate);

    // second bucket
    lastEvaluationDateFilter = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minusDays(1);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(lastEvaluationDateFilter, secondBucketEvaluationDate);

    // first bucket
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(AggregateByDateUnit.DAY)
      .build();
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResult =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResult.getResult();
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    final List<Double> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testEmptyBucketsAreReturnedForEvaluationDateFilterPeriod() {
    // given
    final OffsetDateTime startDate = LocalDateUtil.getCurrentDateTime();

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime secondBucketEvaluationDate = startDate.minusDays(2);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(startDate, secondBucketEvaluationDate);

    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final RollingDateFilterDataDto dateFilterDataDto = new RollingDateFilterDataDto(
      new RollingDateFilterStartDto(4L, DateUnit.DAYS)
    );

    final EvaluationDateFilterDto dateFilterDto = new EvaluationDateFilterDto(dateFilterDataDto);

    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(AggregateByDateUnit.DAY)
      .setFilter(dateFilterDto)
      .build();

    final AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResult =
      reportClient.evaluateMapReport(reportData);

    // then
    final List<MapResultEntryDto> resultData = evaluationResult.getResult().getFirstMeasureData();
    assertThat(resultData).hasSize(5);

    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS));
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);

    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS));
    assertThat(resultData.get(1).getValue()).isEqualTo(0.);

    assertThat(resultData.get(2).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(2), ChronoUnit.DAYS));
    assertThat(resultData.get(2).getValue()).isEqualTo(2.);

    assertThat(resultData.get(3).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(3), ChronoUnit.DAYS));
    assertThat(resultData.get(3).getValue()).isEqualTo(0.);

    assertThat(resultData.get(4).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(startDate.minusDays(4), ChronoUnit.DAYS));
    assertThat(resultData.get(4).getValue()).isEqualTo(0.);
  }

  @ParameterizedTest
  @MethodSource("groupByDateUnits")
  public void reportEvaluationMultiBucketsSpecificVersionGroupedByDifferentUnitsEmptyBucketBetweenTwoOthers(
    final AggregateByDateUnit groupByDateUnit) {
    // given
    final OffsetDateTime beforeStart = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
    final ChronoUnit chronoUnit = ChronoUnit.valueOf(groupByDateUnit.name().toUpperCase() + "S");

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion = String.valueOf(decisionDefinitionDto.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minus(2, chronoUnit);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(beforeStart, thirdBucketEvaluationDate);

    // second empty bucket
    final OffsetDateTime secondBucketEvaluationDate = beforeStart.minus(1, chronoUnit);

    // first bucket
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto, decisionDefinitionVersion, groupByDateUnit
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(3);
    assertThat(resultData.get(0).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(beforeStart, chronoUnit));
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
    assertThat(resultData.get(1).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(secondBucketEvaluationDate, chronoUnit));
    assertThat(resultData.get(1).getValue()).isEqualTo(0.);
    assertThat(resultData.get(2).getKey())
      .isEqualTo(embeddedOptimizeExtension.formatToHistogramBucketKey(thirdBucketEvaluationDate, chronoUnit));
    assertThat(resultData.get(2).getValue()).isEqualTo(3.);
  }

  @Test
  public void automaticIntervalSelectionWorks() {
    // given
    final OffsetDateTime beforeStart = DateCreationFreezer.dateFreezer().freezeDateAndReturn();

    // third bucket
    final DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    final OffsetDateTime thirdBucketEvaluationDate = beforeStart.minus(5, ChronoUnit.DAYS);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(beforeStart, thirdBucketEvaluationDate);

    // second empty bucket

    // first bucket
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    final OffsetDateTime firstBucketEvaluationDate = beforeStart.minus(1, ChronoUnit.DAYS);
    engineDatabaseExtension.changeDecisionInstanceEvaluationDate(beforeStart, firstBucketEvaluationDate);

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, decisionDefinitionVersion1, AggregateByDateUnit.AUTOMATIC
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.get(0).getValue()).isEqualTo(2.);
    assertThat(resultData.stream().map(MapResultEntryDto::getValue).mapToInt(Double::intValue).sum()).isEqualTo(5);
    assertThat(resultData.get(resultData.size() - 1).getValue()).isEqualTo(3.);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByYear() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, AggregateByDateUnit.YEAR
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull().hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(5.);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByYearOtherDefinitionsHaveNoSideEffect() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto1.getId());

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = deployAndStartSimpleDecisionDefinition("key");
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    // other decision definition
    deployAndStartSimpleDecisionDefinition("key2");

    importAllEngineEntitiesFromScratch();

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluateDecisionInstanceFrequencyByEvaluationDate(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, AggregateByDateUnit.YEAR
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(5L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData).isNotNull().hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(5.);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    final String decisionDefinitionKey = deployAndStartMultiTenantDefinition(
      Lists.newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setTenantIds(selectedTenants)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(AggregateByDateUnit.HOUR)
      .build();
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo((long) selectedTenants.size());
  }

  @Test
  public void reportEvaluationSingleBucketFilteredByInputValue() {
    // given
    final double inputVariableValueToFilterFor = 200.0;
    final DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
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

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(String.valueOf(decisionDefinitionDto.getVersion()))
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(AggregateByDateUnit.HOUR)
      .setFilter(createNumericInputVariableFilter(
        INPUT_AMOUNT_ID, FilterOperator.GREATER_THAN_EQUALS, String.valueOf(inputVariableValueToFilterFor)
      ))
      .build();
    final AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResult =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResult.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData)
      .isNotNull()
      .hasSize(1);
    assertThat(resultData.get(0).getValue()).isEqualTo(2);
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .build();
    reportData.getView().setProperties((ViewProperty) null);

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  private static AggregateByDateUnit[] groupByDateUnits() {
    return Arrays.stream(AggregateByDateUnit.values())
      .filter(v -> !v.equals(AggregateByDateUnit.AUTOMATIC))
      .toArray(AggregateByDateUnit[]::new);
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

    // when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private AuthorizedDecisionReportEvaluationResponseDto<List<MapResultEntryDto>> evaluateDecisionInstanceFrequencyByEvaluationDate(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final AggregateByDateUnit groupByDateUnit) {
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_EVALUATION_DATE_TIME)
      .setDateInterval(groupByDateUnit)
      .build();
    return reportClient.evaluateMapReport(reportData);
  }

}
