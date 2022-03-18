/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single;

import com.google.common.collect.ImmutableList;
import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.operator.ComparisonOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.ReportSortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.BpmnModels.getSingleUserTaskDiagram;

public abstract class ModelElementFrequencyByModelElementDurationIT extends AbstractProcessDefinitionIT {

  protected abstract ProcessInstanceEngineDto startProcessInstanceCompleteTaskAndModifyDuration(
    final String definitionId,
    final Number durationInMillis);

  protected abstract void changeRunningInstanceReferenceDate(final ProcessInstanceEngineDto runningProcessInstance,
                                                             final OffsetDateTime startTime);

  protected abstract ProcessViewEntity getModelElementView();

  protected abstract int getNumberOfModelElementsPerInstance();

  protected abstract ProcessReportDataDto createReport(final String processKey, final String definitionVersion);

  @Test
  public void simpleReportEvaluation() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), durationInMilliseconds);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(definition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(definition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(getModelElementView());
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.DURATION);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData())
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(
        Tuple.tuple(createDurationBucketKey(durationInMilliseconds), getExpectedNumberOfModelElements())
      );
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), durationInMilliseconds);
    importAllEngineEntitiesFromScratch();

    final String reportId = createAndStoreDefaultReportDefinition(definition.getKey(), definition.getVersionAsString());

    // when
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(definition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(definition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(getModelElementView());
    assertThat(resultReportDataDto.getView().getFirstProperty()).isEqualTo(ViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.DURATION);

    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData())
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(
        Tuple.tuple(createDurationBucketKey(durationInMilliseconds), getExpectedNumberOfModelElements())
      );
  }

  @Test
  public void simpleReportEvaluation_noData() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    final List<ProcessFilterDto<?>> filterYieldingNoResults = ProcessFilterBuilder.filter()
      .duration()
      .operator(GREATER_THAN_EQUALS)
      .unit(DurationUnit.HOURS)
      .value(1L)
      .add()
      .buildList();
    reportData.setFilter(filterYieldingNoResults);
    final AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(0L);
    assertThat(result.getFirstMeasureData()).isEmpty();
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), durationInMilliseconds);
    // create and start another process
    deployAndStartSimpleServiceTaskProcess();
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    assertThat(result.getFirstMeasureData())
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(Tuple.tuple(
        createDurationBucketKey(durationInMilliseconds),
        getExpectedNumberOfModelElements()
      ));
  }

  @Test
  public void multipleProcessDefinitions() {
    // given
    final String key1 = "key1";
    final String key2 = "key2";
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition1 = deploySimpleOneUserTasksDefinition(key1);
    startProcessInstanceCompleteTaskAndModifyDuration(definition1.getId(), durationInMilliseconds);
    final ProcessDefinitionEngineDto definition2 =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSingleUserTaskDiagram(key2, USER_TASK_2));
    startProcessInstanceCompleteTaskAndModifyDuration(definition2.getId(), durationInMilliseconds);

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition1.getKey(), definition1.getVersionAsString());
    reportData.getDefinitions().add(createReportDataDefinitionDto(key2));
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    assertThat(result.getFirstMeasureData())
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsExactly(Tuple.tuple(
        createDurationBucketKey(durationInMilliseconds),
        getExpectedNumberOfModelElements(2)
      ));
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleUserTaskTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void multipleProcessInstances() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), durationInMilliseconds);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), durationInMilliseconds);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .hasSize(1)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .contains(
        Tuple.tuple(createDurationBucketKey(durationInMilliseconds), getExpectedNumberOfModelElements(2))
      );
  }

  @Test
  public void multipleProcessInstances_runningInstanceDurationIsCalculated() {
    // given
    final int completedActivityInstanceDuration = 1000;
    final OffsetDateTime startTime = DateCreationFreezer.dateFreezer(OffsetDateTime.now()).freezeDateAndReturn();
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), completedActivityInstanceDuration);
    final ProcessInstanceEngineDto runningProcessInstance =
      engineIntegrationExtension.startProcessInstance(definition.getId());
    engineDatabaseExtension.changeAllFlowNodeTotalDurations(
      runningProcessInstance.getId(), completedActivityInstanceDuration
    );
    changeRunningInstanceReferenceDate(runningProcessInstance, startTime);
    importAllEngineEntitiesFromScratch();

    // when
    final OffsetDateTime currentTime = DateCreationFreezer.dateFreezer(startTime.plusSeconds(5)).freezeDateAndReturn();
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData())
      // we expect buckets from 1000ms (finished instance) to 5000ms (running instance in relation to currentTime)
      // in intervals of 100ms (interval is rounded up to nearest power of 10)
      .hasSize(41)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .contains(
        Tuple.tuple(createDurationBucketKey((int) Duration.between(startTime, currentTime).toMillis()), 1.0D)
      );
  }

  @Test
  public void multipleProcessInstances_withoutTimeFreeze_runningInstanceMaxIsInTheResult() {
    // given running instance that makes up the max duration and no time freezing applied
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    // running instance starts first so it has the highest duration
    engineIntegrationExtension.startProcessInstance(definition.getId());
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1L);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then the result should be complete even though the duration increased
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getInstanceCount()).isEqualTo(2L);
    assertThat(resultDto.getInstanceCountWithoutFilters()).isEqualTo(2L);
    assertThat(resultDto.getFirstMeasureData())
      .extracting(MapResultEntryDto::getValue)
      .contains(1.0, 1.0);
  }

  @Test
  public void multipleBuckets_singleValueBucket() {
    // given
    final int minDurationInMillis = 1000;
    final int maxDurationInMillis = minDurationInMillis + NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1;
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), minDurationInMillis);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), maxDurationInMillis);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .isNotNull()
      // if the data range fits into the default max bucket number of 80, we should see a bucket for each value
      .hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION)
      .isSortedAccordingTo(Comparator.comparing(byDurationEntry -> Double.valueOf(byDurationEntry.getKey())))
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .contains(
        Tuple.tuple(createDurationBucketKey(minDurationInMillis), getExpectedNumberOfModelElements()),
        Tuple.tuple(createDurationBucketKey(maxDurationInMillis), getExpectedNumberOfModelElements())
      );
  }

  @Test
  public void multipleBuckets_automaticRangeBucketsBaseOf10Start() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1401);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 2004);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .isNotNull()
      // buckets from 1000ms (nearest lower power of 10 to min value) to 2000ms (start and end inclusive)
      // in intervals of 100ms (nearest power of 10 interval for this range)
      .hasSize(11)
      .isSortedAccordingTo(Comparator.comparing(byDurationEntry -> Double.valueOf(byDurationEntry.getKey())))
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .contains(
        Tuple.tuple(createDurationBucketKey(1400), getExpectedNumberOfModelElements()),
        Tuple.tuple(createDurationBucketKey(2000), getExpectedNumberOfModelElements())
      );
  }

  @Test
  public void multipleBuckets_customBuckets() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 100);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 200);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 300);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    reportData.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(10.0D)
        .bucketSize(100.0D)
        .build()
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .isNotNull()
      .hasSize(3)
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .contains(
        Tuple.tuple(createDurationBucketKey(10), getExpectedNumberOfModelElements()),
        Tuple.tuple(createDurationBucketKey(110), getExpectedNumberOfModelElements()),
        Tuple.tuple(createDurationBucketKey(210), getExpectedNumberOfModelElements())
      );
  }

  private static Stream<Arguments> getCustomBucketUnitScenarios() {
    return Stream.of(
      Arguments.of(BucketUnit.MILLISECOND, Duration.ofMillis(1)),
      Arguments.of(BucketUnit.SECOND, Duration.ofSeconds(1)),
      Arguments.of(BucketUnit.MINUTE, Duration.ofMinutes(1)),
      Arguments.of(BucketUnit.HOUR, Duration.ofHours(1)),
      Arguments.of(BucketUnit.DAY, Duration.ofDays(1)),
      Arguments.of(BucketUnit.WEEK, ChronoUnit.WEEKS.getDuration()),
      Arguments.of(BucketUnit.MONTH, ChronoUnit.MONTHS.getDuration()),
      Arguments.of(BucketUnit.YEAR, ChronoUnit.YEARS.getDuration())
    );
  }

  @ParameterizedTest
  @MethodSource("getCustomBucketUnitScenarios")
  public void multipleBuckets_customBuckets_customBucketUnit(final BucketUnit unit,
                                                             final Duration baseDuration) {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), baseDuration.toMillis());
    startProcessInstanceCompleteTaskAndModifyDuration(
      definition.getId(),
      baseDuration.multipliedBy(2).toMillis()
    );
    startProcessInstanceCompleteTaskAndModifyDuration(
      definition.getId(),
      baseDuration.multipliedBy(3).toMillis()
    );
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    reportData.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(1.0D)
        .baselineUnit(unit)
        .bucketSize(1.0D)
        .bucketSizeUnit(unit)
        .build()
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .isNotNull()
      .hasSize(3)
      .extracting(MapResultEntryDto::getKey)
      .containsExactlyElementsOf(
        ImmutableList.of(1, 2, 3).stream()
          .map(bucketInUnit -> baseDuration.multipliedBy(bucketInUnit).toMillis())
          .map(this::createDurationBucketKey)
          .collect(Collectors.toList())
      );
  }

  @Test
  public void multipleBuckets_customBuckets_baseLine_biggerThanMax_returnsEmptyResult() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 100);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 200);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 300);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    reportData.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(1000.0D)
        .bucketSize(100.0D)
        .build()
    );
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData()).isNotNull().isEmpty();
  }

  @Test
  public void multipleBuckets_defaultSorting() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 900);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1000);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1000);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 1100);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 2000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .hasSize(20)
      .isSortedAccordingTo(Comparator.comparing(byDurationEntry -> Double.valueOf(byDurationEntry.getKey())))
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .contains(
        Tuple.tuple(createDurationBucketKey(900), getExpectedNumberOfModelElements()),
        Tuple.tuple(createDurationBucketKey(1000), getExpectedNumberOfModelElements(2)),
        Tuple.tuple(createDurationBucketKey(1100), getExpectedNumberOfModelElements()),
        Tuple.tuple(createDurationBucketKey(2000), getExpectedNumberOfModelElements())
      );
  }

  @Test
  public void multipleBuckets_customKeySorting() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 10);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 11);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_KEY, SortOrder.DESC));
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .hasSize(2)
      .isSortedAccordingTo(
        Comparator.<MapResultEntryDto, Double>comparing(byDurationEntry -> Double.valueOf(byDurationEntry.getKey()))
          .reversed()
      )
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsSequence(
        Tuple.tuple(createDurationBucketKey(11), getExpectedNumberOfModelElements()),
        Tuple.tuple(createDurationBucketKey(10), getExpectedNumberOfModelElements())
      );
  }

  @Test
  public void multipleBuckets_valueSorting() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleOneUserTasksDefinition();
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 10);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 10);
    startProcessInstanceCompleteTaskAndModifyDuration(definition.getId(), 11);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    reportData.getConfiguration().setSorting(new ReportSortingDto(SORT_BY_VALUE, SortOrder.DESC));
    AuthorizedProcessReportEvaluationResponseDto<List<MapResultEntryDto>> evaluationResponse =
      reportClient.evaluateMapReport(reportData);

    // then
    final ReportResultResponseDto<List<MapResultEntryDto>> resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getFirstMeasureData())
      .hasSize(2)
      .isSortedAccordingTo(Comparator.comparing(MapResultEntryDto::getValue).reversed())
      .extracting(MapResultEntryDto::getKey, MapResultEntryDto::getValue)
      .containsSequence(
        Tuple.tuple(createDurationBucketKey(10), getExpectedNumberOfModelElements(2)),
        Tuple.tuple(createDurationBucketKey(11), getExpectedNumberOfModelElements())
      );
  }

  protected String createDurationBucketKey(final long durationInMs) {
    return Double.valueOf(durationInMs).toString();
  }

  protected double getExpectedNumberOfModelElements(final int instanceCount) {
    return getNumberOfModelElementsPerInstance() * instanceCount;
  }

  protected double getExpectedNumberOfModelElements() {
    return getExpectedNumberOfModelElements(1);
  }

  protected String createAndStoreDefaultReportDefinition(String processDefinitionKey,
                                                         String processDefinitionVersion) {
    final ProcessReportDataDto reportData = createReport(processDefinitionKey, processDefinitionVersion);
    return createNewReport(reportData);
  }
}
