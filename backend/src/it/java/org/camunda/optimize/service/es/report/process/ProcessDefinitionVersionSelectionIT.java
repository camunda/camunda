/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.http.HttpStatus;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedEvaluationResultDto;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_VERSIONS;
import static org.camunda.optimize.dto.optimize.ReportConstants.LATEST_VERSION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProcessDefinitionVersionSelectionIT extends AbstractIT {

  private static final String START_EVENT = "startEvent";
  private static final String END_EVENT = "endEvent";
  private static final String VARIABLE_NAME = "StringVar";
  private static final String VARIABLE_VALUE = "StringVal";
  private static final String DEFINITION_KEY = "aProcess";

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  @Test
  public void processReportAcrossAllVersions() {
    // given
    ProcessDefinitionEngineDto definition1 = deployProcessAndStartInstances(2);
    deployProcessAndStartInstances(1);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<ProcessReportDataDto> allPossibleReports = createAllPossibleProcessReports(
      definition1.getKey(),
      ImmutableList.of(ALL_VERSIONS)
    );
    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(3L));
    }
  }

  @Test
  public void processReportAcrossMultipleVersions() {
    // given
    ProcessDefinitionEngineDto definition1 = deployProcessAndStartInstances(2);
    deployProcessAndStartInstances(1);
    ProcessDefinitionEngineDto definition3 = deployProcessAndStartInstances(3);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<ProcessReportDataDto> allPossibleReports = createAllPossibleProcessReports(
      definition1.getKey(),
      ImmutableList.of(definition1.getVersionAsString(), definition3.getVersionAsString())
    );
    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(5L));
    }
  }

  @Test
  public void processReportsWithLatestVersion() {
    // given
    ProcessDefinitionEngineDto definition1 = deployProcessAndStartInstances(2);
    deployProcessAndStartInstances(1);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<ProcessReportDataDto> allPossibleReports = createAllPossibleProcessReports(
      definition1.getKey(),
      ImmutableList.of(LATEST_VERSION)
    );
    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(1L));
    }

    // when
    deployProcessAndStartInstances(4);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> result = evaluateReport(report);

      // then
      assertThat(result.getResult().getInstanceCount(), is(4L));
    }
  }

  @Test
  public void missingDefinitionVersionResultsIn400() {
    // given
    ProcessDefinitionEngineDto definition1 = deployProcessAndStartInstances(1);

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    List<ProcessReportDataDto> allPossibleReports = createAllPossibleProcessReports(
      definition1.getKey(),
      ImmutableList.of()
    );
    for (ProcessReportDataDto report : allPossibleReports) {
      // when
      Response response = evaluateReportWithResponse(report);

      // then
      assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
    }
  }

  private List<ProcessReportDataDto> createAllPossibleProcessReports(String definitionKey,
                                                                            List<String> definitionVersions) {
    List<ProcessReportDataDto> reports = new ArrayList<>();
    for (ProcessReportDataType reportDataType : ProcessReportDataType.values()) {
      ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder.createReportData()
        .setReportDataType(reportDataType)
        .setProcessDefinitionKey(definitionKey)
        .setProcessDefinitionVersions(definitionVersions)
        .setVariableName(VARIABLE_NAME)
        .setVariableType(VariableType.STRING)
        .setDateInterval(GroupByDateUnit.DAY)
        .setUserTaskDurationTime(UserTaskDurationTime.TOTAL)
        .setStartFlowNodeId(START_EVENT)
        .setEndFlowNodeId(END_EVENT)
        .build();
      reports.add(reportData);
    }
    return reports;
  }

  private AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto> evaluateReport(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute(new TypeReference<AuthorizedEvaluationResultDto<ProcessReportResultDto, SingleProcessReportDefinitionDto>>() {
      });
  }

  private Response evaluateReportWithResponse(ProcessReportDataDto reportData) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSingleUnsavedReportRequest(reportData)
      .execute();
  }

  private ProcessDefinitionEngineDto deployProcessAndStartInstances(int nInstancesToStart) {
    ProcessDefinitionEngineDto definition = deploySimpleServiceTaskProcess();
    IntStream.range(0, nInstancesToStart).forEach(
      i -> engineIntegrationExtension.startProcessInstance(definition.getId(), ImmutableMap.of(VARIABLE_NAME, VARIABLE_VALUE))
    );
    return definition;
  }

  private ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(DEFINITION_KEY)
      .startEvent(START_EVENT)
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent(END_EVENT)
      .done();
    // @formatter:on
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }
}
