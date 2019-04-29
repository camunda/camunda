/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.sharing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
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
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ProcessReportDataBuilderHelper;

import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;


public abstract class AbstractSharingIT {
  protected static final String SHARE = "share";
  protected static final String FAKE_REPORT_ID = "fake";
  private static final String EVALUATE = "evaluate";
  protected static final String REPORT = "report";
  protected static final String DASHBOARD = "dashboard";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  protected String createReport() {
    return createReport("aProcess");
  }

  protected String createReport(String definitionKey) {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess(definitionKey);
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    String reportId = this.createNewReport();
    ProcessReportDataDto reportData =
      ProcessReportDataBuilderHelper.createProcessReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion()
      );
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    updateReport(reportId, report);
    return reportId;
  }

  public static void assertErrorFields(ReportEvaluationException errorMessage) {
    assertThat(errorMessage.getReportDefinition(), is(notNullValue()));
    ReportDefinitionDto reportDefinitionDto = errorMessage.getReportDefinition();
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
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void updateReport(String id, SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateSingleProcessReportRequest(id, updatedReport)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  protected String createNewReport() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateSingleProcessReportRequest()
      .execute(IdDto.class, 200)
      .getId();
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

    updateDashboard(dashboardId, fullBoard);
  }

  protected String addEmptyDashboardToOptimize() {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildCreateDashboardRequest()
      .execute(IdDto.class, 200)
      .getId();
  }

  Response updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildUpdateDashboardRequest(id, updatedDashboard)
      .execute();
  }

  protected String addShareForDashboard(String dashboardId) {
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = createDashboardShareResponse(share);

    return response.readEntity(IdDto.class).getId();
  }

  protected Response createReportShareResponse(ReportShareDto share) {
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildShareReportRequest(share)
      .execute();
  }

  protected Response createDashboardShareResponse(DashboardShareDto share) {
    return embeddedOptimizeRule
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
    return embeddedOptimizeRule
      .getRequestExecutor()
      .buildFindShareForReportRequest(reportId)
      .execute();
  }

  protected ReportShareDto getShareForReport(String reportId) {
    Response response = findShareForReport(reportId);
    return response.readEntity(ReportShareDto.class);
  }

  protected void assertReportData(String reportId, HashMap evaluatedReportAsMap) {
    assertThat(evaluatedReportAsMap, is(notNullValue()));
    assertThat(evaluatedReportAsMap.get("id"), is(reportId));
    assertThat(evaluatedReportAsMap.get("data"), is(notNullValue()));
  }

  protected void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineRule.createAuthorization(authorizationDto);
  }

}
