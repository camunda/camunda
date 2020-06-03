/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.processinstance.frequency.date;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.hamcrest.CoreMatchers.is;

public class CountProcessInstanceFrequencyByProcessInstanceRunningDateReportEvaluationIT
  extends AbstractCountProcessInstanceFrequencyByProcessInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.RUNNING_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(final String processInstanceId,
                                           final OffsetDateTime newDate) throws SQLException {
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, newDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, newDate);
  }

  @Override
  protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> newIdToDates) throws SQLException {
    engineDatabaseExtension.changeProcessInstanceStartDates(newIdToDates);
    engineDatabaseExtension.changeProcessInstanceEndDates(newIdToDates);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void countRunningInstances_instancesFallIntoMultipleBuckets(final GroupByDateUnit unit) {
    // given
    // first instance starts within a bucket (as opposed to on the "edge" of a bucket)
    final OffsetDateTime startOfFirstInstance = OffsetDateTime
      .now()
      .withSecond(10);
    final Duration bucketWidth = mapToChronoUnit(unit).getDuration();
    final List<ProcessInstanceEngineDto> processInstanceDtos =
      startAndEndProcessInstancesWithGivenRuntime(2, bucketWidth, startOfFirstInstance);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    ProcessInstanceEngineDto instance = processInstanceDtos.get(0);
    ProcessReportDataDto reportData = getGroupByRunningDateReportData(
      instance.getProcessDefinitionKey(),
      instance.getProcessDefinitionVersion(),
      unit
    );
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final int expectedNumberOfBuckets = 3;
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(expectedNumberOfBuckets);

    // bucket keys exist for each unit between start date of first and end date of last instance
    final ZonedDateTime lastBucketStartDate = truncateToStartOfUnit(
      startOfFirstInstance.plus(bucketWidth.multipliedBy(expectedNumberOfBuckets - 1)),
      mapToChronoUnit(unit)
    ).toOffsetDateTime().withOffsetSameLocal(OffsetDateTime.now().getOffset()).toZonedDateTime();
    IntStream.range(0, expectedNumberOfBuckets)
      .forEach(i -> {
        final String expectedBucketKey =
          localDateTimeToString(truncateToStartOfUnit(
            lastBucketStartDate.toOffsetDateTime().minus(i, mapToChronoUnit(unit)),
            mapToChronoUnit(unit)
          ).toOffsetDateTime().withOffsetSameLocal(OffsetDateTime.now().getOffset()).toZonedDateTime());
        assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
      });

    // instances fall into correct buckets (overlapping in the 2nd bucket)
    assertThat(resultData.get(0).getValue()).isEqualTo(1);
    assertThat(resultData.get(1).getValue()).isEqualTo(2);
    assertThat(resultData.get(2).getValue()).isEqualTo(1);
  }

  @Override
  protected void assertStartDateResultMap(List<MapResultEntryDto> resultData,
                                          int size,
                                          OffsetDateTime now,
                                          ChronoUnit unit,
                                          long expectedValue) {
    MatcherAssert.assertThat(resultData.size(), is(size));
    final ZonedDateTime finalStartOfUnit = truncateToStartOfUnit(now, unit)
      .toOffsetDateTime()
      .withOffsetSameLocal(OffsetDateTime.now().getOffset())
      .toZonedDateTime();
    IntStream.range(0, size)
      .forEach(i -> {
        final String expectedDateString = localDateTimeToString(finalStartOfUnit.minus((i), unit));
        MatcherAssert.assertThat(resultData.get(i).getKey(), is(expectedDateString));
        MatcherAssert.assertThat(resultData.get(i).getValue(), is(expectedValue));
      });
  }

  private ProcessReportDataDto getGroupByRunningDateReportData(final String key,
                                                               final String version,
                                                               final GroupByDateUnit unit) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(key)
      .setProcessDefinitionVersion(version)
      .setDateInterval(unit)
      .setReportDataType(getTestReportDataType())
      .build();
  }
}
