package org.camunda.optimize.service.sharing;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareLocationDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharedResourceType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */

public class SharingServiceIT extends AbstractSharingIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Test
  public void dashboardsWithDuplicateReportsAreShared() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize(token);
    addReportToDashboard(dashboardId, reportId, reportId);

    String dashboardShareId = addShareForDashboard(token, dashboardId);

    // when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();

    //then
    EvaluatedDashboardShareDto dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    List<ReportShareLocationDto> reportShares = dashboardShareDto.getDashboard().getReportShares();
    assertThat(reportShares.size(), is(2));
    assertThat(reportShares.get(0).getShareId(), is(not(reportShares.get(1).getShareId())));
    assertThat(reportShares.get(0).getPosition().getX(), is(not(reportShares.get(1).getPosition().getX())));
  }

  @Test
  public void individualReportShareIsNotAffectedByDashboard() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize(token);
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(token, dashboardId);

    String reportShareId = addShareForReport(token, reportId2);

    // when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();
    assertThat(response.getStatus(), is(204));

    response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(reportShareId))
        .request()
        .get();
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);

    assertReportData(reportId2, reportShareId, evaluatedReportAsMap);
  }

  @Test
  public void reportSharesOfDashboardsAreIndependent() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize(token);
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(token, dashboardId);

    String dashboardId2 = addEmptyDashboardToOptimize(token);
    assertThat(dashboardId, is(not(dashboardId2)));
    addReportToDashboard(dashboardId2, reportId, reportId2);
    String dashboardShareId2 = addShareForDashboard(token, dashboardId2);

    // when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId2))
        .request()
        .get();
    EvaluatedDashboardShareDto dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    assertThat(dashboardShareDto.getDashboard().getReportShares().size(), is(2));

    response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();
    assertThat(response.getStatus(), is(204));

    //then
    response =
        embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
            .request()
            .get();

    assertThat(response.getStatus(), is(500));

    response =
        embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId2))
            .request()
            .get();
    dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    assertThat(dashboardShareDto.getDashboard().getReportShares().size(), is(2));
  }


  @Test
  public void removingReportFromDashboardRemovesRespectiveShare() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    String dashboardShareId = addShareForDashboard(token, dashboardWithReport);

    //when
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardWithReport);
    updateDashboard(dashboardWithReport, fullBoard);

    //then
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    EvaluatedDashboardShareDto dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    assertThat(dashboardShareDto.getDashboard().getReportShares().size(), is(0));
  }

  @Test
  public void addingReportToDashboardAddsRespectiveShare() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String dashboardId = addEmptyDashboardToOptimize(token);
    String dashboardShareId = addShareForDashboard(token, dashboardId);

    //when
    String reportId = createReport();
    addReportToDashboard(dashboardId, reportId);

    //then
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    EvaluatedDashboardShareDto dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    assertThat(dashboardShareDto.getDashboard().getReportShares().size(), is(1));
  }

  @Test
  public void unsharedDashboardRemovesNotStandaloneReportShares() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    String dashboardShareId = addShareForDashboard(token, dashboardWithReport);
    String reportShareId = addShareForReport(token, reportId);

    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    EvaluatedDashboardShareDto dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    String dashboardReportShareId = dashboardShareDto.getDashboard().getReportShares().get(0).getShareId();

    // when
    response =
      embeddedOptimizeRule.target(SHARE + "/" + DASHBOARD + "/" + dashboardShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();
    assertThat(response.getStatus(), is(204));

    //then
    response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(dashboardReportShareId))
        .request()
        .get();
    assertThat(response.getStatus(), is(500));

    response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(reportShareId))
        .request()
        .get();
    HashMap evaluatedReportAsMap = response.readEntity(HashMap.class);

    assertReportData(reportId, reportShareId, evaluatedReportAsMap);
  }

  @Test
  public void cantEvaluateDashboardOverReportsEndpoint() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    String dashboardShareId = addShareForDashboard(token, dashboardWithReport);

    //when
    Response response =
        embeddedOptimizeRule.target(getSharedReportEvaluationPath(dashboardShareId))
            .request()
            .get();

    //then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void createNewFakeReportShareThrowsError() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    Response response = createReportShareResponse(token, createReportShare());

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantCreateDashboardReportShare() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    ReportShareDto sharingDto = new ReportShareDto();
    sharingDto.setReportId(FAKE_REPORT_ID);
    sharingDto.setType(SharedResourceType.DASHBOARD_REPORT);

    // when
    Response response = createReportShareResponse(token, sharingDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createNewFakeDashboardShareThrowsError() {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    // when
    DashboardShareDto dashboardShare = new DashboardShareDto();
    dashboardShare.setDashboardId(FAKE_REPORT_ID);
    dashboardShare.setType(SharedResourceType.DASHBOARD);

    Response response = createDashboardShareResponse(token, dashboardShare);

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    ReportShareDto share = createReportShare(reportId);

    // when
    Response response = createReportShareResponse(token, share);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));

    response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .post(Entity.json(share));

    assertThat(id, is(response.readEntity(String.class)));
  }

  @Test
  public void cantEvaluateNotExistingReportShare() {
    //when
    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(FAKE_REPORT_ID))
        .request()
        .get();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantEvaluateNotExistingDashboardShare() {

    //when
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(FAKE_REPORT_ID))
        .request()
        .get();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantEvaluateUnsharedReport() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    String shareId = this.addShareForReport(token, reportId);

    Response response =
      embeddedOptimizeRule.target(getSharedReportEvaluationPath(shareId))
        .request()
        .get();
    assertThat(response.getStatus(),is(200));

    //when
    response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT + "/" + shareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();
    assertThat(response.getStatus(),is(204));

    //then
    response =
        embeddedOptimizeRule.target(getSharedReportEvaluationPath(shareId))
            .request()
            .get();
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void newIdGeneratedAfterDeletion() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    String reportShareId = this.addShareForReport(token, reportId);

    //when
    Response response =
      embeddedOptimizeRule.target(SHARE + "/" + REPORT + "/" + reportShareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();
    assertThat(response.getStatus(),is(204));

    String newShareId = this.addShareForReport(token, reportId);
    assertThat(reportShareId,is(not(newShareId)));
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
    ReportShareDto share = getShareForReport(token, reportId);
    assertThat(share, is(nullValue()));
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

  @Test
  public void canCreateReportShareIfDashboardIsShared() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();

    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(token, reportId);
    String dashboardShareId = addShareForDashboard(token, dashboardWithReport);

    //when
    String reportShareId = addShareForReport(token, reportId);
    //then
    assertThat(reportShareId, is(notNullValue()));

    assertThatReportShareIdIsNotEqualToDashboard(dashboardShareId, reportShareId);

    ReportShareDto findApiReport = getShareForReport(token, reportId);
    assertThat(dashboardShareId, is(not(findApiReport.getId())));
  }

  private void assertThatReportShareIdIsNotEqualToDashboard(String dashboardShareId, String reportShareId) {
    Response response =
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(dashboardShareId))
        .request()
        .get();
    EvaluatedDashboardShareDto dashboardShareDto = response.readEntity(EvaluatedDashboardShareDto.class);
    String dashboardReportShareId = dashboardShareDto.getDashboard().getReportShares().get(0).getShareId();

    assertThat(reportShareId, is(not(dashboardReportShareId)));
  }
}
