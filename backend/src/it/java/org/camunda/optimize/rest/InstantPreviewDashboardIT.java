/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.query.dashboard.InstantDashboardDataDto.INSTANT_DASHBOARD_DEFAULT_TEMPLATE;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

public class InstantPreviewDashboardIT extends AbstractDashboardRestServiceIT {

  @Test
  public void instantPreviewDashboardHappyCase() {
    // given
    final DashboardService dashboardService =
      embeddedOptimizeExtension.getDashboardService();
    String processDefKey = "dummy";
    String dashboardJsonTemplateFilename = "dummy_template.json";

    // when
    final InstantDashboardDataDto instantPreviewDashboardDto =
      dashboardService.createInstantPreviewDashboard(processDefKey, dashboardJsonTemplateFilename, DEFAULT_USERNAME);

    // then
    assertThat(instantPreviewDashboardDto.getId()).isEqualTo(
      processDefKey + "_" + dashboardJsonTemplateFilename.replaceAll("\\.", ""));
    assertThat(instantPreviewDashboardDto.getProcessDefinitionKey()).isEqualTo(processDefKey);
    assertThat(instantPreviewDashboardDto.getTemplateName()).isEqualTo(dashboardJsonTemplateFilename);

    // when
    DashboardDefinitionRestDto returnedDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey, dashboardJsonTemplateFilename);

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getId()).isEqualTo(instantPreviewDashboardDto.getDashboardId());
  }

  @ParameterizedTest
  @MethodSource("emptyTemplates")
  public void instantPreviewDashboardEmptyTemplateDefaultsToDefault(final String emptyTemplate) {
    // given
    final DashboardService dashboardService =
      embeddedOptimizeExtension.getDashboardService();
    String processDefKey = "dummy";

    // when
    final InstantDashboardDataDto instantPreviewDashboardDto =
      dashboardService.createInstantPreviewDashboard(processDefKey, emptyTemplate, DEFAULT_USERNAME);

    // then
    assertThat(instantPreviewDashboardDto.getId()).isEqualTo(processDefKey + "_" + INSTANT_DASHBOARD_DEFAULT_TEMPLATE.replaceAll("\\.", ""));
    assertThat(instantPreviewDashboardDto.getProcessDefinitionKey()).isEqualTo(processDefKey);
    assertThat(instantPreviewDashboardDto.getTemplateName()).isEqualTo(INSTANT_DASHBOARD_DEFAULT_TEMPLATE);

    // when
    DashboardDefinitionRestDto returnedDashboard = dashboardClient.getInstantPreviewDashboard(processDefKey, emptyTemplate);

    // then
    assertThat(returnedDashboard).isNotNull();
    assertThat(returnedDashboard.getId()).isEqualTo(instantPreviewDashboardDto.getDashboardId());
  }

  @Test
  public void instantPreviewDashboardNonExistingDashboard() {
    // given
    String processDefKey = "never_heard_of";
    String dashboardJsonTemplateFilename = "dummy_template.json";

    // when
    String response = embeddedOptimizeExtension
      .getRequestExecutor()
      .buildGetInstantPreviewDashboardRequest(processDefKey, dashboardJsonTemplateFilename)
      .execute(String.class, Response.Status.NOT_FOUND.getStatusCode());

    // then
    assertThat(response).containsSequence("Dashboard does not exist!");
  }

  @Test
  public void getInstantPreviewDashboardWithoutAuthentication() {
    // when
    final Response response = embeddedOptimizeExtension.getRequestExecutor()
      .buildGetInstantPreviewDashboardRequest("bla", "bla")
      .withoutAuthentication()
      .execute();

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
  }

  public static Stream<String> emptyTemplates() {
    return Stream.of(null, "");
  }
}
