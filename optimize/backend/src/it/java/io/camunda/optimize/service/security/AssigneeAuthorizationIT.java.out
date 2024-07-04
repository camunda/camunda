/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.security;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static
// io.camunda.optimize.service.util.importing.EngineConstants.RESOURCE_TYPE_PROCESS_DEFINITION;
// import static io.camunda.optimize.test.engine.AuthorizationClient.KERMIT_USER;
// import static
// io.camunda.optimize.test.it.extension.EngineIntegrationExtension.DEFAULT_EMAIL_DOMAIN;
// import static io.camunda.optimize.util.BpmnModels.getUserTaskDiagramWithAssignee;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.AbstractPlatformIT;
// import io.camunda.optimize.dto.optimize.DefinitionType;
// import io.camunda.optimize.dto.optimize.UserDto;
// import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
// import
// io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import java.util.Arrays;
// import java.util.Collections;
// import org.camunda.bpm.model.bpmn.Bpmn;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
//
// @Tag(OPENSEARCH_PASSING)
// public class AssigneeAuthorizationIT extends AbstractPlatformIT {
//
//   @Test
//   public void searchForAssignees_forReports_missingCollectionAuth() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//
// authorizationClient.grantAllResourceAuthorizationsForKermit(RESOURCE_TYPE_PROCESS_DEFINITION);
//
//     engineIntegrationExtension.addUser("userId", "userFirstName", "userLastName");
//     final ProcessInstanceEngineDto instance =
//
// engineIntegrationExtension.deployAndStartProcess(getUserTaskDiagramWithAssignee("userId"));
//     final String collectionId = collectionClient.createNewCollection();
//     collectionClient.addScopeEntryToCollection(
//         collectionId,
//         instance.getProcessDefinitionKey(),
//         DefinitionType.PROCESS,
//         Collections.singletonList(null));
//     final String reportId =
//         reportClient.createAndStoreProcessReport(collectionId,
// instance.getProcessDefinitionKey());
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final IdentitySearchResultResponseDto searchResponse =
//         assigneesClient.searchForAssigneesAsUser(
//             KERMIT_USER,
//             KERMIT_USER,
//             AssigneeCandidateGroupReportSearchRequestDto.builder()
//                 .reportIds(Collections.singletonList(reportId))
//                 .build());
//
//     // then
//     assertThat(searchResponse.getResult()).isEmpty();
//   }
//
//   @Test
//   public void searchForAssignees_forReports_missingReportDefinitionAuth() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     engineIntegrationExtension.addUser("userId", "userFirstName", "userLastName");
//     final ProcessInstanceEngineDto instance =
//
// engineIntegrationExtension.deployAndStartProcess(getUserTaskDiagramWithAssignee("userId"));
//     final String reportId =
//         reportClient.createSingleProcessReportAsUser(
//             instance.getProcessDefinitionKey(), KERMIT_USER, KERMIT_USER);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final IdentitySearchResultResponseDto searchResponse =
//         assigneesClient.searchForAssigneesAsUser(
//             KERMIT_USER,
//             KERMIT_USER,
//             AssigneeCandidateGroupReportSearchRequestDto.builder()
//                 .reportIds(Collections.singletonList(reportId))
//                 .build());
//
//     // then
//     assertThat(searchResponse.getResult()).isEmpty();
//   }
//
//   @Test
//   @Tag(OPENSEARCH_SINGLE_TEST_FAIL_OK)
//   public void searchForAssignees_forReports_partialReportDefinitionAuth() {
//     // given
//     authorizationClient.addKermitUserAndGrantAccessToOptimize();
//     authorizationClient.grantSingleResourceAuthorizationForKermit(
//         "authorizedDef", RESOURCE_TYPE_PROCESS_DEFINITION);
//     engineIntegrationExtension.addUser("userId", "userFirstName", "userLastName");
//     engineIntegrationExtension.addUser("otherUserId", "otherUserFirstName", "otherUserLastName");
//     final ProcessInstanceEngineDto authDefInstance =
//         engineIntegrationExtension.deployAndStartProcess(
//             getUserTaskDiagramWithAssignee("authorizedDef", "userId"));
//     final ProcessInstanceEngineDto noAuthDefInstance =
//         engineIntegrationExtension.deployAndStartProcess(
//             getUserTaskDiagramWithAssignee("unauthorizedDef", "userId"));
//     final String authorizedReportId =
//         reportClient.createSingleProcessReportAsUser(
//             authDefInstance.getProcessDefinitionKey(), KERMIT_USER, KERMIT_USER);
//     final String unauthorizedReportId =
//         reportClient.createSingleProcessReportAsUser(
//             noAuthDefInstance.getProcessDefinitionKey(), KERMIT_USER, KERMIT_USER);
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     final IdentitySearchResultResponseDto searchResponse =
//         assigneesClient.searchForAssigneesAsUser(
//             KERMIT_USER,
//             KERMIT_USER,
//             AssigneeCandidateGroupReportSearchRequestDto.builder()
//                 .reportIds(Arrays.asList(authorizedReportId, unauthorizedReportId))
//                 .build());
//
//     // then
//     assertThat(searchResponse.getResult())
//         .singleElement()
//         .isEqualTo(
//             new UserDto(
//                 "userId", "userFirstName", "userLastName", "userId" + DEFAULT_EMAIL_DOMAIN));
//   }
//
//   private void startSimpleUserTaskProcessWithAssignee() {
//     engineIntegrationExtension.deployAndStartProcess(
//         Bpmn.createExecutableProcess("aProcess")
//             .startEvent()
//             .userTask()
//             .camundaAssignee("demo")
//             .userTask()
//             .camundaAssignee("john")
//             .endEvent()
//             .done());
//   }
// }
