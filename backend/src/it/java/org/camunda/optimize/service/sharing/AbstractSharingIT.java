package org.camunda.optimize.service.sharing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.PositionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractSharingIT {
  public static final String BEARER = "Bearer ";
  public static final String SHARE = "share";
  public static final String FAKE_REPORT_ID = "fake";
  public static final String EVALUATE = "evaluate";
  public static final String REPORT = "report";
  public static final String DASHBOARD = "dashboard";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  protected String createReport() throws InterruptedException {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    String processDefinitionId = processInstance.getDefinitionId();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = this.createNewReport();
    ReportDataDto reportData = ReportDataHelper.createReportDataViewRawAsTable(processDefinitionId);
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    updateReport(reportId, report);
    return reportId;
  }


  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
          .startEvent()
          .endEvent()
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  protected String createNewReport() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  protected String createDashboardWithReport(String token, String reportId) {
    String dashboardId = addEmptyDashboardToOptimize(token);
    addReportToDashboard(dashboardId, reportId);
    return dashboardId;
  }

  protected void addReportToDashboard(String dashboardId, String... reportIds) {
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

  protected String addEmptyDashboardToOptimize(String token) {
    Response response =
        embeddedOptimizeRule.target("dashboard")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(""));

    return response.readEntity(IdDto.class).getId();
  }

  protected void updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("dashboard/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .put(Entity.json(updatedDashboard));
    assertThat(response.getStatus(), is(204));
  }

  protected String addShareForDashboard(String token, String dashboardId) {
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = createDashboardShareResponse(token, share);

    return response.readEntity(IdDto.class).getId();
  }

  protected Response createReportShareResponse(String token, ReportShareDto share) {
    return embeddedOptimizeRule.target(SHARE + "/" + REPORT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));
  }

  protected Response createDashboardShareResponse(String token, DashboardShareDto share) {
    return embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));
  }

  protected ReportShareDto createReportShare() {
    return createReportShare(FAKE_REPORT_ID);
  }

  protected ReportShareDto createReportShare(String reportId) {
    ReportShareDto sharingDto = new ReportShareDto();
    sharingDto.setReportId(reportId);
    sharingDto.setType(SharedResourceType.REPORT);
    return sharingDto;
  }

  protected DashboardShareDto createDashboardShareDto(String dashboardId) {
    DashboardShareDto sharingDto = new DashboardShareDto();
    sharingDto.setDashboardId(dashboardId);
    sharingDto.setType(SharedResourceType.DASHBOARD);
    return sharingDto;
  }

  protected String addShareForReport(String token, String reportId) {
    ReportShareDto share = createReportShare(reportId);
    Response response = createReportShareResponse(token, share);

    return response.readEntity(IdDto.class).getId();
  }

  protected String getSharedReportEvaluationPath(String shareId) {
    return SHARE + "/"+ REPORT + "/" + shareId + "/" + EVALUATE;
  }

  protected String getSharedDashboardEvaluationPath(String shareId) {
    return SHARE + "/"+ DASHBOARD + "/" + shareId + "/" + EVALUATE;
  }

  protected Response findShareForReport(String token, String reportId) {
    return embeddedOptimizeRule.target(SHARE + "/report/" + reportId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .get();
  }

  protected ReportShareDto getShareForReport(String token, String reportId) {
    Response response = findShareForReport(token, reportId);
    return response.readEntity(ReportShareDto.class);
  }

  protected void assertReportData(String reportId, String shareId, HashMap evaluatedReportAsMap) {
    assertThat(evaluatedReportAsMap, is(notNullValue()));
    assertThat(evaluatedReportAsMap.get("id"), is(shareId));
    Map reportMap = (Map) evaluatedReportAsMap.get("report");
    assertThat(reportMap.get("id"), is(reportId));
    assertThat(reportMap.get("data"), is(notNullValue()));
  }

}
