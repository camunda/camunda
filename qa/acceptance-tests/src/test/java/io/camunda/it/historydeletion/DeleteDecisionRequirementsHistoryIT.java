/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historydeletion;

import static io.camunda.it.util.DmnBuilderHelper.getDmnModelInstance;
import static io.camunda.it.util.TestHelper.deployDmnModel;
import static io.camunda.it.util.TestHelper.evaluateDecision;
import static io.camunda.it.util.TestHelper.waitForBatchOperationCompleted;
import static io.camunda.it.util.TestHelper.waitForBatchOperationWithCorrectTotalCount;
import static io.camunda.it.util.TestHelper.waitForDecisionInstanceCount;
import static io.camunda.it.util.TestHelper.waitForDecisionsToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DeleteResourceResponse;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DeleteDecisionRequirementsHistoryIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withProcessingConfig(
              config ->
                  config
                      .getEngine()
                      .getBatchOperations()
                      .setSchedulerInterval(Duration.ofMillis(100)))
          .withDataConfig(
              config -> {
                final var historyDeletionConfig = new HistoryDeletion();
                historyDeletionConfig.setDelayBetweenRuns(Duration.ofMillis(100));
                historyDeletionConfig.setMaxDelayBetweenRuns(Duration.ofMillis(100));
                config.setHistoryDeletion(historyDeletionConfig);
              });

  private static final Duration DELETION_TIMEOUT = Duration.ofSeconds(30);
  private static CamundaClient camundaClient;

  @Test
  void shouldDeleteDecisionRequirementsWithHistory() {
    // given
    final var decisionId = Strings.newRandomValidBpmnId();
    final var dmnModel = getDmnModelInstance(decisionId);
    final Decision decision = deployDmnModel(camundaClient, dmnModel, decisionId);
    waitForDecisionsToBeDeployed(
        camundaClient, decision.getDecisionKey(), decision.getDecisionRequirementsKey(), 1, 1);
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionDefinitionId(decisionId), 1);

    // when
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(decision.getDecisionRequirementsKey())
            .deleteHistory(true)
            .send()
            .join();

    // then
    final var batchOperationKey = result.getCreateBatchOperationResponse().getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 1);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 1, 0);
    assertDecisionInstancesDeleted(camundaClient, decisionId);
    assertDecisionDefinitionsDeleted(camundaClient, decision.getDecisionRequirementsKey());
    assertDecisionRequirementsDeleted(camundaClient, decision.getDecisionRequirementsKey());
  }

  @Test
  void shouldDeleteDecisionRequirementsWithMultipleEvaluatedDecisionInstances() {
    // given
    final var decisionId = Strings.newRandomValidBpmnId();
    final var dmnModel = getDmnModelInstance(decisionId);
    final Decision decision = deployDmnModel(camundaClient, dmnModel, decisionId);
    waitForDecisionsToBeDeployed(
        camundaClient, decision.getDecisionKey(), decision.getDecisionRequirementsKey(), 1, 1);
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionDefinitionId(decisionId), 3);

    // when
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(decision.getDecisionRequirementsKey())
            .deleteHistory(true)
            .send()
            .join();

    // then
    final var batchOperationKey = result.getCreateBatchOperationResponse().getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 3);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 3, 0);
    assertDecisionInstancesDeleted(camundaClient, decisionId);
    assertDecisionDefinitionsDeleted(camundaClient, decision.getDecisionRequirementsKey());
    assertDecisionRequirementsDeleted(camundaClient, decision.getDecisionRequirementsKey());
  }

  @Test
  void shouldDeleteDecisionRequirementsWithoutHistory() {
    // given
    final var decisionId = Strings.newRandomValidBpmnId();
    final var dmnModel = getDmnModelInstance(decisionId);
    final Decision decision = deployDmnModel(camundaClient, dmnModel, decisionId);
    waitForDecisionsToBeDeployed(
        camundaClient, decision.getDecisionKey(), decision.getDecisionRequirementsKey(), 1, 1);
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionDefinitionId(decisionId), 2);

    // when
    final DeleteResourceResponse result =
        camundaClient
            .newDeleteResourceCommand(decision.getDecisionRequirementsKey())
            .deleteHistory(false)
            .send()
            .join();

    // then - deletion without history should not create a batch operation
    assertThat(result.getCreateBatchOperationResponse()).isNull();
    // and - decision instances should still exist
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionDefinitionId(decisionId), 2);
    // and - decision definitions and decision requirements should still exist
    waitForDecisionsToBeDeployed(
        camundaClient, decision.getDecisionKey(), decision.getDecisionRequirementsKey(), 1, 1);
  }

  @Test
  void shouldDeleteDecisionRequirementsHistoryAfterDeletingWithoutHistory() {
    // given - a deployed DRG with evaluated decision instances
    final var decisionId = Strings.newRandomValidBpmnId();
    final var dmnModel = getDmnModelInstance(decisionId);
    final Decision decision = deployDmnModel(camundaClient, dmnModel, decisionId);
    waitForDecisionsToBeDeployed(
        camundaClient, decision.getDecisionKey(), decision.getDecisionRequirementsKey(), 1, 1);
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionDefinitionId(decisionId), 2);

    // and - first delete without history, leaving decision instances and definitions intact
    final DeleteResourceResponse resultWithoutHistory =
        camundaClient
            .newDeleteResourceCommand(decision.getDecisionRequirementsKey())
            .deleteHistory(false)
            .send()
            .join();
    assertThat(resultWithoutHistory.getCreateBatchOperationResponse()).isNull();
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionDefinitionId(decisionId), 2);

    // when - now delete with history to clean up the remaining historical data
    final DeleteResourceResponse resultWithHistory =
        camundaClient
            .newDeleteResourceCommand(decision.getDecisionRequirementsKey())
            .deleteHistory(true)
            .send()
            .join();

    // then - the batch operation should be created and complete successfully
    final var batchOperationKey =
        resultWithHistory.getCreateBatchOperationResponse().getBatchOperationKey();
    waitForBatchOperationWithCorrectTotalCount(camundaClient, batchOperationKey, 2);
    waitForBatchOperationCompleted(camundaClient, batchOperationKey, 2, 0);
    assertDecisionInstancesDeleted(camundaClient, decisionId);
    assertDecisionDefinitionsDeleted(camundaClient, decision.getDecisionRequirementsKey());
    assertDecisionRequirementsDeleted(camundaClient, decision.getDecisionRequirementsKey());
  }

  /** Asserts that all decision instances for a given decision definition ID have been deleted. */
  private void assertDecisionInstancesDeleted(final CamundaClient client, final String decisionId) {
    Awaitility.await("Decision instances should be deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var decisionInstances =
                  client
                      .newDecisionInstanceSearchRequest()
                      .filter(f -> f.decisionDefinitionId(decisionId))
                      .send()
                      .join()
                      .items();
              assertThat(decisionInstances).isEmpty();
            });
  }

  /**
   * Asserts that all decision definitions for a given decision requirements key have been deleted.
   */
  private void assertDecisionDefinitionsDeleted(
      final CamundaClient client, final long decisionRequirementsKey) {
    Awaitility.await("Decision definitions should be deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var decisionDefinitions =
                  client
                      .newDecisionDefinitionSearchRequest()
                      .filter(f -> f.decisionRequirementsKey(decisionRequirementsKey))
                      .send()
                      .join()
                      .items();
              assertThat(decisionDefinitions).isEmpty();
            });
  }

  /** Asserts that the decision requirements have been deleted from secondary storage. */
  private void assertDecisionRequirementsDeleted(
      final CamundaClient client, final long decisionRequirementsKey) {
    Awaitility.await("Decision requirements should be deleted")
        .atMost(DELETION_TIMEOUT)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var decisionRequirements =
                  client
                      .newDecisionRequirementsSearchRequest()
                      .filter(f -> f.decisionRequirementsKey(decisionRequirementsKey))
                      .send()
                      .join()
                      .items();
              assertThat(decisionRequirements).isEmpty();
            });
  }
}
