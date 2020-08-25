/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.flownode.frequency.groupby.flownode.duration;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedBy;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.BucketUnit;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DurationFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.service.es.report.util.HyperMapAsserter;
import org.camunda.optimize.test.util.DateCreationFreezer;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
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
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.GREATER_THAN_EQUALS;
import static org.camunda.optimize.test.util.ProcessReportDataType.FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public class CountFlowNodeFrequencyByFlowNodeDurationDistributeByFlowNodeIT extends AbstractProcessDefinitionIT {

  @Test
  public void simpleReportEvaluation() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), durationInMilliseconds);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(definition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(definition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy()).isEqualTo(DistributedBy.FLOW_NODE);

    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(createDurationBucketKey(durationInMilliseconds))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 1., START_EVENT)
      .distributedByContains(TEST_ACTIVITY, 1., TEST_ACTIVITY)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluationById() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), durationInMilliseconds);
    importAllEngineEntitiesFromScratch();

    final String reportId = createAndStoreDefaultReportDefinition(definition.getKey(), definition.getVersionAsString());

    // when
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReportById(reportId);

    // then
    ProcessReportDataDto resultReportDataDto = evaluationResponse.getReportDefinition().getData();
    assertThat(resultReportDataDto.getProcessDefinitionKey()).isEqualTo(definition.getKey());
    assertThat(resultReportDataDto.getDefinitionVersions()).contains(definition.getVersionAsString());
    assertThat(resultReportDataDto.getView()).isNotNull();
    assertThat(resultReportDataDto.getView().getEntity()).isEqualTo(ProcessViewEntity.FLOW_NODE);
    assertThat(resultReportDataDto.getView().getProperty()).isEqualTo(ProcessViewProperty.FREQUENCY);
    assertThat(resultReportDataDto.getGroupBy().getType()).isEqualTo(ProcessGroupByType.DURATION);
    assertThat(resultReportDataDto.getConfiguration().getDistributedBy()).isEqualTo(DistributedBy.FLOW_NODE);

    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(createDurationBucketKey(durationInMilliseconds))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 1., START_EVENT)
      .distributedByContains(TEST_ACTIVITY, 1., TEST_ACTIVITY)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void simpleReportEvaluation_noData() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    final List<ProcessFilterDto<?>> filterYieldingNoResults = ProcessFilterBuilder.filter()
      .duration()
      .operator(GREATER_THAN_EQUALS)
      .unit(DurationFilterUnit.HOURS)
      .value(1L)
      .add()
      .buildList();
    reportData.setFilter(filterYieldingNoResults);
    final AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount()).isEqualTo(0L);
    assertThat(result.getIsComplete()).isTrue();
    assertThat(result.getData()).isEmpty();
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1000);
    // create and start another process
    deployAndStartSimpleServiceTaskProcess("other", "unexpectedActivity");
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto result = evaluationResponse.getResult();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(1L)
      .processInstanceCountWithoutFilters(1L)
      .groupByContains(createDurationBucketKey(durationInMilliseconds))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 1., START_EVENT)
      .distributedByContains(TEST_ACTIVITY, 1., TEST_ACTIVITY)
      .doAssert(result);
    // @formatter:on
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = newArrayList(tenantId1);
    final String processKey = deployAndStartMultiTenantSimpleServiceTaskProcess(
      newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(processKey, ALL_VERSIONS);
    reportData.setTenantIds(selectedTenants);
    ReportHyperMapResultDto result = reportClient.evaluateHyperMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void multipleProcessInstances() {
    // given
    final int durationInMilliseconds = 1000;
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), durationInMilliseconds);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), durationInMilliseconds);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(createDurationBucketKey(durationInMilliseconds))
      .distributedByContains(END_EVENT, 2., END_EVENT)
      .distributedByContains(START_EVENT, 2., START_EVENT)
      .distributedByContains(TEST_ACTIVITY, 2., TEST_ACTIVITY)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void multipleProcessInstances_runningInstanceDurationIsCalculated() {
    // given
    final int completedActivityInstanceDurations = 1000;
    final OffsetDateTime startTime = DateCreationFreezer.dateFreezer(OffsetDateTime.now()).freezeDateAndReturn();
    final ProcessInstanceEngineDto completedProcessInstance = deployAndStartSimpleUserTaskProcess();
    engineIntegrationExtension.completeUserTaskWithoutClaim(completedProcessInstance.getId());
    engineDatabaseExtension.changeActivityDuration(
      completedProcessInstance.getId(),
      completedActivityInstanceDurations
    );

    final ProcessInstanceEngineDto runningProcessInstance = engineIntegrationExtension
      .startProcessInstance(completedProcessInstance.getDefinitionId());
    engineDatabaseExtension.changeActivityDuration(runningProcessInstance.getId(), completedActivityInstanceDurations);
    engineDatabaseExtension.changeActivityInstanceStartDate(runningProcessInstance.getId(), USER_TASK_1, startTime);
    importAllEngineEntitiesFromScratch();

    // when
    final OffsetDateTime currentTime = DateCreationFreezer
      // just one more ms to ensure we only get back two buckets for easier assertion
      .dateFreezer(startTime.plus(completedActivityInstanceDurations + 1, ChronoUnit.MILLIS))
      .freezeDateAndReturn();
    final ProcessReportDataDto reportData = createReport(
      completedProcessInstance.getProcessDefinitionKey(), completedProcessInstance.getProcessDefinitionVersion()
    );
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    HyperMapAsserter.asserter()
      .processInstanceCount(2L)
      .processInstanceCountWithoutFilters(2L)
      .groupByContains(createDurationBucketKey(completedActivityInstanceDurations))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 2., START_EVENT)
      .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .groupByContains(createDurationBucketKey((int) Duration.between(startTime, currentTime).toMillis()))
      .distributedByContains(END_EVENT, null, END_EVENT)
      .distributedByContains(START_EVENT, null, START_EVENT)
      .distributedByContains(USER_TASK_1, 1., USER_TASK_1)
      .doAssert(resultDto);
    // @formatter:on
  }

  @Test
  public void multipleBuckets_singleValueBucket() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1000);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1079);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    assertThat(resultDto.getData())
      .isNotNull()
      // if the data range fits into the default max bucket number of 80, we should see a bucket for each value
      .hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION)
      .isSortedAccordingTo(Comparator.comparing(byDurationEntry -> Double.valueOf(byDurationEntry.getKey())))
      .extracting(HyperMapResultEntryDto::getKey)
      .contains(createDurationBucketKey(1000), createDurationBucketKey(1079));
  }

  @Test
  public void multipleBuckets_automaticRangeBucketsBaseOf10Start() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1401);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 2004);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    assertThat(resultDto.getData())
      // buckets from 1000ms (nearest lower power of 10 to min value) to 2000ms (start and end inclusive)
      // in intervals of 100ms (nearest power of 10 interval for this range)
      .hasSize(11)
      .isSortedAccordingTo(Comparator.comparing(byDurationEntry -> Double.valueOf(byDurationEntry.getKey())))
      .extracting(HyperMapResultEntryDto::getKey)
      .contains(createDurationBucketKey(1400), createDurationBucketKey(2000));
  }

  @Test
  public void multipleBuckets_customBuckets() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 100);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 200);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 300);
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
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    // @formatter:off
    HyperMapAsserter.asserter()
      .processInstanceCount(3L)
      .processInstanceCountWithoutFilters(3L)
      .groupByContains(createDurationBucketKey(10))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 1., START_EVENT)
      .distributedByContains(TEST_ACTIVITY, 1., TEST_ACTIVITY)
      .groupByContains(createDurationBucketKey(110))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 1., START_EVENT)
      .distributedByContains(TEST_ACTIVITY, 1., TEST_ACTIVITY)
      .groupByContains(createDurationBucketKey(210))
      .distributedByContains(END_EVENT, 1., END_EVENT)
      .distributedByContains(START_EVENT, 1., START_EVENT)
      .distributedByContains(TEST_ACTIVITY, 1., TEST_ACTIVITY)
      .doAssert(resultDto);
    // @formatter:on
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
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), baseDuration.toMillis());
    startProcessInstanceAndModifyActivityDuration(definition.getId(), baseDuration.multipliedBy(2).toMillis());
    startProcessInstanceAndModifyActivityDuration(definition.getId(), baseDuration.multipliedBy(3).toMillis());
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
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);
    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getData())
      .isNotNull()
      .hasSize(3)
      .extracting(HyperMapResultEntryDto::getKey)
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
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 100);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 200);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 300);
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
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    assertThat(resultDto.getData()).isNotNull().isEmpty();
  }

  @Test
  public void multipleBuckets_customBuckets_tooManyBuckets_returnsLimitedResult() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 2000);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 3000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    reportData.getConfiguration().setCustomBucket(
      CustomBucketDto.builder()
        .active(true)
        .baseline(0.0D)
        .bucketSize(1.0D)
        .build()
    );
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isFalse();
    assertThat(resultDto.getData())
      .isNotNull()
      .hasSize(1000)
      .extracting(HyperMapResultEntryDto::getKey)
      .contains(createDurationBucketKey(1))
      .doesNotContain(createDurationBucketKey(2000), createDurationBucketKey(3000));
  }

  @Test
  public void multipleBuckets_defaultSorting() {
    // given
    final ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcessAndGetDefinition();
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 900);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1000);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1000);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 1100);
    startProcessInstanceAndModifyActivityDuration(definition.getId(), 2000);
    importAllEngineEntitiesFromScratch();

    // when
    final ProcessReportDataDto reportData = createReport(definition.getKey(), definition.getVersionAsString());
    AuthorizedProcessReportEvaluationResultDto<ReportHyperMapResultDto> evaluationResponse =
      reportClient.evaluateHyperMapReport(reportData);

    // then
    final ReportHyperMapResultDto resultDto = evaluationResponse.getResult();
    assertThat(resultDto.getIsComplete()).isTrue();
    assertThat(resultDto.getData())
      .hasSize(20)
      .isSortedAccordingTo(Comparator.comparing(byDurationEntry -> Double.valueOf(byDurationEntry.getKey())));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey,
                                                       String processDefinitionVersion) {
    final ProcessReportDataDto reportData = createReport(processDefinitionKey, processDefinitionVersion);
    return createNewReport(reportData);
  }

  private ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(FLOW_NODE_FREQUENCY_GROUP_BY_FLOW_NODE_DURATION_BY_FLOW_NODE)
      .build();
  }

  private String createDurationBucketKey(final long durationInMs) {
    return Double.valueOf(durationInMs).toString();
  }
}
