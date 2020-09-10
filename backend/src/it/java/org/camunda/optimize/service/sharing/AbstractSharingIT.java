/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.sharing;

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
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

public abstract class AbstractSharingIT extends AbstractIT {

  protected static final String FAKE_REPORT_ID = "fake";

  protected String createReportWithInstance() {
    return createReportWithInstance("aProcess");
  }

  protected String createReportWithInstance(String definitionKey) {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess(definitionKey);
    importAllEngineEntitiesFromScratch();
    return createReport(processInstance.getProcessDefinitionKey(), Collections.singletonList("ALL"));
  }

  protected String createReport(String definitionKey, List<String> versions) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = createSingleProcessReport(
      definitionKey,
      versions,
      ProcessReportDataType.RAW_DATA
    );
    return reportClient.createSingleProcessReport(singleProcessReportDefinitionDto);
  }

  protected SingleProcessReportDefinitionDto createSingleProcessReport(final String definitionKey,
                                                                       final List<String> versions,
                                                                       final ProcessReportDataType type) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(definitionKey)
      .setProcessDefinitionVersions(versions)
      .setReportDataType(type)
      .build();
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = new SingleProcessReportDefinitionDto();
    singleProcessReportDefinitionDto.setData(reportData);
    return singleProcessReportDefinitionDto;
  }

  public static void assertErrorFields(ReportEvaluationException errorMessage) {
    assertThat(errorMessage.getReportDefinition()).isNotNull();
    ReportDefinitionDto<?> reportDefinitionDto = errorMessage.getReportDefinition().getDefinitionDto();
    if (reportDefinitionDto instanceof SingleProcessReportDefinitionDto) {
      SingleProcessReportDefinitionDto singleProcessReport =
        (SingleProcessReportDefinitionDto) reportDefinitionDto;
      assertThat(singleProcessReport.getData()).isNotNull();
      assertThat(singleProcessReport.getName()).isNotNull();
      assertThat(singleProcessReport.getId()).isNotNull();
    } else if (reportDefinitionDto instanceof SingleDecisionReportDefinitionDto) {
      SingleDecisionReportDefinitionDto singleDecisionReport =
        (SingleDecisionReportDefinitionDto) reportDefinitionDto;
      assertThat(singleDecisionReport.getData()).isNotNull();
      assertThat(singleDecisionReport.getName()).isNotNull();
      assertThat(singleDecisionReport.getId()).isNotNull();
    } else {
      throw new OptimizeIntegrationTestException("Evaluation exception should return single report definition!");
    }
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess(String definitionKey) {
    return deployAndStartSimpleProcessWithVariables(definitionKey, new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(String definitionKey,
                                                                            Map<String, Object> variables) {
    BpmnModelInstance processModel = getSimpleBpmnDiagram(definitionKey);
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
    Response response = sharingClient.createDashboardShareResponse(share);

    return response.readEntity(IdDto.class).getId();
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
    Response response = sharingClient.createReportShareResponse(share);

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
    assertThat(evaluatedReportAsMap).isNotNull();
    assertThat(evaluatedReportAsMap.get("id")).isEqualTo(reportId);
    assertThat(evaluatedReportAsMap.get("data")).isNotNull();
  }

}
