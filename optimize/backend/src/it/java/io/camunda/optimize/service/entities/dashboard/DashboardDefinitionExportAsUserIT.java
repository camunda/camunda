/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.entities.dashboard;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
// import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
// import static io.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.IdentityDto;
// import io.camunda.optimize.dto.optimize.IdentityType;
// import io.camunda.optimize.dto.optimize.RoleType;
// import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
// import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
// import io.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
// import io.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
// import io.camunda.optimize.util.BpmnModels;
// import jakarta.ws.rs.core.Response;
// import java.util.List;
// import org.assertj.core.groups.Tuple;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.ValueSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class DashboardDefinitionExportAsUserIT extends AbstractDashboardDefinitionExportIT {
//
//   @Override
//   protected List<OptimizeEntityExportDto> exportDashboardDefinitionAndReturnAsList(
//       final String dashboardId) {
//     return exportClient.exportDashboardAndReturnExportDtosAsDemo(dashboardId, "my_file.json");
//   }
//
//   @Override
//   protected Response exportDashboardDefinitionAndReturnResponse(final String dashboardId) {
//     return exportClient.exportDashboardDefinitionAsDemo(dashboardId, "my_file.json");
//   }
//
//   @Test
//   public void exportDashboardAsJsonFile_userDoesNotHaveCollectionRoleForExportingDashboards() {
//     // given
//     engineIntegrationExtension.deployProcessAndGetId(
//         BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY));
//     final String collectionId = collectionClient.createNewCollection();
//     collectionClient.addScopeEntryToCollection(
//         collectionId, new CollectionScopeEntryDto(PROCESS, DEFINITION_KEY, DEFAULT_TENANTS));
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//
// authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
//     collectionClient.addRolesToCollection(
//         collectionId,
//         new CollectionRoleRequestDto(
//             new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER));
//
//     final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
//     // make sure that Kermit is a viewer within the collection
//     assertThat(collectionClient.getCollectionRoles(collectionId))
//         .extracting(
//             roles -> roles.getIdentity().getId(),
//             roles -> roles.getIdentity().getType(),
//             CollectionRoleResponseDto::getRole)
//         .contains(Tuple.tuple(KERMIT_USER, IdentityType.USER, RoleType.VIEWER));
//
//     // when
//     Response response =
//         exportClient.exportDashboardAsUser(KERMIT_USER, KERMIT_USER, dashboardId,
// "my_file.json");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @ValueSource(strings = {"EDITOR", "MANAGER"})
//   public void exportDashboardAsJsonFile_userHasCollectionRoleForExportingDashboards(
//       final String roleType) {
//     // given
//     engineIntegrationExtension.deployProcessAndGetId(
//         BpmnModels.getSingleUserTaskDiagram(DEFINITION_KEY));
//     final String collectionId = collectionClient.createNewCollection();
//     collectionClient.addScopeEntryToCollection(
//         collectionId, new CollectionScopeEntryDto(PROCESS, DEFINITION_KEY, DEFAULT_TENANTS));
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     collectionClient.addRolesToCollection(
//         collectionId,
//         new CollectionRoleRequestDto(
//             new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.valueOf(roleType)));
//
//     final String dashboardId = dashboardClient.createEmptyDashboard(collectionId);
//     // make sure that Kermit has expected role within the collection
//     assertThat(collectionClient.getCollectionRoles(collectionId))
//         .extracting(
//             roles -> roles.getIdentity().getId(),
//             roles -> roles.getIdentity().getType(),
//             CollectionRoleResponseDto::getRole)
//         .contains(Tuple.tuple(KERMIT_USER, IdentityType.USER, RoleType.valueOf(roleType)));
//
//     // when
//     Response response =
//         exportClient.exportDashboardAsUser(KERMIT_USER, KERMIT_USER, dashboardId,
// "my_file.json");
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
//   }
// }
