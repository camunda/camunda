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
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.ReportEvaluationException;
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
  protected static final String SHARE = "share";
  protected static final String FAKE_REPORT_ID = "fake";
  private static final String EVALUATE = "evaluate";
  protected static final String REPORT = "report";
  protected static final String DASHBOARD = "dashboard";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  protected String createReport() throws InterruptedException {
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String reportId = this.createNewReport();
    ReportDataDto reportData =
      ReportDataHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessDefinitionVersion());
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    updateReport(reportId, report);
    return reportId;
  }

  public static void assertErrorFields(ReportEvaluationException errorMessage) {
    assertThat(errorMessage.getReportDefinition(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getData(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getName(), is(notNullValue()));
    assertThat(errorMessage.getReportDefinition().getId(), is(notNullValue()));
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
          .startEvent()
          .endEvent()
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  protected String createNewReport() {
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
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
    Response response =
        embeddedOptimizeRule.target("dashboard")
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .post(Entity.json(""));

    return response.readEntity(IdDto.class).getId();
  }

  void updateDashboard(String id, DashboardDefinitionDto updatedDashboard) {
    Response response =
        embeddedOptimizeRule.target("dashboard/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
            .put(Entity.json(updatedDashboard));
    assertThat(response.getStatus(), is(204));
  }

  protected String addShareForDashboard(String dashboardId) {
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = createDashboardShareResponse(share);

    return response.readEntity(IdDto.class).getId();
  }

  protected Response createReportShareResponse(ReportShareDto share) {
    return embeddedOptimizeRule.target(SHARE + "/" + REPORT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(share));
  }

  protected Response createDashboardShareResponse(DashboardShareDto share) {
    return embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .post(Entity.json(share));
  }

  ReportShareDto createReportShare() {
    return createReportShare(FAKE_REPORT_ID);
  }

  protected ReportShareDto createReportShare(String reportId) {
    ReportShareDto sharingDto = new ReportShareDto();
    sharingDto.setReportId(reportId);
    return sharingDto;
  }

  private DashboardShareDto createDashboardShareDto(String dashboardId) {
    DashboardShareDto sharingDto = new DashboardShareDto();
    sharingDto.setDashboardId(dashboardId);
    return sharingDto;
  }

  protected String addShareForReport(String reportId) {
    ReportShareDto share = createReportShare(reportId);
    Response response = createReportShareResponse(share);

    return response.readEntity(IdDto.class).getId();
  }

  protected String getSharedReportEvaluationPath(String shareId) {
    return SHARE + "/"+ REPORT + "/" + shareId + "/" + EVALUATE;
  }

  protected String getSharedDashboardReportEvaluationPath(String dashboardShareId, String reportId) {
    return SHARE + "/"+ DASHBOARD + "/" + dashboardShareId + "/"  + REPORT + "/" + reportId  + "/" + EVALUATE;
  }

  protected String getSharedDashboardEvaluationPath(String shareId) {
    return SHARE + "/"+ DASHBOARD + "/" + shareId + "/" + EVALUATE;
  }

  private Response findShareForReport(String reportId) {
    return embeddedOptimizeRule.target(SHARE + "/report/" + reportId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .get();
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

}
