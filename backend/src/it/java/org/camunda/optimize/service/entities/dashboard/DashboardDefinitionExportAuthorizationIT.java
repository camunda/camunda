/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.dashboard;

import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.camunda.optimize.util.SuperUserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import javax.ws.rs.core.Response;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;

/**
 * These are authIT for the export via UI with user authorization. For the public API, please refer to
 * PublicJsonExportRestServiceIT.
 */
public class DashboardDefinitionExportAuthorizationIT extends AbstractExportImportEntityDefinitionIT {

  @ParameterizedTest
  @EnumSource(SuperUserType.class)
  public void exportDashboardAsJson_asSuperuser(SuperUserType superUserType) {
    // given
    final String dashboardId = dashboardClient.createEmptyDashboard(null);

    // when
    final Response response;
    if (superUserType == SuperUserType.USER) {
      response = exportClient.exportDashboardAsUser("demo", "demo", dashboardId, "my_file.json");
    } else {
      authorizationClient.addKermitUserAndGrantAccessToOptimize();
      authorizationClient.createKermitGroupAndAddKermitToThatGroup();
      embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperGroupIds().add(GROUP_ID);
      response = exportClient.exportDashboardAsUser(KERMIT_USER, KERMIT_USER, dashboardId, "my_file.json");
    }

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void exportDashboardAsJson_asNonSuperuser() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    final String dashboardId = dashboardClient.createEmptyDashboard(null);

    // when
    final Response response =
      exportClient.exportDashboardAsUser(KERMIT_USER, KERMIT_USER, dashboardId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void exportDashboardAsJson_withoutReportDefinitionAuth() {
    // given a dashboard with a report that Kermit is not authorized to access
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    embeddedOptimizeExtension.getConfigurationService().getAuthConfiguration().getSuperUserIds().add(KERMIT_USER);
    final String defKey =
      engineIntegrationExtension.deployProcessAndGetProcessDefinition(getSimpleBpmnDiagram()).getKey();
    final String reportId = reportClient.createSingleReport(
      null,
      DefinitionType.PROCESS,
      defKey,
      Collections.emptyList()
    );
    final String dashboardId = dashboardClient.createDashboard(null, Collections.singletonList(reportId));

    // when
    final Response response =
      exportClient.exportDashboardAsUser(KERMIT_USER, KERMIT_USER, dashboardId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }
}
