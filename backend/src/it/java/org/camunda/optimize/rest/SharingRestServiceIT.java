package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    ReportShareDto share = createReportShare(reportId);

    // when
    Response response = createReportShareResponse(token, share);

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

    DashboardShareDto sharingDto = new DashboardShareDto();
    sharingDto.setDashboardId(dashboard);
    sharingDto.setType(SharedResourceType.DASHBOARD);

    // when
    Response response = createDashboardShareResponse(token, sharingDto);

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
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    String id = addShareForReport(token, reportId);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT + "/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(token, FAKE_REPORT_ID), is(nullValue()));
  }

  @Test
  public void deleteDashboardShare() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    String id = addShareForDashboard(token, dashboardWithReport);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + id)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(token, reportId), is(nullValue()));
  }

  @Test
  public void findShareForReport() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String id = addShareForReport(token, reportId);

    //when
    ReportShareDto share = getShareForReport(token, reportId);

    //then
    assertThat(share, is(notNullValue()));
    assertThat(share.getId(), is(id));
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
  public void findShareForSharedDashboard() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    String id = addShareForDashboard(token, dashboardWithReport);

    //when
    DashboardShareDto share = findShareForDashboard(token, dashboardWithReport).readEntity(DashboardShareDto.class);

    //then
    assertThat(share, is(notNullValue()));
    assertThat(share.getId(), is(id));
  }

  @Test
  public void evaluateSharedDashboard() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    String dashboardShareId = addShareForDashboard(token, dashboardWithReport);

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    EvaluatedDashboardShareDto dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    //then
    assertThat(dashboardShareDto, is(notNullValue()));
    assertThat(dashboardShareDto.getId(), is(dashboardShareId));
    assertThat(dashboardShareDto.getDashboard(), is(notNullValue()));
    assertThat(dashboardShareDto.getDashboard().getReportShares(), is(notNullValue()));
    assertThat(dashboardShareDto.getDashboard().getReportShares().size(), is(1));

    //when
    String reportShareId = dashboardShareDto.getDashboard().getReportShares().get(0).getShareId();
    response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(reportShareId))
        .request()
        .get();
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);

    assertReportData(reportId, reportShareId, evaluatedReportAsMap);
  }

  @Test
  public void findShareForDashboardWithoutAuthentication() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    addShareForDashboard(token, dashboardWithReport);

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

  private Response findShareForDashboard(String token, String dashboardId) {
    return embeddedOptimizeRule.target(SHARE + "/dashboard/" + dashboardId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, BEARER + token)
      .get();
  }

  private String addShareForFakeReport(String token) {
    return addShareForReport(token, FAKE_REPORT_ID);
  }

}
