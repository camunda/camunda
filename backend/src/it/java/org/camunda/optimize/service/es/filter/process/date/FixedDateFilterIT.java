/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process.date;

import org.camunda.optimize.dto.engine.HistoricProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.filter.process.AbstractFilterIT;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public class FixedDateFilterIT extends AbstractFilterIT {

  @Test
  public void testGetHeatMapWithGteStartDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    List<ProcessFilterDto<?>> fixedStartDateFilter =
      ProcessFilterBuilder.filter()
        .fixedStartDate()
        .start(start.plus(1, ChronoUnit.DAYS))
        .end(OffsetDateTime.now())
        .add()
        .buildList();
    reportData.setFilter(fixedStartDateFilter);
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).isEmpty();

    // when
    reportData.setFilter(ProcessFilterBuilder.filter().fixedStartDate().start(start).end(null).add().buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);
  }

  @Test
  public void testGetHeatMapWithLteStartDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.plus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.plusDays(1))
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).isEmpty();
  }

  @Test
  public void testGetHeatMapWithGteEndDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);

    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(null)
                           .end(end.plus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end)
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).isEmpty();
  }

  @Test
  public void testGetHeatMapWithLteEndDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime end = processInstance.getEndTime();

    // when
    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // when
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.plus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();
    // then
    assertThat(resultData).isEmpty();
  }

  @Test
  public void testGetHeatMapWithMixedDateCriteria() {
    // given
    ProcessInstanceEngineDto engineDto = startAndImportSimpleProcess();
    HistoricProcessInstanceDto processInstance =
      engineIntegrationExtension.getHistoricProcessInstance(engineDto.getId());
    OffsetDateTime start = processInstance.getStartTime();
    OffsetDateTime end = processInstance.getEndTime();

    ProcessReportDataDto reportData = createReportWithInstance(engineDto);
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(start.minus(1, ChronoUnit.DAYS))
                           .end(null)
                           .add()
                           .buildList());

    // when
    List<RawDataProcessInstanceDto> resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedEndDate()
                           .start(end.minusSeconds(200L))
                           .end(null)
                           .add()
                           .buildList());

    // when
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).hasSize(1);

    // given
    reportData.setFilter(ProcessFilterBuilder.filter()
                           .fixedStartDate()
                           .start(null)
                           .end(start.minus(1, ChronoUnit.DAYS))
                           .add()
                           .buildList());

    // when
    resultData = reportClient.evaluateRawReport(reportData).getResult().getFirstMeasureData();

    // then
    assertThat(resultData).isEmpty();
  }

  private ProcessInstanceEngineDto startAndImportSimpleProcess() {
    ProcessInstanceEngineDto processInstanceDto = engineIntegrationExtension.deployAndStartProcess(
      getSingleServiceTaskProcess());
    importAllEngineEntitiesFromScratch();
    return processInstanceDto;
  }

}
