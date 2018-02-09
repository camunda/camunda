package org.camunda.optimize.service.sharing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
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
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardId);
    ReportLocationDto reportLocation = new ReportLocationDto();
    reportLocation.setId(reportId);
    List<ReportLocationDto> reports = new ArrayList<>();
    reports.add(reportLocation);
    fullBoard.setReports(reports);
    updateDashboard(dashboardId, fullBoard);
    return dashboardId;
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
    SharingDto share = createDashboardShare(dashboardId);
    Response response = createShareResponse(token, share);

    return response.readEntity(IdDto.class).getId();
  }

  protected Response createShareResponse(String token, SharingDto share) {
    return embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));
  }

  protected SharingDto createReportShare() {
    return createReportShare(FAKE_REPORT_ID);
  }

  protected SharingDto createReportShare(String reportId) {
    SharingDto sharingDto = new SharingDto();
    sharingDto.setResourceId(reportId);
    sharingDto.setType(SharedResourceType.REPORT);
    return sharingDto;
  }

  protected SharingDto createDashboardShare(String dashboardId) {
    SharingDto sharingDto = new SharingDto();
    sharingDto.setResourceId(dashboardId);
    sharingDto.setType(SharedResourceType.DASHBOARD);
    return sharingDto;
  }

  protected String addShareForReport(String token, String reportId) {
    SharingDto share = createReportShare(reportId);
    Response response = createShareResponse(token, share);

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

  protected SharingDto getShareForReport(String token, String reportId) {
    Response response = findShareForReport(token, reportId);
    return response.readEntity(SharingDto.class);
  }

  protected void assertReportData(String reportId, String shareId, HashMap evaluatedReportAsMap) {
    assertThat(evaluatedReportAsMap, is(notNullValue()));
    assertThat(evaluatedReportAsMap.get("id"), is(shareId));
    Map reportMap = (Map) evaluatedReportAsMap.get("report");
    assertThat(reportMap.get("id"), is(reportId));
    assertThat(reportMap.get("data"), is(notNullValue()));
  }

}
