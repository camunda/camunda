/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter.process;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.SingleReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.AggregateByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.rest.report.CombinedProcessReportResultDataDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.AbstractProcessDefinitionIT;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE;
import static org.camunda.optimize.test.util.ProcessReportDataType.RAW_DATA;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;

public class InstanceCountIT extends AbstractProcessDefinitionIT {

  @SneakyThrows
  @Test
  public void instanceCountWithoutFilters_processReport() {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deploySimpleOneUserTasksDefinition();
    ProcessInstanceEngineDto firstProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    ProcessInstanceEngineDto secondProcInst = engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());
    engineIntegrationExtension.startProcessInstance(userTaskProcess.getId());

    engineDatabaseExtension.changeProcessInstanceState(
      firstProcInst.getId(),
      SUSPENDED_STATE
    );
    engineDatabaseExtension.changeProcessInstanceState(
      secondProcInst.getId(),
      SUSPENDED_STATE
    );

    importAllEngineEntitiesFromScratch();

    // when
    ProcessReportDataDto reportWithFilter = createReport(
      userTaskProcess.getKey(),
      userTaskProcess.getVersionAsString()
    );
    ProcessReportDataDto reportWithoutFilter = createReport(
      userTaskProcess.getKey(),
      userTaskProcess.getVersionAsString()
    );
    reportWithFilter.setFilter(ProcessFilterBuilder.filter().suspendedInstancesOnly().add().buildList());

    RawDataProcessReportResultDto resultWithFilter = reportClient.evaluateRawReport(reportWithFilter).getResult();
    RawDataProcessReportResultDto resultWithoutFilter = reportClient.evaluateRawReport(reportWithoutFilter).getResult();

    // then
    assertThat(resultWithFilter.getInstanceCount()).isEqualTo(2L);
    assertThat(resultWithFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);

    assertThat(resultWithoutFilter.getInstanceCount()).isEqualTo(3L);
    assertThat(resultWithoutFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);
  }

  @Test
  public void instanceCountWithoutFilters_decisionReport() {
    DecisionDefinitionEngineDto decisionDefinitionDto = engineIntegrationExtension.deployDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto.getId());

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportWithFilter = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();
    DecisionReportDataDto reportWithoutFilter = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.RAW_DATA)
      .build();

    reportWithFilter.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(
      OffsetDateTime.now().plusDays(1),
      null
    )));

    RawDataDecisionReportResultDto resultWithFilter = reportClient.evaluateDecisionRawReport(reportWithFilter).getResult();
    RawDataDecisionReportResultDto resultWithoutFilter =
      reportClient.evaluateDecisionRawReport(reportWithoutFilter).getResult();

    // then
    assertThat(resultWithFilter.getInstanceCount()).isEqualTo(0L);
    assertThat(resultWithFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);

    assertThat(resultWithoutFilter.getInstanceCount()).isEqualTo(3L);
    assertThat(resultWithoutFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);
  }

  @SneakyThrows
  @Test
  public void instanceCount_combinedReport_endDateReportsExcludeRunningInstances() {
    // given
    ProcessDefinitionEngineDto runningInstanceDef = deploySimpleOneUserTasksDefinition("runningInstanceDef", null);
    engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());
    engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());

    final SingleProcessReportDefinitionRequestDto singleReport1 = createDateReport(
      COUNT_PROC_INST_FREQ_GROUP_BY_END_DATE
    );
    singleReport1.getData().setProcessDefinitionKey("runningInstanceDef");
    final SingleProcessReportDefinitionRequestDto singleReport2 = createDateReport(
      COUNT_PROC_INST_FREQ_GROUP_BY_START_DATE
    );
    singleReport2.getData().setProcessDefinitionKey("runningInstanceDef");

    importAllEngineEntitiesFromScratch();

    // when
    final List<String> reportIds = Stream.of(singleReport1, singleReport2)
      .map(reportClient::createSingleProcessReport)
      .collect(toList());
    final CombinedProcessReportResultDataDto<SingleReportResultDto> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(reportIds);

    // then
    assertThat(combinedResult.getInstanceCount()).isEqualTo(2);
  }

  @SneakyThrows
  @Test
  public void instanceCount_emptyCombinedReport() {
    // given
    ProcessDefinitionEngineDto runningInstanceDef = deploySimpleOneUserTasksDefinition("runningInstanceDef", null);
    engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());
    engineIntegrationExtension.startProcessInstance(runningInstanceDef.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final CombinedProcessReportResultDataDto<SingleReportResultDto> combinedResult =
      reportClient.saveAndEvaluateCombinedReport(Collections.emptyList());

    // then
    assertThat(combinedResult.getInstanceCount()).isEqualTo(0);
  }

  private ProcessReportDataDto createReport(String definitionKey, String definitionVersion) {
    return TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersion(definitionVersion)
      .setReportDataType(RAW_DATA)
      .build();
  }

  private SingleProcessReportDefinitionRequestDto createDateReport(final ProcessReportDataType reportDataType) {
    SingleProcessReportDefinitionRequestDto reportDefinitionDto = new SingleProcessReportDefinitionRequestDto();
    ProcessReportDataDto runningReportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(TEST_PROCESS)
      .setProcessDefinitionVersion("1")
      .setGroupByDateInterval(AggregateByDateUnit.DAY)
      .setReportDataType(reportDataType)
      .build();
    reportDefinitionDto.setData(runningReportData);
    return reportDefinitionDto;
  }
}
