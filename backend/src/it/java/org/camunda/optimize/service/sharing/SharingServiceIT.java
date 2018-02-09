package org.camunda.optimize.service.sharing;

import org.camunda.optimize.dto.optimize.query.sharing.EvaluatedDashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.SharingDto;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.ws.rs.core.Response;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
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
