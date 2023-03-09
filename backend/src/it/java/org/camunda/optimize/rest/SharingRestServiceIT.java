/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.SneakyThrows;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_RETRIES_ON_CONFLICT;

public class SharingRestServiceIT extends AbstractSharingIT {

  @Test
  public void checkShareStatusWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCheckSharingStatusRequest(null)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createNewReportShareWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildShareReportRequest(null)
        .withoutAuthentication()
        .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createNewDashboardShareWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildShareDashboardRequest(null)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void createNewReportShare() {
    // given
    String reportId = createReportWithInstance();
    ReportShareRestDto share = createReportShare(reportId);

    // when
    Response response = sharingClient.createReportShareResponse(share);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String id = response.readEntity(String.class);
    assertThat(id).isNotNull();
  }

  @Test
  public void createNewReportShareWithSharingDisabled() {
    // given
    String reportId = createReportWithInstance();
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(false);
    ReportShareRestDto share = createReportShare(reportId);

    // when
    Response response = sharingClient.createReportShareResponse(share);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewReportShareForManagementReport() {
    // given
    String reportId = createManagementReport();
    ReportShareRestDto share = createReportShare(reportId);

    // when
    Response response = sharingClient.createReportShareResponse(share);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void createNewDashboardShare() {
    // given
    String dashboard = addEmptyDashboardToOptimize();

    DashboardShareRestDto sharingDto = new DashboardShareRestDto();
    sharingDto.setDashboardId(dashboard);

    // when
    Response response = sharingClient.createDashboardShareResponse(sharingDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    String id = response.readEntity(String.class);
    assertThat(id).isNotNull();
  }

  @Test
  public void createNewReportShareForManagementDashboard() {
    // given
    String dashboardId = createManagementDashboard();
    final DashboardShareRestDto dashboardShareDto = createDashboardShareDto(dashboardId);

    // when
    Response response = sharingClient.createDashboardShareResponse(dashboardShareDto);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  @Test
  public void deleteReportShareWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportShareRequest("1124")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void deleteNonExistingReportShare() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteReportShareRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void deleteDashboardShareWithoutAuthentication() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardShareRequest("1124")
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void deleteNonExistingDashboardShare() {
    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildDeleteDashboardShareRequest("nonExistingId")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  @Test
  public void deleteReportShare() {
    // given
    String reportId = createReportWithInstance();
    String id = addShareForReport(reportId);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteReportShareRequest(id)
        .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getShareForReport(FAKE_REPORT_ID)).isNull();
  }

  @Test
  public void deleteDashboardShare() {
    // given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String id = addShareForDashboard(dashboardWithReport);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDeleteDashboardShareRequest(id)
        .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(getShareForReport(reportId)).isNull();
  }

  @Test
  public void findShareForReport() {
    // given
    String reportId = createReportWithInstance();
    String id = addShareForReport(reportId);

    // when
    ReportShareRestDto share = getShareForReport(reportId);

    // then
    assertThat(share).isNotNull();
    assertThat(share.getId()).isEqualTo(id);
  }

  @Test
  public void findShareForReportWithoutAuthentication() {
    // given
    addShareForFakeReport();

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildFindShareForReportRequest(FAKE_REPORT_ID)
      .withoutAuthentication()
      .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void findShareForSharedDashboard() {
    // given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    String id = addShareForDashboard(dashboardWithReport);

    // when
    DashboardShareRestDto share = findShareForDashboard(dashboardWithReport).readEntity(DashboardShareRestDto.class);

    // then
    assertThat(share).isNotNull();
    assertThat(share.getId()).isEqualTo(id);
  }

  @Test
  public void evaluateSharedDashboard() {
    // given
    String reportId = createReportWithInstance();
    String dashboardId = createDashboardWithReport(reportId);
    String dashboardShareId = addShareForDashboard(dashboardId);

    // when
    DashboardDefinitionRestDto dashboardShareDto = sharingClient.evaluateDashboard(dashboardShareId);

    // then
    assertThat(dashboardShareDto).isNotNull();
    assertThat(dashboardShareDto.getId()).isEqualTo(dashboardId);
    assertThat(dashboardShareDto.getTiles()).isNotNull();
    assertThat(dashboardShareDto.getTiles()).hasSize(1);

    // when
    String reportShareId = dashboardShareDto.getTiles().get(0).getId();
    HashMap<?, ?> evaluatedReportAsMap =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildEvaluateSharedDashboardReportRequest(dashboardShareId, reportShareId)
        .execute(HashMap.class, Response.Status.OK.getStatusCode());

    // then
    assertReportData(reportId, evaluatedReportAsMap);
  }

  @Test
  public void findShareForDashboardWithoutAuthentication() {
    // given
    String reportId = createReportWithInstance();
    String dashboardWithReport = createDashboardWithReport(reportId);
    addShareForDashboard(dashboardWithReport);

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardWithReport)
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void evaluationOfNotExistingShareReturnsError() {

    // when
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildEvaluateSharedReportRequest("123")
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void checkSharingAuthorizationWithoutAuthentication() {
    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDashboardShareAuthorizationCheck("1124")
        .withoutAuthentication()
        .execute();

    // then the status code is not authorized
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @Test
  public void checkSharingAuthorizationIsOkay() {
    // given
    String reportId = createReportWithInstance();
    String dashboardId = createDashboardWithReport(reportId);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDashboardShareAuthorizationCheck(dashboardId)
        .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void checkSharingAuthorizationResultsInForbidden() {
    // given
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantUserOptimizeAccess("kermit");
    String reportId = createReportWithInstance();
    String dashboardId = createDashboardWithReport(reportId);

    // when
    Response response =
      embeddedOptimizeExtension
        .getRequestExecutor()
        .buildDashboardShareAuthorizationCheck(dashboardId)
        .withUserAuthentication("kermit", "kermit")
        .execute();

    // then the status code is okay
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  private Response findShareForDashboard(String dashboardId) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildFindShareForDashboardRequest(dashboardId)
      .execute();
  }

  private void addShareForFakeReport() {
    addShareForReport(FAKE_REPORT_ID);
  }

  @SneakyThrows
  private String createManagementReport() {
    // The initial report is created for a specific process
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey("procDefKey")
      .setProcessDefinitionVersion("1")
      .setReportDataType(PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    final String reportId = reportClient.createSingleProcessReport(
      new SingleProcessReportDefinitionRequestDto(reportData));

    final UpdateRequest update = new UpdateRequest()
      .index(ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME)
      .id(reportId)
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.data.managementReport = true",
        Collections.emptyMap()
      ))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().update(update);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return reportId;
  }

  @SneakyThrows
  protected String createManagementDashboard() {
    final String dashboardId = dashboardClient.createEmptyDashboard();
    final UpdateRequest update = new UpdateRequest()
      .index(DASHBOARD_INDEX_NAME)
      .id(dashboardId)
      .script(new Script(
        ScriptType.INLINE,
        Script.DEFAULT_SCRIPT_LANG,
        "ctx._source.managementDashboard = true",
        Collections.emptyMap()
      ))
      .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().update(update);
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return dashboardId;
  }

}
