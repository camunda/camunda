/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.sharing;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.dashboard.ReportLocationDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.exceptions.evaluation.ReportEvaluationException;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.camunda.optimize.test.it.rule.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SharingServiceIT extends AbstractSharingIT {

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule);

  @Test
  public void dashboardWithoutReportsShare() {
    //given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute(DashboardDefinitionDto.class, 200);

    //then
    List<ReportLocationDto> reportLocations = dashboardShareDto.getReports();
    assertThat(reportLocations.size(), is(0));
  }

  @Test
  public void dashboardsWithDuplicateReportsAreShared() {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId);

    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute(DashboardDefinitionDto.class, 200);

    // then
    List<ReportLocationDto> reportLocation = dashboardShareDto.getReports();
    assertThat(reportLocation.size(), is(2));
    assertThat(reportLocation.get(0).getPosition().getX(), is(not(reportLocation.get(1).getPosition().getX())));
  }

  @Test
  public void individualReportShareIsNotAffectedByDashboard() {
    //given
    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String reportShareId = addShareForReport(reportId2);

    // when
    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteDashboardShareRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus(), is(204));

    HashMap evaluatedReportAsMap = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(reportShareId)
            .execute(HashMap.class, 200);

    // then
    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void shareDashboardWithExternalResourceReport () {
    // given
    String dashboardId = addEmptyDashboardToOptimize();
    String externalResourceReportId = "";
    addReportToDashboard(dashboardId, externalResourceReportId);

    // when
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = createDashboardShareResponse(share);

    // then
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void canEvaluateEveryReportOfSharedDashboard() {
    //given
    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildFindShareForDashboardRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus(), is(204));

    // then
    HashMap evaluatedReportAsMap = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId)
            .execute(HashMap.class, 200);

    assertReportData(reportId, evaluatedReportAsMap);

    evaluatedReportAsMap = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportId2)
            .execute(HashMap.class, 200);

    assertReportData(reportId2, evaluatedReportAsMap);
  }

  @Test
  public void sharedDashboardReportsCannotBeEvaluateViaSharedReport() {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(reportId)
            .execute();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void evaluateUnknownReportOfSharedDashboardThrowsError() {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    String dashboardShareId = addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(dashboardShareId, FAKE_REPORT_ID)
            .execute();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void evaluateUnknownSharedDashboardThrowsError() {
    //given
    String reportId = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId);

    // when
    addShareForDashboard(dashboardId);

    // then
    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest("fakedashboardshareid", reportId)
            .execute();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void reportSharesOfDashboardsAreIndependent() {
    //given
    String reportId = createReport();
    String reportId2 = createReport();
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId, reportId2);
    String dashboardShareId = addShareForDashboard(dashboardId);

    String dashboardId2 = addEmptyDashboardToOptimize();
    assertThat(dashboardId, is(not(dashboardId2)));
    addReportToDashboard(dashboardId2, reportId, reportId2);
    String dashboardShareId2 = addShareForDashboard(dashboardId2);

    // when
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId2)
            .execute(DashboardDefinitionDto.class, 200);

    assertThat(dashboardShareDto.getReports().size(), is(2));

    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteDashboardShareRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus(), is(204));

    //then
    response =
        embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus(), is(500));

    dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId2)
            .execute(DashboardDefinitionDto.class, 200);

    assertThat(dashboardShareDto.getReports().size(), is(2));
  }

  @Test
  public void removingReportFromDashboardRemovesRespectiveShare() {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardWithReport);
    updateDashboard(dashboardWithReport, fullBoard);

    //then
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute(DashboardDefinitionDto.class, 200);

    assertThat(dashboardShareDto.getReports().size(), is(0));
  }

  @Test
  public void updateDashboardShareMoreThanOnce() {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);
    DashboardDefinitionDto fullBoard = new DashboardDefinitionDto();
    fullBoard.setId(dashboardWithReport);
    updateDashboard(dashboardWithReport, fullBoard);

    //when
    Response response = updateDashboard(dashboardWithReport, fullBoard);

    //then
    assertThat(response.getStatus(), is(204));
  }

  @Test
  public void updatingDashboardUpdatesRespectiveShare() {
    // given
    final String shouldBeIgnoredString = "shouldNotBeUpdated";
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    ReportLocationDto reportLocationDto = new ReportLocationDto();
    final String reportId = createNewReport();
    reportLocationDto.setId(reportId);
    reportLocationDto.setConfiguration("testConfiguration");
    DashboardDefinitionDto dashboard = new DashboardDefinitionDto();
    dashboard.setReports(Collections.singletonList(reportLocationDto));
    dashboard.setId(shouldBeIgnoredString);
    dashboard.setLastModifier("shouldNotBeUpdatedManually");
    dashboard.setName("MyDashboard");
    OffsetDateTime shouldBeIgnoredDate = OffsetDateTime.now().plusHours(1);
    dashboard.setCreated(shouldBeIgnoredDate);
    dashboard.setLastModified(shouldBeIgnoredDate);
    dashboard.setOwner(shouldBeIgnoredString);

    // when
    updateDashboard(dashboardId, dashboard);
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute(DashboardDefinitionDto.class, 200);

    // then
    assertThat(dashboardShareDto.getReports().size(), is(1));
    ReportLocationDto retrievedLocation = dashboardShareDto.getReports().get(0);
    assertThat(retrievedLocation.getId(), is(reportId));
    assertThat(retrievedLocation.getConfiguration(), is("testConfiguration"));
    assertThat(dashboardShareDto.getId(), is(dashboardId));
    assertThat(dashboardShareDto.getCreated(), is(not(shouldBeIgnoredDate)));
    assertThat(dashboardShareDto.getLastModified(), is(not(shouldBeIgnoredDate)));
    assertThat(dashboardShareDto.getName(), is("MyDashboard"));
    assertThat(dashboardShareDto.getOwner(), is(DEFAULT_USERNAME));
  }

  @Test
  public void addingReportToDashboardAddsRespectiveShare() {
    //given
    String dashboardId = addEmptyDashboardToOptimize();
    String dashboardShareId = addShareForDashboard(dashboardId);

    //when
    String reportId = createReport();
    addReportToDashboard(dashboardId, reportId);

    //then
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute(DashboardDefinitionDto.class, 200);

    assertThat(dashboardShareDto.getReports().size(), is(1));
  }

  @Test
  public void unsharedDashboardRemovesNotStandaloneReportShares() {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);
    String reportShareId = addShareForReport(reportId);

    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute(DashboardDefinitionDto.class, 200);
    String dashboardReportShareId = dashboardShareDto.getReports().get(0).getId();

    // when
    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteDashboardShareRequest(dashboardShareId)
            .execute();

    assertThat(response.getStatus(), is(204));

    //then
    response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(dashboardReportShareId)
            .execute();
    assertThat(response.getStatus(), is(500));

    HashMap evaluatedReportAsMap = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(reportShareId)
            .execute(HashMap.class, 200);

    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void cannotEvaluateDashboardOverReportsEndpoint() {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    Response response =
        embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(dashboardShareId)
            .execute();

    //then
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void createNewFakeReportShareThrowsError() {

    // when
    Response response = createReportShareResponse(createReportShare());

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantCreateDashboardReportShare() {
    //given
    ReportShareDto sharingDto = new ReportShareDto();
    sharingDto.setReportId(FAKE_REPORT_ID);

    // when
    Response response = createReportShareResponse(sharingDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void createNewFakeDashboardShareThrowsError() {
    //given
    DashboardShareDto dashboardShare = new DashboardShareDto();
    dashboardShare.setDashboardId(FAKE_REPORT_ID);

    // when
    Response response = createDashboardShareResponse(dashboardShare);

    // then the status code is okay
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void shareIsNotCreatedForSameResourceTwice() {
    //given
    String reportId = createReport();
    ReportShareDto share = createReportShare(reportId);

    // when
    Response response = createReportShareResponse(share);

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
    String id =
        response.readEntity(String.class);
    assertThat(id, is(notNullValue()));

    response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildShareReportRequest(share)
            .execute();

    assertThat(id, is(response.readEntity(String.class)));
  }

  @Test
  public void cantEvaluateNotExistingReportShare() {
    //when
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(FAKE_REPORT_ID)
            .execute();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantEvaluateNotExistingDashboardShare() {

    //when
    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(FAKE_REPORT_ID)
            .execute();

    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void cantEvaluateUnsharedReport() {
    //given
    String reportId = createReport();
    String shareId = this.addShareForReport(reportId);

    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(shareId)
            .execute();
    assertThat(response.getStatus(),is(200));

    //when
    response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteReportShareRequest(shareId)
            .execute();

    assertThat(response.getStatus(),is(204));

    //then
    response =
        embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(shareId)
            .execute();
    assertThat(response.getStatus(),is(500));
  }

  @Test
  public void newIdGeneratedAfterDeletion() {
    String reportId = createReport();
    String reportShareId = this.addShareForReport(reportId);

    //when
    Response response =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteReportShareRequest(reportShareId)
            .execute();

    assertThat(response.getStatus(),is(204));

    String newShareId = this.addShareForReport(reportId);
    assertThat(reportShareId,is(not(newShareId)));
  }

  @Test
  public void sharesRemovedOnReportDeletion() {
    //given
    String reportId = createReport();
    this.addShareForReport(reportId);

    // when
    embeddedOptimizeRule
            .getRequestExecutor()
            .buildDeleteReportRequest(reportId)
            .execute();

    //then
    ReportShareDto share = getShareForReport(reportId);
    assertThat(share, is(nullValue()));
  }

  @Test
  public void canEvaluateSharedReportWithoutAuthentication() {
    // given
    String reportId = createReport();

    String shareId = addShareForReport(reportId);

    //when
    HashMap evaluatedReportAsMap = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedReportRequest(shareId)
            .execute(HashMap.class, 200);

    //then
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void canCheckDashboardSharingStatus() {
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);

    addShareForDashboard(dashboardWithReport);

    ShareSearchDto statusRequest = new ShareSearchDto();
    statusRequest.getDashboards().add(dashboardWithReport);
    statusRequest.getReports().add(reportId);

    String dashboardWithReport2 = createDashboardWithReport(reportId);
    statusRequest.getDashboards().add(dashboardWithReport2);
    //when

    ShareSearchResultDto result =
      embeddedOptimizeRule
            .getRequestExecutor()
            .buildCheckSharingStatusRequest(statusRequest)
            .execute(ShareSearchResultDto.class, 200);

    //then
    assertThat(result.getDashboards().size(), is(2));
    assertThat(result.getDashboards().get(dashboardWithReport), is(true));
    assertThat(result.getDashboards().get(dashboardWithReport2), is(false));

    assertThat(result.getReports().size(), is(1));
    assertThat(result.getReports().get(reportId), is(false));
  }

  @Test
  public void canCheckReportSharingStatus() {
    String reportId = createReport();
    addShareForReport(reportId);

    ShareSearchDto statusRequest = new ShareSearchDto();
    statusRequest.getReports().add(reportId);
    String reportId2 = createReport();
    statusRequest.getReports().add(reportId2);

    //when
    ShareSearchResultDto result =
        embeddedOptimizeRule
          .getRequestExecutor()
          .buildCheckSharingStatusRequest(statusRequest)
          .execute(ShareSearchResultDto.class, 200);

    // then
    assertThat(result.getReports().size(), is(2));
    assertThat(result.getReports().get(reportId), is(true));
    assertThat(result.getReports().get(reportId2), is(false));
  }

  @Test
  public void canCreateReportShareIfDashboardIsShared() {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    String reportShareId = addShareForReport(reportId);

    //then
    assertThat(reportShareId, is(notNullValue()));

    ReportShareDto findApiReport = getShareForReport(reportId);
    assertThat(dashboardShareId, is(not(findApiReport.getId())));
  }

  @Test
  public void errorMessageIsWellStructured () {
    //given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess("aProcess");
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    String reportId = this.createNewReport();
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
      .setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion())
      .setReportDataType(ProcessReportDataType.COUNT_FLOW_NODE_FREQ_GROUP_BY_FLOW_NODE)
      .build();
    reportData.setView(null);
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    updateReport(reportId, report);

    String dashboardWithReport = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardWithReport);

    //when
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardRequest(dashboardShareId)
            .execute(DashboardDefinitionDto.class, 200);

    ReportEvaluationException errorResponse = embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSharedDashboardReportRequest(
                    dashboardShareId,
                    dashboardShareDto.getReports().get(0).getId()
            )
            .execute(ReportEvaluationException.class, 500);

    //then
    AbstractSharingIT.assertErrorFields(errorResponse);
  }

  @Test
  public void shareUnauthorizedDashboard() {
    // given
    engineRule.addUser("kermit", "kermit");
    engineRule.grantUserOptimizeAccess("kermit");
    String reportId1 = createReport("processDefinition1");
    String reportId2 = createReport("processDefinition2");
    String dashboardId = addEmptyDashboardToOptimize();
    addReportToDashboard(dashboardId, reportId1, reportId2);

    grantSingleDefinitionAuthorizationsForUser("kermit", "processDefinition1");

    // when I want to share the dashboard as kermit and kermit has no access to report 2
    DashboardShareDto share = createDashboardShareDto(dashboardId);
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildShareDashboardRequest(share)
            .withUserAuthentication("kermit", "kermit")
            .execute();

    // then
    assertThat(response.getStatus(), is(403));
  }

}
