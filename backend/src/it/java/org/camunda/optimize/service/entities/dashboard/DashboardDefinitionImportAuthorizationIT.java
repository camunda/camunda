/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;

import jakarta.ws.rs.core.Response;
import org.camunda.optimize.service.entities.AbstractExportImportEntityDefinitionIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class DashboardDefinitionImportAuthorizationIT
    extends AbstractExportImportEntityDefinitionIT {

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
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);

    // when
    final Response response =
        importClient.importEntityAsUser(KERMIT_USER, KERMIT_USER, createSimpleDashboardExportDto());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

  @Test
  public void importDashboardIntoCollection_asSuperuser() {
    // given
    final String collectionId = collectionClient.createNewCollection();

    // when
    final Response response =
        importClient.importEntityIntoCollection(collectionId, createSimpleDashboardExportDto());

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
