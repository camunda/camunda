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
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_DECISION_DEFINITION;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
// import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
// import static io.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
// import static io.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.IdentityDto;
// import io.camunda.optimize.dto.optimize.IdentityType;
// import io.camunda.optimize.dto.optimize.RoleType;
// import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
// import
// io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
// import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;
// import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
// import jakarta.ws.rs.core.Response;
// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.List;
// import java.util.stream.Stream;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.MethodSource;
//
// @Tag(OPENSEARCH_PASSING)
// public class CollectionReportsAuthorizationIT extends AbstractPlatformIT {
//
//   private static Stream<Integer> definitionTypes() {
//     return Stream.of(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION);
//   }
//
//   private static Stream<List<Integer>> definitionTypePairs() {
//     return Stream.of(
//         Arrays.asList(RESOURCE_TYPE_PROCESS_DEFINITION, RESOURCE_TYPE_DECISION_DEFINITION),
//         Arrays.asList(RESOURCE_TYPE_DECISION_DEFINITION, RESOURCE_TYPE_PROCESS_DEFINITION));
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionTypes")
//   public void getReportsForAuthorizedCollection(final int definitionType) {
//     // given
//     final String collectionId1 = collectionClient.createNewCollectionForAllDefinitionTypes();
//     final List<String> expectedReportIds = new ArrayList<>();
//     expectedReportIds.add(createReportForCollection(collectionId1, definitionType));
//     expectedReportIds.add(createReportForCollection(collectionId1, definitionType));
//
//     final String collectionId2 = collectionClient.createNewCollectionForAllDefinitionTypes();
//     createReportForCollection(collectionId2, definitionType);
//
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.addGlobalAuthorizationForResource(definitionType);
//     collectionClient.addRolesToCollection(
//         collectionId1,
//         new CollectionRoleRequestDto(
//             new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER));
//
//     // when
//     final List<AuthorizedReportDefinitionResponseDto> reports =
//         collectionClient.getReportsForCollectionAsUser(collectionId1, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(reports).hasSize(expectedReportIds.size());
//     assertThat(reports.stream())
//         .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId()));
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionTypePairs")
//   public void getReportsForPartiallyAuthorizedCollection(final List<Integer> typePair) {
//     // given
//     final String collectionId1 = collectionClient.createNewCollectionForAllDefinitionTypes();
//     final List<String> expectedReportIds = new ArrayList<>();
//     expectedReportIds.add(createReportForCollection(collectionId1, typePair.get(0)));
//     expectedReportIds.add(createReportForCollection(collectionId1, typePair.get(0)));
//     createReportForCollection(collectionId1, typePair.get(1));
//
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.addGlobalAuthorizationForResource(typePair.get(0));
//     collectionClient.addRolesToCollection(
//         collectionId1,
//         new CollectionRoleRequestDto(
//             new IdentityDto(KERMIT_USER, IdentityType.USER), RoleType.VIEWER));
//
//     // when
//     final List<AuthorizedReportDefinitionResponseDto> allAlerts =
//         collectionClient.getReportsForCollectionAsUser(collectionId1, KERMIT_USER, KERMIT_USER);
//
//     // then
//     assertThat(allAlerts).hasSize(expectedReportIds.size());
//     assertThat(allAlerts.stream())
//         .allMatch(reportDto -> expectedReportIds.contains(reportDto.getDefinitionDto().getId()));
//   }
//
//   @ParameterizedTest
//   @MethodSource("definitionTypes")
//   public void getReportsForUnauthorizedCollection(final int definitionResourceType) {
//     // given
//     final String collectionId1 = collectionClient.createNewCollectionForAllDefinitionTypes();
//     createReportForCollection(collectionId1, definitionResourceType);
//     createReportForCollection(collectionId1, definitionResourceType);
//
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.addGlobalAuthorizationForResource(definitionResourceType);
//
//     // when
//     final Response response =
//         embeddedOptimizeExtension
//             .getRequestExecutor()
//             .buildGetReportsForCollectionRequest(collectionId1)
//             .withUserAuthentication(KERMIT_USER, KERMIT_USER)
//             .execute();
//
//     // then
//     assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
//   }
//
//   private String createReportForCollection(final String collectionId, final int resourceType) {
//     return switch (resourceType) {
//       case RESOURCE_TYPE_PROCESS_DEFINITION -> {
//         final SingleProcessReportDefinitionRequestDto procReport =
//             reportClient.createSingleProcessReportDefinitionDto(
//                 collectionId, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
//         yield reportClient.createSingleProcessReport(procReport);
//       }
//       case RESOURCE_TYPE_DECISION_DEFINITION -> {
//         final SingleDecisionReportDefinitionRequestDto decReport =
//             reportClient.createSingleDecisionReportDefinitionDto(
//                 collectionId, DEFAULT_DEFINITION_KEY, DEFAULT_TENANTS);
//         yield reportClient.createSingleDecisionReport(decReport);
//       }
//       default -> throw new OptimizeRuntimeException("Unknown resource type provided.");
//     };
//   }
// }
