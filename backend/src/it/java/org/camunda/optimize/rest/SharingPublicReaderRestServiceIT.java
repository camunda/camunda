/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.sharing.AbstractSharingIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.OptimizeJettyServerCustomizer.EXTERNAL_SUB_PATH;
import static org.camunda.optimize.jetty.OptimizeResourceConstants.REST_API_PATH;
import static org.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_RESOURCE_PATH;
import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_INPUTS_NAMES_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_OUTPUTS_NAMES_PATH;
import static org.camunda.optimize.rest.DecisionVariablesRestService.DECISION_VARIABLES_PATH;
import static org.camunda.optimize.rest.FlowNodeRestService.FLOW_NODE_NAMES_SUB_PATH;
import static org.camunda.optimize.rest.FlowNodeRestService.FLOW_NODE_PATH;
import static org.camunda.optimize.rest.ProcessVariableRestService.PROCESS_VARIABLES_PATH;
import static org.camunda.optimize.rest.SharingRestService.DASHBOARD_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.EVALUATE_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.REPORT_SUB_PATH;
import static org.camunda.optimize.rest.SharingRestService.SHARE_PATH;


public class SharingPublicReaderRestServiceIT extends AbstractSharingIT {

  private static final String EXTERNAL_API_PATH = REST_API_PATH + EXTERNAL_SUB_PATH;
  private String reportShareId;
  private String dashboardShareId;

  @Test
  public void accessingExternalResourcesDirectly_unauthorized() {
    // when
    Response response = embeddedOptimizeExtension
      .rootTarget(REST_API_PATH + CANDIDATE_GROUP_RESOURCE_PATH)
      .request()
      .get();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("publicResourcesIndependentOfSharing")
  public void publicResourcesIndependentOfSharingAvailableWhenSharingDeactivated(final String resourcePath) {
    // given
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(false);
    // when
    Response response = embeddedOptimizeExtension
      .rootTarget(resourcePath)
      .request()
      .get();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("publicResourcesForSharingGet")
  public void publicResourcesAreProtectedWhenSharingDisabledGet(final String resourcePath) {
    // given
    initializeShares();
    String resourcePathProcessed = resourcePath.replace("{dashboardShareId}", dashboardShareId);
    resourcePathProcessed = resourcePathProcessed.replace("{reportShareId}", reportShareId);

    // when
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(false);
    Response response = embeddedOptimizeExtension
      .rootTarget(resourcePathProcessed)
      .request()
      .get();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("publicResourcesForSharingPost")
  public void publicResourcesAreProtectedWhenSharingDisabledPost(final String resourcePath) {
    // given
    initializeShares();
    String resourcePathProcessed = resourcePath.replace("{dashboardShareId}", dashboardShareId);
    resourcePathProcessed = resourcePathProcessed.replace("{reportShareId}", reportShareId);

    // when
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(false);
    Response response = embeddedOptimizeExtension
      .rootTarget(resourcePathProcessed)
      .request()
      .post(Entity.json(""));
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  @ParameterizedTest
  @MethodSource("publicResourcesForSharingGet")
  public void dynamicEnablingDisablingOfSharingWorks(final String resourcePath) {
    // given
    initializeShares();
    String resourcePathProcessed = resourcePath.replace("{dashboardShareId}", dashboardShareId);
    resourcePathProcessed = resourcePathProcessed.replace("{reportShareId}", reportShareId);

    // when
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(false);
    Response response = embeddedOptimizeExtension
      .rootTarget(resourcePathProcessed)
      .request()
      .get();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());

    // when
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(true);
    response = embeddedOptimizeExtension
      .rootTarget(resourcePathProcessed)
      .request()
      .get();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    // when
    embeddedOptimizeExtension.getConfigurationService().setSharingEnabled(false);
    response = embeddedOptimizeExtension
      .rootTarget(resourcePathProcessed)
      .request()
      .get();
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }


  private static Stream<String> publicResourcesIndependentOfSharing() {
    return Stream.of(
      EXTERNAL_API_PATH + UIConfigurationRestService.UI_CONFIGURATION_PATH,
      EXTERNAL_API_PATH + LocalizationRestService.LOCALIZATION_PATH
    );
  }

  private static Stream<String> publicResourcesForSharingGet() {
    return Stream.of(
      EXTERNAL_API_PATH + SHARE_PATH + DASHBOARD_SUB_PATH + "/{dashboardShareId}" + EVALUATE_SUB_PATH,
      EXTERNAL_API_PATH + CANDIDATE_GROUP_RESOURCE_PATH,
      EXTERNAL_API_PATH + ASSIGNEE_RESOURCE_PATH
    );
  }

  private static Stream<String> publicResourcesForSharingPost() {
    return Stream.of(
      EXTERNAL_API_PATH + SHARE_PATH + REPORT_SUB_PATH + "/{reportShareId}" +  EVALUATE_SUB_PATH,
      EXTERNAL_API_PATH + SHARE_PATH + DASHBOARD_SUB_PATH + "/{dashboardShareId}" + REPORT_SUB_PATH +
        "/{reportShareId}" + EVALUATE_SUB_PATH,
      EXTERNAL_API_PATH + PROCESS_VARIABLES_PATH,
      EXTERNAL_API_PATH + DECISION_VARIABLES_PATH + DECISION_INPUTS_NAMES_PATH,
      EXTERNAL_API_PATH + DECISION_VARIABLES_PATH + DECISION_OUTPUTS_NAMES_PATH,
      EXTERNAL_API_PATH + FLOW_NODE_PATH + FLOW_NODE_NAMES_SUB_PATH
    );
  }

  private void initializeShares()
  {
    String reportId = createReportWithInstance();
    ReportShareRestDto share = createReportShare(reportId);
    // when
    Response response = sharingClient.createReportShareResponse(share);
    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    reportShareId = response.readEntity(IdResponseDto.class).getId();
    assertThat(reportShareId).isNotNull();
    String dashboardId = addEmptyDashboardToOptimize();
    DashboardShareRestDto sharingDto = new DashboardShareRestDto();
    sharingDto.setDashboardId(dashboardId);
    response = sharingClient.createDashboardShareResponse(sharingDto);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    dashboardShareId = response.readEntity(IdResponseDto.class).getId();
    assertThat(dashboardShareId).isNotNull();
  }
}
