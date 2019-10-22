/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.RelativeDateFilterUnit;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedProcessReportEvaluationResultDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.camunda.optimize.test.util.ProcessReportDataType.PROC_INST_DUR_GROUP_BY_START_DATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class RollingDateFilterIT extends AbstractRollingDateFilterIT {

  @Test
  public void testStartDateRollingLogic() {
    // given
    embeddedOptimizeExtensionRule.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    OffsetDateTime processInstanceStartTime =
      engineIntegrationExtensionRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();

    engineIntegrationExtensionRule.finishAllRunningUserTasks(processInstance.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    LocalDateUtil.setCurrentTime(processInstanceStartTime);

    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithRollingStartDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      RelativeDateFilterUnit.DAYS,
      false
    );

    assertResults(processInstance, result, 1);

    //when
    LocalDateUtil.setCurrentTime(OffsetDateTime.now().plusDays(2L));

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithRollingStartDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      RelativeDateFilterUnit.DAYS,
      true
    );

    assertResults(processInstance, result, 0);
  }

  @Test
  public void testEndDateRollingLogic() {
    embeddedOptimizeExtensionRule.reloadConfiguration();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    engineIntegrationExtensionRule.finishAllRunningUserTasks(processInstance.getId());

    OffsetDateTime processInstanceEndTime =
      engineIntegrationExtensionRule.getHistoricProcessInstance(processInstance.getId()).getEndTime();

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    LocalDateUtil.setCurrentTime(processInstanceEndTime);

    //token has to be refreshed, as the old one expired already after moving the date
    AuthorizedProcessReportEvaluationResultDto<RawDataProcessReportResultDto> result =
      createAndEvaluateReportWithRollingEndDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      RelativeDateFilterUnit.DAYS,
      true
    );

    assertResults(processInstance, result, 1);

    LocalDateUtil.setCurrentTime(processInstanceEndTime.plusDays(2L));

    //token has to be refreshed, as the old one expired already after moving the date
    result = createAndEvaluateReportWithRollingEndDateFilter(
      processInstance.getProcessDefinitionKey(),
      processInstance.getProcessDefinitionVersion(),
      RelativeDateFilterUnit.DAYS,
      true
    );

    assertResults(processInstance, result, 0);
  }

  @Test
  public void resultLimited_onTooBroadRelativeStartDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 1L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 2L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 100L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 1L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 2L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 3L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 4L);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();


    embeddedOptimizeExtensionRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter().relativeStartDate().start(10L, RelativeDateFilterUnit.DAYS).add().buildList()
    );
    final ReportMapResultDto result = evaluateReportWithMapResult(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtensionRule.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS))
    );
    assertThat(
      resultData.get(0).getValue(),
      is(1000L)
    );

    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtensionRule.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS))
    );
    assertThat(
      resultData.get(1).getValue(),
      is(nullValue())
    );
  }

  @Test
  public void resultLimited_onTooBroadRelativeEndDateFilter() {
    // given
    final OffsetDateTime startDate = OffsetDateTime.now();
    final ProcessInstanceEngineDto processInstanceDto1 = deployAndStartSimpleServiceTaskProcess();
    final String processDefinitionId = processInstanceDto1.getDefinitionId();
    final String processDefinitionKey = processInstanceDto1.getProcessDefinitionKey();
    final String processDefinitionVersion = processInstanceDto1.getProcessDefinitionVersion();
    adjustProcessInstanceDates(processInstanceDto1.getId(), startDate, 0L, 0L);

    final ProcessInstanceEngineDto processInstanceDto2 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto2.getId(), startDate, -1L, 10L);
    final ProcessInstanceEngineDto processInstanceDto3 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto3.getId(), startDate, -1L, 10L);

    final ProcessInstanceEngineDto processInstanceDto4 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto4.getId(), startDate, -2L, 10L);
    final ProcessInstanceEngineDto processInstanceDto5 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto5.getId(), startDate, -2L, 10L);
    final ProcessInstanceEngineDto processInstanceDto6 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto6.getId(), startDate, -2L, 10L);
    final ProcessInstanceEngineDto processInstanceDto7 = engineIntegrationExtensionRule.startProcessInstance(processDefinitionId);
    adjustProcessInstanceDates(processInstanceDto7.getId(), startDate, -2L, 10L);

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    embeddedOptimizeExtensionRule.getConfigurationService().setEsAggregationBucketLimit(2);

    // when
    final ProcessReportDataDto reportData = ProcessReportDataBuilder.createReportData()
      .setDateInterval(GroupByDateUnit.DAY)
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(PROC_INST_DUR_GROUP_BY_START_DATE)
      .build();
    reportData.setFilter(
      ProcessFilterBuilder.filter()
        .relativeEndDate()
        .start(10L, RelativeDateFilterUnit.DAYS)
        .add()
        .buildList()
    );
    final ReportMapResultDto result = evaluateReportWithMapResult(reportData).getResult();

    // then
    List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData.size(), is(2));
    assertThat(result.getIsComplete(), is(false));

    assertThat(
      resultData.get(0).getKey(),
      is(embeddedOptimizeExtensionRule.formatToHistogramBucketKey(startDate, ChronoUnit.DAYS))
    );

    assertThat(
      resultData.get(1).getKey(),
      is(embeddedOptimizeExtensionRule.formatToHistogramBucketKey(startDate.minusDays(1), ChronoUnit.DAYS))
    );
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    try {
      engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
      engineDatabaseExtensionRule.changeProcessInstanceEndDate(processInstanceId, shiftedStartDate.plusSeconds(durationInSec));
    } catch (SQLException e) {
      throw new OptimizeIntegrationTestException("Failed adjusting process instance dates", e);
    }
  }

  private ProcessInstanceEngineDto deployAndStartSimpleServiceTaskProcess() {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .serviceTask("activity")
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployAndStartProcess(processModel);
  }
}
