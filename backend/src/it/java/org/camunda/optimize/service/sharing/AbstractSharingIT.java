/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.sharing;

import org.apache.http.HttpStatus;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractSharingIT extends AbstractIT {

  protected static final String FAKE_REPORT_ID = "fake";

  protected String createReport() {
    return createReport("aProcess");
  }

  protected String createReport(String definitionKey) {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess(definitionKey);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.RAW_DATA)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  public static void assertErrorFields(ReportEvaluationException errorMessage) {
    assertThat(errorMessage.getReportDefinition(), is(notNullValue()));
    ReportDefinitionDto<?> reportDefinitionDto = errorMessage.getReportDefinition().getDefinitionDto();
    if (reportDefinitionDto instanceof SingleProcessReportDefinitionDto) {
      SingleProcessReportDefinitionDto singleProcessReport =
        (SingleProcessReportDefinitionDto) reportDefinitionDto;
      assertThat(singleProcessReport.getData(), is(notNullValue()));
      assertThat(singleProcessReport.getName(), is(notNullValue()));
      assertThat(singleProcessReport.getId(), is(notNullValue()));
    } else if (reportDefinitionDto instanceof SingleDecisionReportDefinitionDto) {
      SingleDecisionReportDefinitionDto singleDecisionReport =
        (SingleDecisionReportDefinitionDto) reportDefinitionDto;
      assertThat(singleDecisionReport.getData(), is(notNullValue()));
      assertThat(singleDecisionReport.getName(), is(notNullValue()));
      assertThat(singleDecisionReport.getId(), is(notNullValue()));
    } else {
      throw new OptimizeIntegrationTestException("Evaluation exception should return single report definition!");
    }
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess(String definitionKey) {
    return deployAndStartSimpleProcessWithVariables(definitionKey, new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(String definitionKey,
                                                                            Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected String createDashboardWithReport(String reportId) {
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);
    return dashboardId;
  }

  void addReportToDashboard(String dashboardId, String... reportIds) {
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardId);

    List<ReportLocationDto> reports = new ArrayList<>();

    if (reportIds != null) {
      int i = 0;
      for (String reportId : reportIds) {
        ReportLocationDto reportLocation = new ReportLocationDto();
        reportLocation.setId(reportId);
        PositionDto position = new PositionDto();
        position.setX(i);
        position.setY(i);
        reportLocation.setPosition(position);
        reports.add(reportLocation);
        i = i + 2;
      }
    }

    fullBoard.setReports(reports);

    dashboardClient.updateDashboard(dashboardId, fullBoard);
  }

  protected String addEmptyDashboardToOptimize() {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  protected String addShareForDashboard(String dashboardId) {
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = createDashboardShareResponse(share);

    return response.readEntity(IdDto.class).getId();
  }

  protected Response createReportShareResponse(ReportShareDto share) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildShareReportRequest(share)
      .execute();
  }

  protected Response createDashboardShareResponse(DashboardShareDto share) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildShareDashboardRequest(share)
      .execute();
  }

  ReportShareDto createReportShare() {
    return createReportShare(FAKE_REPORT_ID);
  }

  protected ReportShareDto createReportShare(String reportId) {
    ReportShareDto sharingDto = new ReportShareDto();
    sharingDto.setReportId(reportId);
    return sharingDto;
  }

  protected DashboardShareDto createDashboardShareDto(String dashboardId) {
    DashboardShareDto sharingDto = new DashboardShareDto();
    sharingDto.setDashboardId(dashboardId);
    return sharingDto;
  }

  protected String addShareForReport(String reportId) {
    ReportShareDto share = createReportShare(reportId);
    Response response = createReportShareResponse(share);

    return response.readEntity(IdDto.class).getId();
  }

  private Response findShareForReport(String reportId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildFindShareForReportRequest(reportId)
      .execute();
  }

  protected ReportShareDto getShareForReport(String reportId) {
    Response response = findShareForReport(reportId);
    return response.readEntity(ReportShareDto.class);
  }

  protected void assertReportData(String reportId, HashMap<?, ?> evaluatedReportAsMap) {
    assertThat(evaluatedReportAsMap, is(notNullValue()));
    assertThat(evaluatedReportAsMap.get("id"), is(reportId));
    assertThat(evaluatedReportAsMap.get("data"), is(notNullValue()));
  }

}
