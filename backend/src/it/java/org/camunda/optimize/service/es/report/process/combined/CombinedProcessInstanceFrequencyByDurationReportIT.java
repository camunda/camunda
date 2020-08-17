/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.combined;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportItemDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.RoundingUtil.roundUpToNearestPowerOfTen;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_DURATION;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;

public class CombinedProcessInstanceFrequencyByDurationReportIT extends AbstractProcessDefinitionIT {

  @Test
  public void distinctRangesGetMerged() {
    // given
    final ProcessInstanceEngineDto firstProcessProcessInstance = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDuration(firstProcessProcessInstance, 1000);
    startInstanceAndModifyDuration(firstProcessProcessInstance.getDefinitionId(), 2000);

    final ProcessInstanceEngineDto secondProcessProcessInstance = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDuration(secondProcessProcessInstance, 8000);
    startInstanceAndModifyDuration(secondProcessProcessInstance.getDefinitionId(), 10_000);

    importAllEngineEntitiesFromScratch();

    //when
    final CombinedReportDefinitionDto combinedReport = createCombinedReport(
      firstProcessProcessInstance.getProcessDefinitionKey(), secondProcessProcessInstance.getProcessDefinitionKey()
    );
    final IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    //then
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.<ReportMapResultDto>evaluateCombinedReportById(response.getId()).getResult();
    assertThat(result.getData().values())
      .hasSize(2)
      .allSatisfy(singleReportResult -> {
        assertThat(singleReportResult.getResult().getData())
          .hasSize(calculateExpectedBucketCount(1000, 10_000))
          .extracting(MapResultEntryDto::getKey)
          .first().isEqualTo(createDurationBucketKey(1000));
        assertThat(singleReportResult.getResult().getData())
          .extracting(MapResultEntryDto::getKey)
          .last().isEqualTo(createDurationBucketKey(10_000));
      });
  }

  @Test
  public void intersectingRangesGetMerged() {
    // given
    final ProcessInstanceEngineDto firstProcessProcessInstance = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDuration(firstProcessProcessInstance, 5000);
    startInstanceAndModifyDuration(firstProcessProcessInstance.getDefinitionId(), 7000);

    final ProcessInstanceEngineDto secondProcessProcessInstance = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDuration(secondProcessProcessInstance, 6000);
    startInstanceAndModifyDuration(secondProcessProcessInstance.getDefinitionId(), 10_000);

    importAllEngineEntitiesFromScratch();

    //when
    final CombinedReportDefinitionDto combinedReport = createCombinedReport(
      firstProcessProcessInstance.getProcessDefinitionKey(), secondProcessProcessInstance.getProcessDefinitionKey()
    );
    final IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    //then
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.<ReportMapResultDto>evaluateCombinedReportById(response.getId()).getResult();
    assertThat(result.getData().values())
      .hasSize(2)
      .allSatisfy(singleReportResult -> {
        assertThat(singleReportResult.getResult().getData())
          // expecting the range to be from 5000ms to 10000ms as these are the global min/max values
          .hasSize(calculateExpectedBucketCount(5000, 10_000))
          .extracting(MapResultEntryDto::getKey)
          .first().isEqualTo(createDurationBucketKey(5000));
        assertThat(singleReportResult.getResult().getData())
          .extracting(MapResultEntryDto::getKey)
          .last().isEqualTo(createDurationBucketKey(10_000));
      });
  }

  @Test
  public void inclusiveRangesOuterRangeIsKept() {
    // given
    final ProcessInstanceEngineDto firstProcessProcessInstance = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDuration(firstProcessProcessInstance, 60_000);
    startInstanceAndModifyDuration(firstProcessProcessInstance.getDefinitionId(), 80_000);

    final ProcessInstanceEngineDto secondProcessProcessInstance = deployAndStartSimpleServiceTaskProcess();
    changeProcessInstanceDuration(secondProcessProcessInstance, 50_000);
    startInstanceAndModifyDuration(secondProcessProcessInstance.getDefinitionId(), 100_000);

    importAllEngineEntitiesFromScratch();

    //when
    final CombinedReportDefinitionDto combinedReport = createCombinedReport(
      firstProcessProcessInstance.getProcessDefinitionKey(), secondProcessProcessInstance.getProcessDefinitionKey()
    );
    final IdDto response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateCombinedReportRequest(combinedReport)
      .execute(IdDto.class, Response.Status.OK.getStatusCode());

    //then
    final CombinedProcessReportResultDataDto<ReportMapResultDto> result =
      reportClient.<ReportMapResultDto>evaluateCombinedReportById(response.getId()).getResult();
    assertThat(result.getData().values())
      .hasSize(2)
      .allSatisfy(singleReportResult -> {
        assertThat(singleReportResult.getResult().getData())
          // expecting the range to be from 50_000ms to 100_000ms as these are the global min/max values
          .hasSize(calculateExpectedBucketCount(50_000, 100_000))
          .extracting(MapResultEntryDto::getKey)
          .first().isEqualTo(createDurationBucketKey(50__000));
        assertThat(singleReportResult.getResult().getData())
          .extracting(MapResultEntryDto::getKey)
          .last().isEqualTo(createDurationBucketKey(100_000));
      });
  }

  private int calculateExpectedBucketCount(final int globalMinimumDuration, final int globalMaximumDuration) {
    final int distance = globalMaximumDuration - globalMinimumDuration;
    // this reflects the expected behavior in the ProcessGroupByDuration command to slice the buckets
    final int expectedBucketInterval = roundUpToNearestPowerOfTen(
      Math.ceil((double) distance / NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION)
    ).intValue();
    // + 1 as the min/max is inclusive
    return 1 + distance / expectedBucketInterval;
  }

  private CombinedReportDefinitionDto createCombinedReport(final String firstReportDefinitionKey,
                                                           final String secondReportDefinitionKey) {
    final String reportId1 = createAndStoreDefaultReportDefinition(firstReportDefinitionKey);
    final String reportId2 = createAndStoreDefaultReportDefinition(secondReportDefinitionKey);

    final CombinedReportDataDto combinedReportData = new CombinedReportDataDto();
    final List<CombinedReportItemDto> reportIds = new ArrayList<>();
    reportIds.add(new CombinedReportItemDto(reportId1));
    reportIds.add(new CombinedReportItemDto(reportId2));

    combinedReportData.setReports(reportIds);
    final CombinedReportDefinitionDto combinedReport = new CombinedReportDefinitionDto();
    combinedReport.setData(combinedReportData);
    return combinedReport;
  }

  private String createDurationBucketKey(final int durationInMs) {
    return durationInMs + ".0";
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey) {
    final ProcessReportDataDto reportData = createReport(processDefinitionKey, ReportConstants.ALL_VERSIONS);
    return createNewReport(reportData);
  }

  private ProcessReportDataDto createReport(final String processKey, final String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(COUNT_PROC_INST_FREQ_GROUP_BY_DURATION)
      .build();
  }

}
