/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.pub;

import org.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.db.DatabaseConstants.DASHBOARD_INDEX_NAME;

public class PublicApiDashboardDeletionIT extends AbstractPlatformIT {
  private static final String ACCESS_TOKEN = "secret_export_token";

  @Test
  public void deleteDashboard() {
    // given
    setAccessToken();
    final String dashboardId = dashboardClient.createEmptyDashboard();

    // when
    final Response deleteResponse = publicApiClient.deleteDashboard(dashboardId, ACCESS_TOKEN);

    // then
    assertThat(deleteResponse.getStatus()).isEqualTo(Response.Status.NO_CONTENT.getStatusCode());
    assertThat(databaseIntegrationTestExtension.getDocumentCountOf(DASHBOARD_INDEX_NAME)).isEqualTo(0);
  }

  @Test
  public void deleteDashboardNotExisting() {
    // given
    setAccessToken();

    // when
    final Response deleteResponse = publicApiClient.deleteDashboard("notExisting", ACCESS_TOKEN);

    // then
    assertThat(deleteResponse.getStatus()).isEqualTo(Response.Status.NOT_FOUND.getStatusCode());
  }

  private void setAccessToken() {
    embeddedOptimizeExtension.getConfigurationService().getOptimizeApiConfiguration().setAccessToken(ACCESS_TOKEN);
  }

}
