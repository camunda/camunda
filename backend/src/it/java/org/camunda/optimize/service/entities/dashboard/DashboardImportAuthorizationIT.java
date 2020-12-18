/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.entities.dashboard;

import org.camunda.optimize.service.entities.AbstractExportImportIT;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

public class DashboardImportAuthorizationIT extends AbstractExportImportIT {

  @Test
  public void importDashboard_asSuperuser() {
    // when
    final Response response = importClient.importEntity(createSimpleDashboardExportDto());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void importDashboard_asNonSuperuser() {
    // given
    authorizationClient.addKermitUserAndGrantAccessToOptimize();

    // when
    final Response response = importClient.importEntityAsUser(
      KERMIT_USER,
      KERMIT_USER,
      createSimpleDashboardExportDto()
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @Test
  public void importDashboardIntoCollection_asSuperuser() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final Response response = importClient.importEntityIntoCollection(
      collectionId,
      createSimpleDashboardExportDto()
    );

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
