package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class SharingRestServiceIT extends AbstractSharingIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Test
  public void createNewReportShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT)
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewDashboardShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD)
        .request()
        .post(Entity.json(""));

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewReportShare() throws Exception {
    //given
    String reportId = createReport();
    ReportShareDto share = createReportShare(reportId);

    // when
    Response response = createReportShareResponse(share);

    // then
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void createNewDashboardShare() {
    //given
    String dashboard = addEmptyDashboardToOptimize();

    DashboardShareDto sharingDto = new DashboardShareDto();
    sharingDto.setDashboardId(dashboard);

    // when
    Response response = createDashboardShareResponse(sharingDto);

    // then
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));
  }

  @Test
  public void deleteReportShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT + "/1124")
        .request()
        .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteDashboardShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/1124")
        .request()
        .delete();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteReportShare() throws Exception {
    //given
    String reportId = createReport();
    String id = addShareForReport(reportId);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT + "/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(FAKE_REPORT_ID), is(nullValue()));
  }

  @Test
  public void deleteDashboardShare() throws Exception {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String id = addShareForDashboard(dashboardWithReport);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
        .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(reportId), is(nullValue()));
  }

  @Test
  public void findShareForReport() throws Exception {
    //given
    String reportId = createReport();
    String id = addShareForReport(reportId);

    //when
    ReportShareDto share = getShareForReport(reportId);

    //then
    assertThat(share, is(notNullValue()));
    assertThat(share.getId(), is(id));
  }

  @Test
  public void findShareForReportWithoutAuthentication() {
    //given
    addShareForFakeReport();

    // when
    Response response = embeddedOptimizeRule.target(SHARE + "/report/" + FAKE_REPORT_ID)
        .request()
        .get();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void findShareForSharedDashboard() throws Exception {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String id = addShareForDashboard(dashboardWithReport);

    //when
    DashboardShareDto share = findShareForDashboard(dashboardWithReport).readEntity(DashboardShareDto.class);

    //then
    assertThat(share, is(notNullValue()));
    assertThat(share.getId(), is(id));
  }

  @Test
  public void evaluateSharedDashboard() throws Exception {
    //given
    String reportId = createReport();
    String dashboardId = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    DashboardDefinitionDto dashboardShareDto = response.readEntity(DashboardDefinitionDto.class);

    //then
    assertThat(dashboardShareDto, is(notNullValue()));
    assertThat(dashboardShareDto.getId(), is(dashboardId));
    assertThat(dashboardShareDto.getReports(), is(notNullValue()));
    assertThat(dashboardShareDto.getReports().size(), is(1));

    // when
    String reportShareId = dashboardShareDto.getReports().get(0).getId();
    response =
      embeddedOptimizeRule.target(getSharedDashboardReportEvaluationPath(dashboardShareId, reportShareId))
        .request()
        .get();

    // then
    assertThat(response.getStatus(), is(200));
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void findShareForDashboardWithoutAuthentication() throws Exception {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    addShareForDashboard(dashboardWithReport);

    //when
    Response response = embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardWithReport)
      .request()
      .get();

    // then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluationOfNotExistingShareReturnsError() {

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath("123"))
        .request()
        .get();

    //then
    assertThat(response.getStatus(), is(500));
  }

  private Response findShareForDashboard(String dashboardId) {
    return embeddedOptimizeRule.target(SHARE + "/dashboard/" + dashboardId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, embeddedOptimizeRule.getAuthorizationHeader())
      .get();
  }

  private void addShareForFakeReport() {
    addShareForReport(FAKE_REPORT_ID);
  }

}
