/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date.instance;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.DateCreationFreezer.dateFreezer;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public class FixedInstanceDateFilterIT extends AbstractFilterIT {

  @Test
  public void filterWithGteStartDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    List<ProcessFilterDto<?>> fixedStartDateFilter =
      ProcessFilterBuilder.filter()
        .fixedInstanceStartDate()
        .start(start.plus(1, ChronoUnit.DAYS))
        .end(OffsetDateTime.now())
        .add()
        .buildList();
    reportData.setFilter(fixedStartDateFilter);
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData)
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData).isEmpty();

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(start)
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(start.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);
  }

  @Test
  public void filterWithLteStartDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(null)
                           .end(start.plus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData)
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(null)
                           .end(start.plusDays(1))
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(null)
                           .end(start.minus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).isEmpty();
  }

  @Test
  public void filterWithGteEndDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceEndDate()
                           .start(null)
                           .end(end.plus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData)
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceEndDate()
                           .start(end)
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceEndDate()
                           .start(end.plus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).isEmpty();
  }

  @Test
  public void filterWithLteEndDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceEndDate()
                           .start(end.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData)
      .getResult()
      .getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceEndDate()
                           .start(end.plus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("dateReportTypes")
  public void filtersWithNullFieldsWorkForDateReports(final ProcessReportDataType reportType) {
    // given
    final OffsetDateTime now = dateFreezer().freezeDateAndReturn();
    final ProcessInstanceEngineDto instance1 = startAndImportSimpleProcess();
    final ProcessInstanceEngineDto instance2 = startAndImportSimpleProcess();
    engineDatabaseExtension.changeProcessInstanceStartDate(instance1.getId(), now.minusDays(2));
    engineDatabaseExtension.changeProcessInstanceStartDate(instance2.getId(), now);
    engineDatabaseExtension.changeProcessInstanceEndDate(instance1.getId(), now.minusDays(2));
    engineDatabaseExtension.changeProcessInstanceEndDate(instance2.getId(), now);

    importAllEngineEntitiesFromScratch();

    // works with lte filter
    final ProcessReportDataDto reportData = createReportData(instance1.getProcessDefinitionKey(), reportType);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(null)
                           .end(now.minusDays(1))
                           .add()
                           .fixedInstanceEndDate()
                           .start(null)
                           .end(now.minusDays(1))
                           .add()
                           .buildList());

    // when
    final ReportResultResponseDto<List<MapResultEntryDto>> result =
      reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);

    // works with gte filter
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedInstanceStartDate()
                           .start(now)
                           .end(null)
                           .add()
                           .fixedInstanceEndDate()
                           .start(now)
                           .end(null)
                           .add()
                           .buildList());

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1);
    assertThat(result.getInstanceCountWithoutFilters()).isEqualTo(1);
  }

  private static Stream<ProcessReportDataType> dateReportTypes() {
    return Stream.of(
      ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_START_DATE,
      ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_END_DATE
    );
  }

  private ProcessReportDataDto createReportData(final String definitionKey,
                                                final ProcessReportDataType reportType) {
    return TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.MONTH)
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(reportType)
      .build();
  }

  private ProcessInstanceEngineDto startAndImportSimpleProcess() {
    ProcessInstanceEngineDto processInstanceDto =
      engineIntegrationExtension.deployAndStartProcess(getSingleServiceTaskProcess());
    importAllEngineEntitiesFromScratch();
    return processInstanceDto;
  }

}
