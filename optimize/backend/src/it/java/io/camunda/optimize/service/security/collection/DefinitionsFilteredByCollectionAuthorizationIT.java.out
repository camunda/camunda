/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.security.collection;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
// import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
// import static io.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.optimize.DefinitionType;
// import io.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
// import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
// import io.camunda.optimize.util.BpmnModels;
// import jakarta.ws.rs.core.Response;
// import java.util.List;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class DefinitionsFilteredByCollectionAuthorizationIT extends AbstractCollectionRoleIT {
//
//   @ParameterizedTest
//   @MethodSource(ACCESS_IDENTITY_ROLES)
//   public void getDefinitionKeys_FilterByCollectionId_ForAuthorizedCollection(
//       final AbstractCollectionRoleIT.IdentityAndRole accessIdentityRolePairs) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.createKermitGroupAndAddKermitToThatGroup();
//     authorizationClient.grantKermitGroupOptimizeAccess();
//
// authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
//
//     deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
//     final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
//     addRoleToCollectionAsDefaultUser(
//         accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId);
//
//     // when
//     List<DefinitionKeyResponseDto> definitionKeys =
//         definitionClient.getDefinitionKeysByTypeAsUser(
//             DefinitionType.PROCESS, collectionId, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(definitionKeys)
//         .extracting(DefinitionKeyResponseDto::getKey)
//         .containsExactly(DEFAULT_DEFINITION_KEY);
//   }
//
//   @Test
//   public void getDefinitionKeys_FilterByCollectionId_ForUnauthorizedCollection() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//
// authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
//
//     deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
//     final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
//
//     // when
//     Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .buildGetDefinitionKeysByType(DefinitionType.PROCESS.getId(), collectionId)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   @ParameterizedTest
//   @MethodSource(ACCESS_IDENTITY_ROLES)
//   public void getDefinitionVersionByKey_FilterByCollectionId_ForAuthorizedCollection(
//       final AbstractCollectionRoleIT.IdentityAndRole accessIdentityRolePairs) {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.createKermitGroupAndAddKermitToThatGroup();
//     authorizationClient.grantKermitGroupOptimizeAccess();
//
// authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
//
//     deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
//     final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
//     addRoleToCollectionAsDefaultUser(
//         accessIdentityRolePairs.roleType, accessIdentityRolePairs.identityDto, collectionId);
//
//     // when
//     List<DefinitionVersionResponseDto> definitionVersions =
//         definitionClient.getDefinitionVersionsByTypeAndKeyAsUser(
//             DefinitionType.PROCESS, DEFAULT_DEFINITION_KEY, collectionId, KERMIT_USER,
// KERMIT_USER);
//
//     // then
//     assertThat(definitionVersions)
//         .extracting(DefinitionVersionResponseDto::getVersion)
//         .containsExactly("1");
//   }
//
//   @Test
//   public void getDefinitionVersionByKey_FilterByCollectionId_ForUnauthorizedCollection() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//
// authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
//
//     deployAndImportSimpleProcess(DEFAULT_DEFINITION_KEY);
//     final String collectionId = collectionClient.createNewCollectionForAllDefinitionTypes();
//
//     // when
//     Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .buildGetDefinitionVersionsByTypeAndKeyRequest(
//                 DefinitionType.PROCESS.getId(), DEFAULT_DEFINITION_KEY, collectionId)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   private void deployAndImportSimpleProcess(final String definitionKey) {
//     engineIntegrationExtension.deployProcessAndGetProcessDefinition(
//         BpmnModels.getSingleServiceTaskProcess(definitionKey));
//     importAllEngineEntitiesFromScratch();
//   }
// }
