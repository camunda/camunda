/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.entities.dashboard;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.ws.rs.core.Response;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;

public class DashboardDefinitionExportAsUserIT extends AbstractDashboardDefinitionExportIT {

  @Override
  protected List<OptimizeEntityExportDto> exportDashboardDefinitionAndReturnAsList(final String dashboardId) {
    return exportClient.exportDashboardAndReturnExportDtosAsDemo(dashboardId, "my_file.json");
  }

  @Override
  protected Response exportDashboardDefinitionAndReturnResponse(final String dashboardId) {
    return exportClient.exportDashboardDefinitionAsDemo(dashboardId, "my_file.json");
  }

  @Test
  public void exportDashboardAsJsonFile_userDoesNotHaveCollectionRoleForExportingDashboards() {
    // given
    engineIntegrationExtension.deployProcessAndGetId(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY));
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(PROCESS, DEFINITION_KEY, DEFAULT_TENANTS));
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
    collectionClient.addRolesToCollection(
      collectionId, new CollectionRoleRequestDto(new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER));

    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    // make sure that Kermit is a viewer within the collection
    assertThat(collectionClient.getCollectionRoles(collectionId))
      .extracting(
        roles -> roles.getIdentity().getId(), roles -> roles.getIdentity().getType(), CollectionRoleResponseDto::getRole)
      .contains(Tuple.tuple(KERMIT_USER, IdentityType.USER, RoleType.VIEWER));

    // when
    Response response = exportClient.exportDashboardAsUser(KERMIT_USER, KERMIT_USER, dashboardId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
  }

  @ParameterizedTest
  @ValueSource(strings = {"EDITOR", "MANAGER"})
  public void exportDashboardAsJsonFile_userHasCollectionRoleForExportingDashboards(final String roleType) {
    // given
    engineIntegrationExtension.deployProcessAndGetId(BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY));
    final String collectionId = collectionClient.createNewCollection();
    collectionClient.addScopeEntryToCollection(
      collectionId, new CollectionScopeEntryDto(PROCESS, DEFINITION_KEY, DEFAULT_TENANTS));
    authorizationClient.addKermitUserAndGrantAccessToOptimize();
    collectionClient.addRolesToCollection(
      collectionId, new CollectionRoleRequestDto(new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.valueOf(roleType)));

    final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
    // make sure that Kermit has expected role within the collection
    assertThat(collectionClient.getCollectionRoles(collectionId))
      .extracting(
        roles -> roles.getIdentity().getId(), roles -> roles.getIdentity().getType(), CollectionRoleResponseDto::getRole)
      .contains(Tuple.tuple(KERMIT_USER, IdentityType.USER, RoleType.valueOf(roleType)));

    // when
    Response response = exportClient.exportDashboardAsUser(KERMIT_USER, KERMIT_USER, dashboardId, "my_file.json");

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }

}
