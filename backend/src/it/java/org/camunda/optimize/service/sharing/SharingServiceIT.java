package org.camunda.optimize.service.sharing;

import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/it/it-applicationContext.xml"})
public class SharingServiceIT extends AbstractSharingIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

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
      embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(shareId))
        .request()
        .get();
    assertThat(response.getStatus(),is(200));

    //when
    response =
      embeddedOptimizeRule.target(SHARE + "/" + shareId)
        .request()
        .header(HttpHeaders.AUTHORIZATION, BEARER + token)
        .delete();
    assertThat(response.getStatus(),is(204));

    //then
    response =
        embeddedOptimizeRule.target(getSharedDashboardEvaluationPath(shareId))
            .request()
            .get();
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void newIdGeneratedAfterDeletion() throws Exception {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    String reportId = createReport();
    String shareId = this.addShareForReport(token, reportId);

    //when
    Response response =
        embeddedOptimizeRule.target(SHARE + "/" + shareId)
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .delete();
    assertThat(response.getStatus(),is(204));

    String newShareId = this.addShareForReport(token, reportId);
    assertThat(shareId,is(not(newShareId)));
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

    SharingDto findApiReport = getShareForReport(token, reportId);
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
