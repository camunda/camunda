/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.date.distributedby.none;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;

public class ProcessInstanceFrequencyByProcessInstanceRunningDateReportEvaluationIT
  extends AbstractProcessInstanceFrequencyByProcessInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_RUNNING_DATE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.RUNNING_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(final String processInstanceId,
                                           final OffsetDateTime newDate) {
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, newDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, newDate);
  }

  @Override
  protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> newIdToDates) {
    engineDatabaseExtension.changeProcessInstanceStartDates(newIdToDates);
    engineDatabaseExtension.changeProcessInstanceEndDates(newIdToDates);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void countRunningInstances_instancesFallIntoMultipleBuckets(final AggregateByDateUnit unit) {
    // given
    // first instance starts within a bucket (as opposed to on the "edge" of a bucket)
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.parse("2020-06-15T12:00:00+02:00").withSecond(10);
    final Duration bucketWidth = mapToChronoUnit(unit).getDuration();
    final List<ProcessInstanceEngineDto> processInstanceDtos =
      startAndEndProcessInstancesWithGivenRuntime(2, bucketWidth, startOfFirstInstance);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessInstanceEngineDto instance = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      instance.getProcessDefinitionKey(),
      instance.getProcessDefinitionVersion(),
      unit
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final int expectedNumberOfBuckets = 3;
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData)
      .isNotNull()
      .hasSize(expectedNumberOfBuckets);

    // bucket keys exist for each unit between start date of first and end date of last instance
    final ZonedDateTime lastBucketStartDate = truncateToStartOfUnit(
      startOfFirstInstance.plus(bucketWidth.multipliedBy(expectedNumberOfBuckets - 1)),
      mapToChronoUnit(unit)
    );
    IntStream.range(0, expectedNumberOfBuckets)
      .forEach(i -> {
        final String expectedBucketKey = convertToExpectedBucketKey(
          startOfFirstInstance.plus(i, mapToChronoUnit(unit)),
          unit
        );
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
      });

    // instances fall into correct buckets (overlapping in the 2nd bucket)
    assertThat(resultData.get(0).getValue()).isEqualTo(1.);
    assertThat(resultData.get(1).getValue()).isEqualTo(2.);
    assertThat(resultData.get(2).getValue()).isEqualTo(1.);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void countRunningInstances_runningInstancesOnly(final AggregateByDateUnit unit) {
    // given two running instances
    final Duration bucketWidth = mapToChronoUnit(unit).getDuration();
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.parse("2020-01-05T12:00:00+02:00");
    final OffsetDateTime startOfSecondInstance = OffsetDateTime.parse("2020-01-05T12:00:00+02:00").plus(bucketWidth);

    ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), startOfFirstInstance);
    engineDatabaseExtension.changeProcessInstanceStartDate(instance2.getId(), startOfSecondInstance);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      processDefinition.getKey(),
      processDefinition.getVersionAsString(),
      unit
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then the bucket range is based on earliest and latest start date
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();

    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(2);
    assertThat(resultData.get(0).getKey()).isEqualTo(convertToExpectedBucketKey(startOfFirstInstance, unit));
    assertThat(resultData.get(1).getKey()).isEqualTo(convertToExpectedBucketKey(startOfSecondInstance, unit));
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void countRunningInstances_latestStartDateAfterLatestEndDate(final AggregateByDateUnit unit) {
    // given instances whose latest start date is after the latest end date
    final Duration bucketWidth = mapToChronoUnit(unit).getDuration();
    final OffsetDateTime earliestStartDate = OffsetDateTime.parse("2020-01-05T12:00:10+02:00");
    final OffsetDateTime latestStartDate = OffsetDateTime.parse("2020-01-05T12:00:10+02:00")
      .plus(bucketWidth.multipliedBy(3));
    ProcessDefinitionEngineDto processDefinition = deployUserTaskProcess();
    ProcessInstanceEngineDto instance1 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto instance2 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    ProcessInstanceEngineDto instance3 = engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), earliestStartDate);
    engineDatabaseExtension.changeProcessInstanceStartAndEndDate(
      instance2.getId(),
      earliestStartDate,
      earliestStartDate.plus(bucketWidth)
    );
    engineDatabaseExtension.changeProcessInstanceStartDate(instance3.getId(), latestStartDate);
    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      processDefinition.getKey(),
      processDefinition.getVersionAsString(),
      unit
    );
    ReportResultResponseDto<List<MapResultEntryDto>> result = reportClient.evaluateMapReport(reportData).getResult();

    // then the bucket range is earliest to latest start date
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();

    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(4);
    assertThat(resultData.get(0).getKey()).isEqualTo(convertToExpectedBucketKey(earliestStartDate, unit));
    assertThat(resultData.get(3).getKey()).isEqualTo(convertToExpectedBucketKey(latestStartDate, unit));
  }

  @Override
  protected void assertStartDateResultMap(List<MapResultEntryDto> resultData,
                                          int size,
                                          OffsetDateTime now,
                                          ChronoUnit unit,
                                          Double expectedValue) {
    assertThat(resultData).hasSize(size);
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit);
    IntStream.range(0, size)
      .forEach(i -> {
        final String expectedDateString = localDateTimeToString(finalStartOfUnit.minus((i), unit));
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedDateString);
        assertThat(resultData.get(i).getValue()).isEqualTo(expectedValue);
      });
  }

  private ProcessReportDataDto getGroupByRunningDateReportData(final String key,
                                                               final String version,
                                                               final AggregateByDateUnit unit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(version)
      .setGroupByDateInterval(unit)
      .setReportDataType(getTestReportDataType())
      .build();
  }

  private String convertToExpectedBucketKey(final OffsetDateTime date, final AggregateByDateUnit unit) {
    return localDateTimeToString(
      truncateToStartOfUnit(date, mapToChronoUnit(unit))
    );
  }

  private ProcessDefinitionEngineDto deployUserTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }
}
