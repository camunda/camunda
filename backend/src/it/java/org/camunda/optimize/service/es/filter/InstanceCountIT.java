/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.camunda.optimize.dto.optimize.ProcessInstanceConstants.SUSPENDED_STATE;
import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createFixedEvaluationDateFilter;

public class InstanceCountIT extends AbstractFilterIT {

  @SneakyThrows
  @Test
  public void instanceCountWithoutFilters_processReport() {
    //given
    ProcessDefinitionEngineDto userTaskProcess = deployUserTaskProcess();
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
    ProcessReportDataDto reportWithFilter = createReportWithDefinition(userTaskProcess);
    ProcessReportDataDto reportWithoutFilter = createReportWithDefinition(userTaskProcess);
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

    reportWithFilter.setFilter(Lists.newArrayList(createFixedEvaluationDateFilter(OffsetDateTime.now(), null)));

    RawDataDecisionReportResultDto resultWithFilter = reportClient.evaluateRawReport(reportWithFilter).getResult();
    RawDataDecisionReportResultDto resultWithoutFilter =
      reportClient.evaluateRawReport(reportWithoutFilter).getResult();

    // then
    assertThat(resultWithFilter.getInstanceCount()).isEqualTo(0L);
    assertThat(resultWithFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);

    assertThat(resultWithoutFilter.getInstanceCount()).isEqualTo(3L);
    assertThat(resultWithoutFilter.getInstanceCountWithoutFilters()).isEqualTo(3L);
  }

}
