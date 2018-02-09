package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
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
public class SharingRestServiceIT extends AbstractSharingIT {

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
    Response response = createShareResponse(token, createReportShare());

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void sharesRemovedOnReportDeletion() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    this.addShareForReport(token, reportId);

    // when
    embeddedOptimizeRule.target("report/" + reportId)
      .request()
      .header(HttpHeaders.AUTHORIZATION, BEARER + token)
      .delete();

    //then
    SharingDto share = getShareForReport(token, reportId);
    assertThat(share, is(nullValue()));
  }

  @Test
  public void createNewReportShare() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    SharingDto share = createReportShare(reportId);

    // when
    Response response = createShareResponse(token, share);

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
    Response response = createShareResponse(token, sharingDto);

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
    Response response = createShareResponse(token, sharingDto);

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

    Response response = createShareResponse(token, dashboardShare);

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
    Response response = createShareResponse(token, share);

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
    SharingDto share = getShareForReport(token, reportId);

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
    SharingDto share = findShareForDashboard(token, dashboardWithReport).readEntity(SharingDto.class);

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
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);

    //then
    assertThat(response.getStatus(), is(200));
    assertReportData(reportId, shareId, evaluatedReportAsMap);
  }

  private void assertReportData(String reportId, String shareId, HashMap evaluatedReportAsMap) {
    assertThat(evaluatedReportAsMap, is(notNullValue()));
    assertThat(evaluatedReportAsMap.get("id"), is(shareId));
    Map reportMap = (Map) evaluatedReportAsMap.get("report");
    assertThat(reportMap.get("id"), is(reportId));
    assertThat(reportMap.get("data"), is(notNullValue()));
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
