/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;

public class CombinedDateReportIT extends AbstractProcessDefinitionIT {

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticIntervalReportCombinationsPerUnit")
  public void staticIntervals_sameResultsAsSingleReportEvaluation(
    final Pair<GroupByDateUnit, List<SingleProcessReportDefinitionDto>> combinableReportsWithUnit) {
    // given
    startAndEndProcessInstancesWithGivenRuntime(
      4,
      mapToChronoUnit(combinableReportsWithUnit.getKey()).getDuration(),
      OffsetDateTime.now().withSecond(10)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<String> reportIds = combinableReportsWithUnit.getValue().stream()
      .map(this::createNewSingleReport)
      .collect(toList());
    final ReportMapResultDto[] singleReportResults = reportIds
      .stream()
      .map(reportId -> reportClient.evaluateMapReportById(reportId).getResult())
      .toArray(ReportMapResultDto[]::new);
    final CombinedProcessReportResultDataDto<SingleReportResultDto> combinedResult =
      getCombinedReportResultFromReportIds(reportIds);

    // then the combined combinedResult evaluation yields the same results as the single report evaluations
    assertThat(combinedResult.getData()).isNotNull();
    assertThat(combinedResult.getData().values())
      .extracting(AuthorizedEvaluationResultDto::getResult)
      .containsExactlyInAnyOrder(singleReportResults);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("automaticIntervalReportCombinations")
  public void automaticInterval_combinedReportsHaveSameBucketRanges(
    final List<SingleProcessReportDefinitionDto> singleReports) {
    // given
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.now().withSecond(10);
    startAndEndProcessInstancesWithGivenRuntime(
      4,
      Duration.of(60, ChronoUnit.SECONDS),
      startOfFirstInstance
    );
    importAllEngineEntitiesFromScratch();

    // when
    final CombinedProcessReportResultDataDto<SingleReportResultDto> combinedResult =
      getCombinedReportResult(singleReports);

    // then both reports have the same buckets
    assertThat(combinedResult.getData()).isNotNull();
    assertSameBucketKeys(combinedResult);
  }

  private void assertSameBucketKeys(final CombinedProcessReportResultDataDto<SingleReportResultDto> result) {
    List<AuthorizedProcessReportEvaluationResultDto<SingleReportResultDto>> singleReportResults =
      new ArrayList<>(result.getData().values());
    List<String> bucketKeys1 = ((ReportMapResultDto) singleReportResults.get(0).getResult())
      .getData()
      .stream()
      .map(MapResultEntryDto::getKey).collect(toList());
    List<String> bucketKeys2 = ((ReportMapResultDto) singleReportResults.get(1).getResult())
      .getData()
      .stream()
      .map(MapResultEntryDto::getKey).collect(toList());
    assertThat(bucketKeys1).isEqualTo(bucketKeys2);
  }

  private static Stream<Pair<GroupByDateUnit, List<SingleProcessReportDefinitionDto>>> staticIntervalReportCombinationsPerUnit() {
    return staticGroupByDateUnits().flatMap(unit -> {
      final SingleProcessReportDefinitionDto runningDateReport = createReport(
        COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE,
        unit
      );

      final SingleProcessReportDefinitionDto startDateReport = createReport(
        COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE,
        unit
      );

      final SingleProcessReportDefinitionDto endDateReport = createReport(
        COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE,
        unit
      );

      return Arrays.asList(
        Pair.of(unit, Arrays.asList(runningDateReport, startDateReport)),
        Pair.of(unit, Arrays.asList(runningDateReport, endDateReport))
      ).stream();
    });
  }

  private static Stream<List<SingleProcessReportDefinitionDto>> automaticIntervalReportCombinations() {
    final SingleProcessReportDefinitionDto runningDateReport = createReport(
      COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE,
      GroupByDateUnit.AUTOMATIC
    );

    final SingleProcessReportDefinitionDto startDateReport = createReport(
      COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE,
      GroupByDateUnit.AUTOMATIC
    );

    final SingleProcessReportDefinitionDto endDateReport = createReport(
      COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE,
      GroupByDateUnit.AUTOMATIC
    );

    final SingleProcessReportDefinitionDto emptyRunningDateReport = createReport(
      COUNT_PROC_INST_FREQ_GROUP_BY_RUNNING_DATE,
      GroupByDateUnit.AUTOMATIC,
      createSuspendedInstancesOnlyFilter()
    );

    final SingleProcessReportDefinitionDto emptyStartDateReport = createReport(
      COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE,
      GroupByDateUnit.AUTOMATIC,
      createSuspendedInstancesOnlyFilter()
    );

    return Stream.of(
      Arrays.asList(runningDateReport, startDateReport),
      Arrays.asList(runningDateReport, endDateReport),
      Arrays.asList(emptyRunningDateReport, emptyStartDateReport),
      Arrays.asList(runningDateReport, emptyStartDateReport)
    );
  }

  private CombinedProcessReportResultDataDto<SingleReportResultDto> getCombinedReportResult(
    final List<SingleProcessReportDefinitionDto> reports) {
    return getCombinedReportResultFromReportIds(
      reports.stream()
        .map(this::createNewSingleReport)
        .collect(toList())
    );
  }

  private CombinedProcessReportResultDataDto<SingleReportResultDto> getCombinedReportResultFromReportIds(
    final List<String> reportIds) {
    final List<CombinedReportItemDto> reportItems = reportIds.stream()
      .map(CombinedReportItemDto::new)
      .collect(toList());

    final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    combinedReportData.setReports(reportItems);
    final CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);

    final IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    return reportClient.evaluateCombinedReportById(response.getId()).getResult();
  }

  private String createNewSingleReport(SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private static SingleProcessReportDefinitionDto createReport(final ProcessReportDataType reportDataType,
                                                               final GroupByDateUnit unit) {
    return createReport(reportDataType, unit, Collections.emptyList());
  }

  private static SingleProcessReportDefinitionDto createReport(final ProcessReportDataType reportDataType,
                                                               final GroupByDateUnit unit,
                                                               final List<ProcessFilterDto<?>> filters) {
    SingleProcessReportDefinitionDto reportDefinitionDto = new SingleProcessReportDefinitionDto();
    ProcessReportDataDto runningReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_PROCESS)
      .setProcessDefinitionVersion("1")
      .setDateInterval(unit)
      .setReportDataType(reportDataType)
      .build();
    runningReportData.setFilter(filters);
    reportDefinitionDto.setData(runningReportData);
    return reportDefinitionDto;
  }

  private static List<ProcessFilterDto<?>> createSuspendedInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().suspendedInstancesOnly().add().buildList();
  }

}
