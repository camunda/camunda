package org.camunda.optimize.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class SharingRestServiceIT {

  public static final String BEARER = "Bearer ";
  public static final String SHARE = "share";
  public static final String FAKE_REPORT_ID = "fake";
  public static final String EVALUATE = "evaluate";
  public static final String REPORT = "report";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Test
  public void createNewShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewFakeReportShareThrowsError() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(createReportShare()));

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createNewReportShare() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    SharingDto share = createReportShare(reportId);

    // when
    Response response =
        embeddedOptimizeRule.target(SHARE)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(share));

    // then
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void createNewDashboardShare() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String dashboard = addEmptyDashboardToOptimize(token);

    SharingDto sharingDto = new SharingDto();
    sharingDto.setResourceId(dashboard);
    sharingDto.setType(SharedResourceType.DASHBOARD);

    // when
    Response response =
        embeddedOptimizeRule.target(SHARE)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(sharingDto));

    // then
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void cantCreateDashboardReportShare() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    SharingDto sharingDto = new SharingDto();
    sharingDto.setResourceId(FAKE_REPORT_ID);
    sharingDto.setType(SharedResourceType.DASHBOARD_REPORT);

    // when
    Response response =
        embeddedOptimizeRule.target(SHARE)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(sharingDto));

    // then
    assertThat(response.getStatus(), is(500));
  }



  @Test
  public void createNewFakeDashboardShareThrowsError() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    SharingDto dashboardShare = new SharingDto();
    dashboardShare.setResourceId(FAKE_REPORT_ID);
    dashboardShare.setType(SharedResourceType.DASHBOARD);

    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(dashboardShare));

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    SharingDto share = createReportShare(reportId);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));

    response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));

    assertThat(id, is(response.readEntity(String.class)));
  }

  @Test
  public void deleteShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/1124")
        .request()
        .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteShare() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    String id = addShareForReport(token, reportId);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(token, FAKE_REPORT_ID), is(nullValue()));
  }

  @Test
  public void findShareForReport() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String id = addShareForReport(token, reportId);

    //when
    SharingDto fake = getShareForReport(token, reportId);

    //then
    assertThat(fake, is(notNullValue()));
    assertThat(fake.getId(), is(id));
  }

  @Test
  public void findShareForReportWithoutAuthentication() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    addShareForFakeReport(token);

    Response response = findShareForReport(null, FAKE_REPORT_ID);

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void canEvaluateSharedReportWithoutAuthentication() throws Exception {
    // given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();

    String shareId = addShareForReport(token, reportId);

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(shareId))
        .request()
        .get();
    HashMap sharingDto = response.readEntity(HashMap.class);

    //then
    assertThat(response.getStatus(), is(200));
    assertThat(sharingDto, is(notNullValue()));
    assertThat(sharingDto.get("id"), is(shareId));
    Map reportMap = (Map) sharingDto.get("report");
    assertThat(reportMap.get("id"), is(reportId));
    assertThat(reportMap.get("data"), is(notNullValue()));
  }

  private String addEmptyDashboardToOptimize(String token) {
    Response response =
        embeddedOptimizeRule.target("dashboard")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .post(Entity.json(""));

    return response.readEntity(IdDto.class).getId();
  }

  private String createReport() throws InterruptedException {
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

  private String getSharedReportEvaluationPath(String shareId) {
    return SHARE + "/"+ REPORT + "/" + shareId + "/" + EVALUATE;
  }

  @Test
  public void evaluationOfNotExistingShareReturnsError() {

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath("123"))
        .request()
        .get();

    //then
    assertThat(response.getStatus(), is(401));
  }

  private SharingDto getShareForReport(String token, String reportId) {
    Response response = findShareForReport(token, reportId);
    return response.readEntity(SharingDto.class);
  }

  private Response findShareForReport(String token, String reportId) {
    return embeddedOptimizeRule.target(SHARE + "/report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, BEARER + token)
      .get();
  }

  private String addShareForFakeReport(String token) {
    return addShareForReport(token, FAKE_REPORT_ID);
  }

  private String addShareForReport(String token, String reportId) {
    SharingDto share = createReportShare(reportId);
    Response response =
      embeddedOptimizeRule.target(SHARE)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));

    return response.readEntity(String.class);
  }

  private SharingDto createReportShare() {
    return createReportShare(FAKE_REPORT_ID);
  }

  private SharingDto createReportShare(String reportId) {
    SharingDto sharingDto = new SharingDto();
    sharingDto.setResourceId(reportId);
    sharingDto.setType(SharedResourceType.REPORT);
    return sharingDto;
  }

  private String createNewReport() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report")
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
      embeddedOptimizeRule.target("report/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
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
}
