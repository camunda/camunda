/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareDto;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.HashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class SharingRestServiceIT extends AbstractSharingIT {

  @Test
  public void checkShareStatusWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCheckSharingStatusRequest(null)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewReportShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildShareReportRequest(null)
        .withoutAuthentication()
        .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewDashboardShareWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildShareDashboardRequest(null)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void createNewReportShare() {
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
  public void createNewReportShareWithSharingDisabled() {
    //given
    String reportId = createReport();
    embeddedOptimizeExtensionRule.getConfigurationService().setSharingEnabled(false);
    ReportShareDto share = createReportShare(reportId);

    // when
    Response response = createReportShareResponse(share);

    // then
    assertThat(response.getStatus(), is(500));
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
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteReportShareRequest("1124")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNonExistingReportShare() {
    // when
    Response response = embeddedOptimizeExtensionRule
            .getRequestExecutor()
            .buildDeleteReportShareRequest("nonExistingId")
            .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void deleteDashboardShareWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildDeleteDashboardShareRequest("1124")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void deleteNonExistingDashboardShare() {
    // when
    Response response = embeddedOptimizeExtensionRule
            .getRequestExecutor()
            .buildDeleteDashboardShareRequest("nonExistingId")
            .execute();

    // then
    assertThat(response.getStatus(), is(404));
  }

  @Test
  public void deleteReportShare() {
    //given
    String reportId = createReport();
    String id = addShareForReport(reportId);

    // when
    Response response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildDeleteReportShareRequest(id)
        .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(FAKE_REPORT_ID), is(nullValue()));
  }

  @Test
  public void deleteDashboardShare() {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String id = addShareForDashboard(dashboardWithReport);

    // when
    Response response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildDeleteDashboardShareRequest(id)
        .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(204));
    assertThat(getShareForReport(reportId), is(nullValue()));
  }

  @Test
  public void findShareForReport() {
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
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildFindShareForReportRequest(FAKE_REPORT_ID)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void findShareForSharedDashboard() {
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
  public void evaluateSharedDashboard() {
    //given
    String reportId = createReport();
    String dashboardId = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    //when
    DashboardDefinitionDto dashboardShareDto = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSharedDashboardRequest(dashboardShareId)
      .execute(DashboardDefinitionDto.class, 200);

    //then
    assertThat(dashboardShareDto, is(notNullValue()));
    assertThat(dashboardShareDto.getId(), is(dashboardId));
    assertThat(dashboardShareDto.getReports(), is(notNullValue()));
    assertThat(dashboardShareDto.getReports().size(), is(1));

    // when
    String reportShareId = dashboardShareDto.getReports().get(0).getId();
    HashMap evaluatedReportAsMap =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportShareId)
        .execute(HashMap.class, 200);

    // then
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void findShareForDashboardWithoutAuthentication() {
    //given
    String reportId = createReport();
    String dashboardWithReport = createDashboardWithReport(reportId);
    addShareForDashboard(dashboardWithReport);

    //when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardWithReport)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void evaluationOfNotExistingShareReturnsError() {

    //when
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest("123")
      .execute();

    //then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void checkSharingAuthorizationWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildDashboardShareAuthorizationCheck("1124")
        .withoutAuthentication()
        .execute();

    // then the status code is not authorized
    assertThat(response.getStatus(), is(401));
  }

  @Test
  public void checkSharingAuthorizationIsOkay() {
    //given
    String reportId = createReport();
    String dashboardId = createDashboardWithReport(reportId);

    // when
    Response response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildDashboardShareAuthorizationCheck(dashboardId)
        .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(200));
  }

  @Test
  public void checkSharingAuthorizationResultsInForbidden() {
    //given
    engineIntegrationExtensionRule.addUser("kermit", "kermit");
    engineIntegrationExtensionRule.grantUserOptimizeAccess("kermit");
    String reportId = createReport();
    String dashboardId = createDashboardWithReport(reportId);

    // when
    Response response =
      embeddedOptimizeExtensionRule
        .getRequestExecutor()
        .buildDashboardShareAuthorizationCheck(dashboardId)
        .withUserAuthentication("kermit", "kermit")
        .execute();

    // then the status code is okay
    assertThat(response.getStatus(), is(403));
  }

  private Response findShareForDashboard(String dashboardId) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardId)
      .execute();
  }

  private void addShareForFakeReport() {
    addShareForReport(FAKE_REPORT_ID);
  }

}
