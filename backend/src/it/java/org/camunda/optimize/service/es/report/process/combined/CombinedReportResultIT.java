/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedSingleReportEvaluationResponseDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnitMapper.mapToChronoUnit;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_RUNNING_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE;

public class CombinedReportResultIT extends AbstractProcessDefinitionIT {

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("staticIntervalDateReportCombinationsPerUnit")
  public void dateReports_staticIntervals_sameResultsAsSingleReportEvaluation(
    final Pair<AggregateByDateUnit, List<SingleProcessReportDefinitionRequestDto>> combinableReportsWithUnit) {
    // given
    startAndEndProcessInstancesWithGivenRuntime(
      4,
      mapToChronoUnit(combinableReportsWithUnit.getKey()).getDuration(),
      OffsetDateTime.now().withSecond(10)
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<String> reportIds = combinableReportsWithUnit.getValue().stream()
      .map(reportClient::createSingleProcessReport)
      .collect(toList());
    final List<ReportResultResponseDto<List<MapResultEntryDto>>> singleReportResults = reportIds
      .stream()
      .map(reportId -> reportClient.evaluateMapReportById(reportId).getResult())
      .collect(Collectors.toList());
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(reportIds);

    // then the combined combinedResult evaluation yields the same results as the single report evaluations
    assertThat(combinedResult.getData()).isNotNull();
    assertThat(combinedResult.getData().values())
      .extracting(AuthorizedSingleReportEvaluationResponseDto::getResult)
      .containsExactlyInAnyOrderElementsOf(singleReportResults);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("automaticIntervalDateReportCombinations")
  public void dateReports_automaticInterval_combinedReportsHaveSameBucketRanges(
    final List<SingleProcessReportDefinitionRequestDto> singleReports) {
    // given
    final OffsetDateTime startOfFirstInstance = OffsetDateTime.now().withSecond(10);
    startAndEndProcessInstancesWithGivenRuntime(
      4,
      Duration.of(60, ChronoUnit.SECONDS),
      startOfFirstInstance
    );
    importAllEngineEntitiesFromScratch();

    // when
    final CombinedProcessReportResultDataDto<?> combinedResult =
      getCombinedReportResult(singleReports);

    // then both reports have the same buckets
    assertThat(combinedResult.getData()).isNotNull();
    assertSameBucketKeys(combinedResult);
  }

  @ParameterizedTest
  @MethodSource("staticAggregateByDateUnits")
  public void dateVariableReports_staticIntervals_sameResultsAsSingleReportEvaluation(final AggregateByDateUnit unit) {
    // given
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();
    Map<String, Object> variables = new HashMap<>();
    OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plus(1, chronoUnit);
      variables.put(dateVarName, dateVariableValue);
      engineIntegrationExtension.startProcessInstance(def.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData1 = createDateVariableReport(
      def.getKey(),
      def.getVersionAsString()
    );
    reportData1.getConfiguration().setGroupByDateVariableUnit(unit);
    ProcessReportDataDto reportData2 = createDateVariableReport(
      def.getKey(),
      def.getVersionAsString()
    );
    reportData2.getConfiguration().setGroupByDateVariableUnit(unit);

    List<SingleProcessReportDefinitionRequestDto> reportDefs = Arrays.asList(
      new SingleProcessReportDefinitionRequestDto(reportData1),
      new SingleProcessReportDefinitionRequestDto(reportData2)
    );

    final List<String> reportIds = reportDefs.stream()
      .map(reportClient::createSingleProcessReport)
      .collect(toList());
    final List<ReportResultResponseDto<List<MapResultEntryDto>>> singleReportResults = reportIds
      .stream()
      .map(reportId -> reportClient.evaluateMapReportById(reportId).getResult())
      .collect(Collectors.toList());
    final CombinedProcessReportResultDataDto<List<MapResultEntryDto>> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(reportIds);

    // then the combined combinedResult evaluation yields the same results as the single report evaluations
    assertThat(combinedResult.getData()).isNotNull();
    assertThat(combinedResult.getData().values())
      .extracting(AuthorizedSingleReportEvaluationResponseDto::getResult)
      .containsExactlyInAnyOrderElementsOf(singleReportResults);
  }

  @Test
  public void dateVariableReports_automaticInterval_combinedReportsHaveSameBucketRanges() {
    // given
    final int numberOfInstances = 3;
    final String dateVarName = "dateVar";
    final ProcessDefinitionEngineDto def = deploySimpleServiceTaskProcessAndGetDefinition();
    Map<String, Object> variables = new HashMap<>();
    OffsetDateTime dateVariableValue = OffsetDateTime.parse("2020-06-15T00:00:00+02:00");

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plus(60, ChronoUnit.SECONDS);
      variables.put(dateVarName, dateVariableValue);
      engineIntegrationExtension.startProcessInstance(def.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData1 = createDateVariableReport(
      def.getKey(),
      def.getVersionAsString()
    );
    reportData1.getConfiguration().setGroupByDateVariableUnit(AggregateByDateUnit.AUTOMATIC);
    ProcessReportDataDto reportData2 = createDateVariableReport(
      def.getKey(),
      def.getVersionAsString()
    );
    reportData2.getConfiguration().setGroupByDateVariableUnit(AggregateByDateUnit.AUTOMATIC);

    List<SingleProcessReportDefinitionRequestDto> reportDefs = Arrays.asList(
      new SingleProcessReportDefinitionRequestDto(reportData1),
      new SingleProcessReportDefinitionRequestDto(reportData2)
    );

    // when
    final CombinedProcessReportResultDataDto<?> combinedResult =
      getCombinedReportResult(reportDefs);

    // then both reports have the same buckets
    assertThat(combinedResult.getData()).isNotNull();
    assertSameBucketKeys(combinedResult);
  }

  @SneakyThrows
  @Test
  public void correctCombinedInstanceCount_differentProcessDefinitions() {
    // given report for first definition
    final SingleProcessReportDefinitionRequestDto singleReport1 = createReport(
      PROC_INST_FREQ_GROUP_BY_END_DATE,
      AggregateByDateUnit.DAY,
      createNonSuspendedInstancesOnlyFilter()
    );
    startAndEndProcessInstancesWithGivenRuntime(
      4,
      mapToChronoUnit(AggregateByDateUnit.DAY).getDuration(),
      OffsetDateTime.now()
    );

    // and report for second definition (with no instances in it)
    final SingleProcessReportDefinitionRequestDto singleReport2 = createReport(
      PROC_INST_FREQ_GROUP_BY_END_DATE,
      AggregateByDateUnit.DAY
    );
    singleReport2.getData().setProcessDefinitionKey("runningInstanceDef");
    ProcessDefinitionEngineDto runningInstanceDef = deploySimpleOneUserTasksDefinition("runningInstanceDef", null);
    engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());

    // and a report for a second definition
    final SingleProcessReportDefinitionRequestDto singleReport3 = createReport(
      PROC_INST_FREQ_GROUP_BY_START_DATE,
      AggregateByDateUnit.DAY
    );
    singleReport3.getData().setProcessDefinitionKey("otherDef");
    ProcessDefinitionEngineDto otherDef = deploySimpleOneUserTasksDefinition("otherDef", null);
    engineIntegrationExtension.startProcessInstance(otherDef.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final List<String> reportIds = Arrays.asList(singleReport1, singleReport2, singleReport3)
      .stream()
      .map(reportClient::createSingleProcessReport)
      .collect(toList());
    final CombinedProcessReportResultDataDto<?> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(reportIds);

    // then
    assertThat(combinedResult.getInstanceCount()).isEqualTo(5);
  }

  @SneakyThrows
  @Test
  public void correctCombinedInstanceCount_sameDefinition_distinctSingleReportInstances() {
    // given
    final SingleProcessReportDefinitionRequestDto singleReport1 = createReport(
      PROC_INST_FREQ_GROUP_BY_START_DATE,
      AggregateByDateUnit.DAY,
      createNonSuspendedInstancesOnlyFilter()
    );
    final SingleProcessReportDefinitionRequestDto singleReport2 = createReport(
      PROC_INST_FREQ_GROUP_BY_END_DATE,
      AggregateByDateUnit.DAY,
      createSuspendedInstancesOnlyFilter()
    );
    List<ProcessInstanceEngineDto> instances = startAndEndProcessInstancesWithGivenRuntime(
      4,
      mapToChronoUnit(AggregateByDateUnit.DAY).getDuration(),
      OffsetDateTime.now()
    );
    engineDatabaseExtension.changeProcessInstanceState(
      instances.get(0).getId(),
      SUSPENDED_STATE
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<String> reportIds = Arrays.asList(singleReport1, singleReport2)
      .stream()
      .map(reportClient::createSingleProcessReport)
      .collect(toList());
    final CombinedProcessReportResultDataDto<?> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(reportIds);

    // then
    assertThat(combinedResult.getInstanceCount()).isEqualTo(4);
  }

  @SneakyThrows
  @Test
  public void correctCombinedInstanceCount_sameDefinition_overlappingSingleReportInstances() {
    // given
    final SingleProcessReportDefinitionRequestDto singleReport1 = createReport(
      PROC_INST_FREQ_GROUP_BY_START_DATE,
      AggregateByDateUnit.DAY
    );
    final SingleProcessReportDefinitionRequestDto singleReport2 = createReport(
      PROC_INST_FREQ_GROUP_BY_END_DATE,
      AggregateByDateUnit.DAY,
      createSuspendedInstancesOnlyFilter()
    );
    List<ProcessInstanceEngineDto> instances = startAndEndProcessInstancesWithGivenRuntime(
      4,
      mapToChronoUnit(AggregateByDateUnit.DAY).getDuration(),
      OffsetDateTime.now()
    );
    engineDatabaseExtension.changeProcessInstanceState(
      instances.get(0).getId(),
      SUSPENDED_STATE
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<String> reportIds = Arrays.asList(singleReport1, singleReport2)
      .stream()
      .map(reportClient::createSingleProcessReport)
      .collect(toList());

    final CombinedProcessReportResultDataDto<?> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(reportIds);

    // then
    assertThat(combinedResult.getInstanceCount()).isEqualTo(4);
  }

  @SneakyThrows
  @Test
  public void correctCombinedInstanceCount_emptySingleReportInstanceCounts() {
    // given
    final SingleProcessReportDefinitionRequestDto singleReport1 = createReport(
      PROC_INST_FREQ_GROUP_BY_START_DATE,
      AggregateByDateUnit.DAY,
      createSuspendedInstancesOnlyFilter()
    );
    final SingleProcessReportDefinitionRequestDto singleReport2 = createReport(
      PROC_INST_FREQ_GROUP_BY_END_DATE,
      AggregateByDateUnit.DAY,
      createSuspendedInstancesOnlyFilter()
    );
    startAndEndProcessInstancesWithGivenRuntime(
      4,
      mapToChronoUnit(AggregateByDateUnit.DAY).getDuration(),
      OffsetDateTime.now()
    );
    importAllEngineEntitiesFromScratch();

    // when
    final List<String> reportIds = Arrays.asList(singleReport1, singleReport2)
      .stream()
      .map(reportClient::createSingleProcessReport)
      .collect(toList());
    final CombinedProcessReportResultDataDto<?> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(reportIds);

    // then
    assertThat(combinedResult.getInstanceCount()).isEqualTo(0);
  }

  private void assertSameBucketKeys(final CombinedProcessReportResultDataDto<?> result) {
    List<AuthorizedProcessReportEvaluationResponseDto<?>> singleReportResults =
      new ArrayList<>(result.getData().values());
    List<String> bucketKeys1 = ((List<MapResultEntryDto>) singleReportResults.get(0).getResult().getFirstMeasureData())
      .stream()
      .map(MapResultEntryDto::getKey).collect(toList());
    List<String> bucketKeys2 = ((List<MapResultEntryDto>) singleReportResults.get(1).getResult().getFirstMeasureData())
      .stream()
      .map(MapResultEntryDto::getKey).collect(toList());
    assertThat(bucketKeys1).isEqualTo(bucketKeys2);
  }

  private static Stream<Pair<AggregateByDateUnit, List<SingleProcessReportDefinitionRequestDto>>> staticIntervalDateReportCombinationsPerUnit() {
    return staticAggregateByDateUnits().flatMap(unit -> {
      final SingleProcessReportDefinitionRequestDto runningDateReport = createReport(
        PROC_INST_FREQ_GROUP_BY_RUNNING_DATE,
        unit
      );

      final SingleProcessReportDefinitionRequestDto startDateReport = createReport(
        PROC_INST_FREQ_GROUP_BY_START_DATE,
        unit
      );

      final SingleProcessReportDefinitionRequestDto endDateReport = createReport(
        PROC_INST_FREQ_GROUP_BY_END_DATE,
        unit
      );

      return Arrays.asList(
        Pair.of(unit, Arrays.asList(runningDateReport, startDateReport)),
        Pair.of(unit, Arrays.asList(runningDateReport, endDateReport)),
        Pair.of(unit, Arrays.asList(startDateReport, endDateReport))
      ).stream();
    });
  }

  private static Stream<List<SingleProcessReportDefinitionRequestDto>> automaticIntervalDateReportCombinations() {
    final SingleProcessReportDefinitionRequestDto runningDateReport = createReport(
      PROC_INST_FREQ_GROUP_BY_RUNNING_DATE,
      AggregateByDateUnit.AUTOMATIC
    );

    final SingleProcessReportDefinitionRequestDto startDateReport = createReport(
      PROC_INST_FREQ_GROUP_BY_START_DATE,
      AggregateByDateUnit.AUTOMATIC
    );

    final SingleProcessReportDefinitionRequestDto endDateReport = createReport(
      PROC_INST_FREQ_GROUP_BY_END_DATE,
      AggregateByDateUnit.AUTOMATIC
    );

    final SingleProcessReportDefinitionRequestDto emptyRunningDateReport = createReport(
      PROC_INST_FREQ_GROUP_BY_RUNNING_DATE,
      AggregateByDateUnit.AUTOMATIC,
      createSuspendedInstancesOnlyFilter()
    );

    final SingleProcessReportDefinitionRequestDto emptyStartDateReport = createReport(
      PROC_INST_FREQ_GROUP_BY_START_DATE,
      AggregateByDateUnit.AUTOMATIC,
      createSuspendedInstancesOnlyFilter()
    );

    return Stream.of(
      Arrays.asList(runningDateReport, startDateReport),
      Arrays.asList(runningDateReport, endDateReport),
      Arrays.asList(startDateReport, endDateReport),
      Arrays.asList(emptyRunningDateReport, emptyStartDateReport),
      Arrays.asList(runningDateReport, emptyStartDateReport)
    );
  }

  private CombinedProcessReportResultDataDto<?> getCombinedReportResult(
    final List<SingleProcessReportDefinitionRequestDto> reports) {
    return reportClient.saveAndEvaluateCombinedReport(
      reports.stream()
        .map(reportClient::createSingleProcessReport)
        .collect(toList())
    );
  }

  private ProcessReportDataDto createDateVariableReport(final String processDefinitionKey,
                                                        final String processDefinitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setVariableName("dateVar")
      .setVariableType(VariableType.DATE)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_VARIABLE)
      .build();
  }

  private static SingleProcessReportDefinitionRequestDto createReport(final ProcessReportDataType reportDataType,
                                                                      final AggregateByDateUnit unit) {
    return createReport(reportDataType, unit, Collections.emptyList());
  }

  private static SingleProcessReportDefinitionRequestDto createReport(final ProcessReportDataType reportDataType,
                                                                      final AggregateByDateUnit unit,
                                                                      final List<ProcessFilterDto<?>> filters) {
    SingleProcessReportDefinitionRequestDto reportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto runningReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_PROCESS)
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(unit)
      .setReportDataType(reportDataType)
      .build();
    runningReportData.setFilter(filters);
    reportDefinitionDto.setData(runningReportData);
    return reportDefinitionDto;
  }

  private static List<ProcessFilterDto<?>> createSuspendedInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().suspendedInstancesOnly().add().buildList();
  }

  private static List<ProcessFilterDto<?>> createNonSuspendedInstancesOnlyFilter() {
    return ProcessFilterBuilder.filter().nonSuspendedInstancesOnly().add().buildList();
  }

}
