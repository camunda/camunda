/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.processinstance.frequency.groupby.date.distributedby.none;

import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RollingDateFilterStartDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.EndDateFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.test.util.DateModificationHelper.truncateToStartOfUnit;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

public class CountProcessInstanceFrequencyByProcessInstanceEndDateReportEvaluationIT
  extends AbstractCountProcessInstanceFrequencyByProcessInstanceDateReportEvaluationIT {

  @Override
  protected ProcessReportDataType getTestReportDataType() {
    return ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.END_DATE;
  }

  @Override
  protected void changeProcessInstanceDate(String processInstanceId,
                                           final OffsetDateTime newDate) {
    engineDatabaseExtension.changeProcessInstanceEndDate(processInstanceId, newDate);
  }

  @Override
  protected void updateProcessInstanceDates(final Map<String, OffsetDateTime> newIdToDates) {
    engineDatabaseExtension.changeProcessInstanceEndDates(newIdToDates);
  }

  @Test
  public void testEmptyBucketsAreReturnedForEndDateFilterPeriod() {
    // given
    final OffsetDateTime endDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto = deployAndStartSimpleServiceTaskProcess();
    final String definitionId = processInstanceDto.getDefinitionId();
    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtension.startProcessInstance(definitionId);
    changeProcessInstanceDate(processInstanceDto2.getId(), endDate.minusDays(2));

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = createReportDataSortedDesc(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionVersion(),
      getTestReportDataType(),
      AggregateByDateUnit.DAY
    );

    final RollingDateFilterDataDto dateFilterDataDto = new RollingDateFilterDataDto(
      new RollingDateFilterStartDto(4L, DateFilterUnit.DAYS)
    );
    final EndDateFilterDto endDateFilter = new EndDateFilterDto();
    endDateFilter.setData(dateFilterDataDto);
    endDateFilter.setFilterLevel(FilterApplicationLevel.INSTANCE);
    reportData.setFilter(Collections.singletonList(endDateFilter));

    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();
    assertThat(resultData.size(), is(5));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate, ChronoUnit.DAYS))
    );
    assertThat(resultData.get(0).getValue(), is(1.));

    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(1), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(1).getValue(), is(0.));

    assertThat(
      resultData.get(2).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(2), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(2).getValue(), is(1.));

    assertThat(
      resultData.get(3).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(3), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(3).getValue(), is(0.));

    assertThat(
      resultData.get(4).getKey(),
      is(embeddedOptimizeExtension.formatToHistogramBucketKey(endDate.minusDays(4), ChronoUnit.DAYS))
    );
    assertThat(resultData.get(4).getValue(), is(0.));
  }

  @Test
  public void evaluateReportWithSeveralRunningAndCompletedProcessInstances() {
    // given 1 completed + 2 running process instances
    final OffsetDateTime now = OffsetDateTime.now();

    final ProcessDefinitionEngineDto processDefinition = deployTwoRunningAndOneCompletedUserTaskProcesses(now);

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinition.getKey())
      .setProcessDefinitionVersion(processDefinition.getVersionAsString())
      .setReportDataType(getTestReportDataType())
      .build();

    AuthorizedProcessReportEvaluationResultDto<ReportMapResultDto> evaluationResponse = reportClient.evaluateMapReport(
      reportData);

    // then
    final ReportMapResultDto result = evaluationResponse.getResult();
    assertThat(result.getInstanceCount(), is(1L));

    final List<MapResultEntryDto> resultData = result.getFirstMeasureData();

    assertThat(resultData, is(notNullValue()));
    assertThat(resultData.size(), is(1));

    ZonedDateTime startOfToday = truncateToStartOfUnit(now, ChronoUnit.DAYS);
    assertThat(resultData.get(0).getKey(), is(localDateTimeToString(startOfToday)));
    assertThat(resultData.get(0).getValue(), is(1.));
  }

}
