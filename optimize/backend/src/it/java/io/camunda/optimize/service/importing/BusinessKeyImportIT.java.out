/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.importing;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static io.camunda.optimize.service.db.DatabaseConstants.BUSINESS_KEY_INDEX_NAME;
// import static jakarta.ws.rs.HttpMethod.POST;
// import static org.assertj.core.api.Assertions.assertThat;
// import static org.mockserver.model.HttpRequest.request;
// import static org.mockserver.model.StringBody.subString;
//
// import com.fasterxml.jackson.core.JsonProcessingException;
// import io.camunda.optimize.dto.optimize.persistence.BusinessKeyDto;
// import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
// import java.util.List;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.mockserver.integration.ClientAndServer;
// import org.mockserver.matchers.Times;
// import org.mockserver.model.HttpError;
// import org.mockserver.model.HttpRequest;
//
// @Tag(OPENSEARCH_PASSING)
// public class BusinessKeyImportIT extends AbstractImportIT {
//
//   @BeforeEach
//   public void init() {
//     embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
//   }
//
//   @Test
//   public void businessKeyImportedForRunningAndCompletedProcess() throws JsonProcessingException {
//     // given
//     ProcessInstanceEngineDto completedProcess = deployAndStartUserTaskProcess();
//     engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());
//     ProcessInstanceEngineDto runningProcess = deployAndStartUserTaskProcess();
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(getAllStoredBusinessKeys())
//         .containsExactlyInAnyOrder(
//             new BusinessKeyDto(completedProcess.getId(), completedProcess.getBusinessKey()),
//             new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey()));
//
//     // when running process is completed and import run again
//     engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());
//     importAllEngineEntitiesFromLastIndex();
//
//     // then keys are still correct
//     assertThat(getAllStoredBusinessKeys())
//         .containsExactlyInAnyOrder(
//             new BusinessKeyDto(completedProcess.getId(), completedProcess.getBusinessKey()),
//             new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey()));
//   }
//
//   @Test
//   public void
//       importOfBusinessKeyForRunningProcess_isImportedOnNextSuccessfulAttemptAfterDbFailures()
//           throws JsonProcessingException {
//     // given
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(getAllStoredBusinessKeys()).isEmpty();
//
//     // when updates to DB fail
//     ProcessInstanceEngineDto runningProcess = deployAndStartUserTaskProcess();
//     final ClientAndServer dbMockServer = useAndGetDbMockServer();
//     final HttpRequest businessKeyImportMatcher =
//         request()
//             .withPath("/_bulk")
//             .withMethod(POST)
//             .withBody(
//                 subString(
//                     "\"_index\":\""
//                         + embeddedOptimizeExtension
//                             .getOptimizeDatabaseClient()
//                             .getIndexNameService()
//                             .getIndexPrefix()
//                         + "-"
//                         + BUSINESS_KEY_INDEX_NAME
//                         + "\""));
//     dbMockServer
//         .when(businessKeyImportMatcher, Times.once())
//         .error(HttpError.error().withDropConnection(true));
//     importAllEngineEntitiesFromLastIndex();
//
//     // then the key gets stored after successful write
//     assertThat(getAllStoredBusinessKeys())
//         .containsExactlyInAnyOrder(
//             new BusinessKeyDto(runningProcess.getId(), runningProcess.getBusinessKey()));
//     dbMockServer.verify(businessKeyImportMatcher);
//   }
//
//   @Test
//   public void
//       importOfBusinessKeyForCompletedProcess_isImportedOnNextSuccessfulAttemptAfterDbFailures()
//           throws JsonProcessingException {
//     // given
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     assertThat(getAllStoredBusinessKeys()).isEmpty();
//
//     // when updates to DB fail
//     ProcessInstanceEngineDto process = deployAndStartUserTaskProcess();
//     engineIntegrationExtension.finishAllRunningUserTasks(process.getId());
//
//     final ClientAndServer dbMockServer = useAndGetDbMockServer();
//     final HttpRequest businessKeyImportMatcher =
//         request()
//             .withPath("/_bulk")
//             .withMethod(POST)
//             .withBody(
//                 subString(
//                     "\"_index\":\""
//                         + embeddedOptimizeExtension
//                             .getOptimizeDatabaseClient()
//                             .getIndexNameService()
//                             .getIndexPrefix()
//                         + "-"
//                         + BUSINESS_KEY_INDEX_NAME
//                         + "\""));
//     dbMockServer
//         .when(businessKeyImportMatcher, Times.once())
//         .error(HttpError.error().withDropConnection(true));
//     importAllEngineEntitiesFromLastIndex();
//
//     // then the key gets stored after successful write
//     assertThat(getAllStoredBusinessKeys())
//         .containsExactlyInAnyOrder(new BusinessKeyDto(process.getId(),
// process.getBusinessKey()));
//     dbMockServer.verify(businessKeyImportMatcher);
//   }
//
//   @Test
//   public void businessKeyNotImported_whenFeatureDisabled() throws JsonProcessingException {
//     // given
//     embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(false);
//
//     deployAndStartUserTaskProcess();
//     ProcessInstanceEngineDto completedProcess = deployAndStartUserTaskProcess();
//     engineIntegrationExtension.finishAllRunningUserTasks(completedProcess.getId());
//
//     // when
//     importAllEngineEntitiesFromScratch();
//
//     // then
//     List<BusinessKeyDto> storedBusinessKeys = getAllStoredBusinessKeys();
//     assertThat(storedBusinessKeys).isEmpty();
//   }
//
//   private List<BusinessKeyDto> getAllStoredBusinessKeys() {
//     return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
//         BUSINESS_KEY_INDEX_NAME, BusinessKeyDto.class);
//   }
// }
