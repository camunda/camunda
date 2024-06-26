/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
// TODO recreate C8 IT equivalent of this with #13337
// package io.camunda.optimize.service.cleanup;
//
// import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
// import static org.assertj.core.api.Assertions.assertThat;
//
// import io.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
// import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
// import
// io.camunda.optimize.service.util.configuration.cleanup.DecisionDefinitionCleanupConfiguration;
// import io.camunda.optimize.util.DmnModels;
// import io.github.netmikey.logunit.api.LogCapturer;
// import java.io.IOException;
// import java.sql.SQLException;
// import java.time.OffsetDateTime;
// import java.util.List;
// import lombok.SneakyThrows;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Tag;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.RegisterExtension;
//
// @Tag(OPENSEARCH_PASSING)
// public class EngineDataDecisionCleanupServiceIT extends AbstractCleanupIT {
//
//   @RegisterExtension
//   LogCapturer cleanupServiceLogs = LogCapturer.create().captureForType(CleanupService.class);
//
//   @RegisterExtension
//   LogCapturer engineDataCleanupLogs =
//       LogCapturer.create().captureForType(EngineDataDecisionCleanupService.class);
//
//   @BeforeEach
//   public void enableCamundaCleanup() {
//     embeddedOptimizeExtension
//         .getConfigurationService()
//         .getCleanupServiceConfiguration()
//         .getDecisionCleanupConfiguration()
//         .setEnabled(true);
//   }
//
//   @Test
//   @SneakyThrows
//   public void testCleanupWithDecisionInstanceDelete() {
//     // given
//     final List<String> decisionInstanceIds =
//         deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertNoDecisionInstanceDataExists(decisionInstanceIds);
//   }
//
//   @Test
//   @SneakyThrows
//   public void testCleanupWithDecisionInstanceDeleteVerifyThatNewOnesAreUnaffected() {
//     // given
//     final List<String> instanceIdsToCleanup =
//         deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
//     final List<String> unaffectedDecisionDefinitionsIds =
//         deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime.now());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     assertNoDecisionInstanceDataExists(instanceIdsToCleanup);
//     assertDecisionInstancesExistInDatabase(unaffectedDecisionDefinitionsIds);
//   }
//
//   @Test
//   @SneakyThrows
//   public void testCleanupModeVariables_specificKeyCleanupMode_noInstanceDataExists() {
//     // given
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployDecisionDefinition(DmnModels.createDefaultDmnModel());
//     getCleanupConfiguration()
//         .getDecisionCleanupConfiguration()
//         .getDecisionDefinitionSpecificConfiguration()
//         .put(
//             decisionDefinitionEngineDto.getKey(),
//             new DecisionDefinitionCleanupConfiguration(getCleanupConfiguration().getTtl()));
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then
//     engineDataCleanupLogs.assertContains(
//         "Finished cleanup on decision instances for decisionDefinitionKey: invoiceClassification,
// with ttl: P2Y");
//   }
//
//   @Test
//   @SneakyThrows
//   public void
//       testCleanupOnSpecificKeyConfigWithNoMatchingDecisionDefinitionWorksWithLoggedWarning() {
//     // given I have a key specific config
//     final String configuredKey = "myMistypedKey";
//     getCleanupConfiguration()
//         .getDecisionCleanupConfiguration()
//         .getDecisionDefinitionSpecificConfiguration()
//         .put(
//             configuredKey,
//             new DecisionDefinitionCleanupConfiguration(getCleanupConfiguration().getTtl()));
//     // and deploy processes with different keys
//     deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl();
//     final List<String> unaffectedDecisionDefinitionsIds =
//         deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime.now());
//
//     importAllEngineEntitiesFromScratch();
//
//     // when
//     embeddedOptimizeExtension.getCleanupScheduler().runCleanup();
//
//     databaseIntegrationTestExtension.refreshAllOptimizeIndices();
//
//     // then data clear up has succeeded as expected
//     assertDecisionInstancesExistInDatabase(unaffectedDecisionDefinitionsIds);
//     // and the misconfigured process is logged
//     cleanupServiceLogs.assertContains(
//         String.format(
//             "History Cleanup Configuration contains definition keys for which there is no "
//                 + "definition imported yet. The keys without a match in the database are: [%s]",
//             configuredKey));
//   }
//
//   @SneakyThrows
//   protected void assertNoDecisionInstanceDataExists(final List<String> decisionInstanceIds) {
//     assertThat(getDecisionInstancesById(decisionInstanceIds)).isEmpty();
//   }
//
//   protected void assertDecisionInstancesExistInDatabase(List<String> decisionInstanceIds)
//       throws IOException {
//     List<DecisionInstanceDto> idsResp = getDecisionInstancesById(decisionInstanceIds);
//     assertThat(idsResp).hasSameSizeAs(decisionInstanceIds);
//   }
//
//   protected List<DecisionInstanceDto> getDecisionInstancesById(List<String> decisionInstanceIds)
//       throws IOException {
//     return databaseIntegrationTestExtension.getDecisionInstancesById(decisionInstanceIds);
//   }
//
//   @SneakyThrows
//   protected List<String> deployTwoDecisionInstancesWithEvaluationTimeLessThanTtl() {
//     return deployTwoDecisionInstancesWithEvaluationTime(getEndTimeLessThanGlobalTtl());
//   }
//
//   protected List<String> deployTwoDecisionInstancesWithEvaluationTime(OffsetDateTime
// evaluationTime)
//       throws SQLException {
//     final DecisionDefinitionEngineDto decisionDefinitionEngineDto =
//         engineIntegrationExtension.deployDecisionDefinition();
//
//     OffsetDateTime lastEvaluationDateFilter = OffsetDateTime.now();
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionEngineDto.getId());
//     engineIntegrationExtension.startDecisionInstance(decisionDefinitionEngineDto.getId());
//
//     engineDatabaseExtension.changeDecisionInstanceEvaluationDate(
//         lastEvaluationDateFilter, evaluationTime);
//
//     return
// engineDatabaseExtension.getDecisionInstanceIdsWithEvaluationDateEqualTo(evaluationTime);
//   }
// }
