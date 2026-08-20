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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Decision;
import io.camunda.client.api.response.DeleteDecisionInstanceResponse;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.test.util.Strings;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DeleteDecisionInstanceHistoryIT {

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
  void shouldDeleteTwoDecisionInstancesByIdUsingBatchOperation() {
    // given
    final var dmnModel = getDmnModelInstance(Strings.newRandomValidBpmnId());
    final Decision decision =
        deployDmnModel(camundaClient, dmnModel, dmnModel.getModel().getModelName());
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    evaluateDecision(camundaClient, decision.getDecisionKey(), "{}");
    waitForDecisionInstanceCount(
        camundaClient, f -> f.decisionDefinitionId(decision.getDmnDecisionId()), 2);

    // when
    final var batchResult =
        camundaClient
            .newCreateBatchOperationCommand()
            .deleteDecisionInstance()
            .filter(f -> f.decisionDefinitionId(decision.getDmnDecisionId()))
            .send()
            .join();

    // then
    assertThat(batchResult).isNotNull();
    assertThat(batchResult.getBatchOperationKey()).isNotNull();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchResult.getBatchOperationKey(), 2);
    waitForBatchOperationCompleted(camundaClient, batchResult.getBatchOperationKey(), 2, 0);

    waitForDecisionInstanceCount(
        camundaClient, f -> f.decisionDefinitionId(decision.getDmnDecisionId()), 0);
  }

  @Test
  void shouldDeleteOneOfTwoDecisionInstancesByKeyUsingBatchOperation() {
    // given
    final var dmnModel = getDmnModelInstance(Strings.newRandomValidBpmnId());
    final Decision decision =
        deployDmnModel(camundaClient, dmnModel, dmnModel.getModel().getModelName());
    final long instanceToDeleteKey =
        evaluateDecision(camundaClient, decision.getDecisionKey(), "{}").getDecisionEvaluationKey();
    final long instanceToKeepKey =
        evaluateDecision(camundaClient, decision.getDecisionKey(), "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(
        camundaClient, f -> f.decisionDefinitionId(decision.getDmnDecisionId()), 2);

    // when
    final var batchResult =
        camundaClient
            .newCreateBatchOperationCommand()
            .deleteDecisionInstance()
            .filter(f -> f.decisionInstanceKey(instanceToDeleteKey))
            .send()
            .join();

    // then
    assertThat(batchResult).isNotNull();
    assertThat(batchResult.getBatchOperationKey()).isNotNull();
    waitForBatchOperationWithCorrectTotalCount(
        camundaClient, batchResult.getBatchOperationKey(), 1);
    waitForBatchOperationCompleted(camundaClient, batchResult.getBatchOperationKey(), 1, 0);

    waitForDecisionInstanceCount(camundaClient, f -> f.decisionInstanceKey(instanceToDeleteKey), 0);
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionInstanceKey(instanceToKeepKey), 1);
  }

  @Test
  void shouldDeleteDecisionInstanceByKeyUsingSingleOperation() {
    // given
    final var dmnModel = getDmnModelInstance(Strings.newRandomValidBpmnId());
    final Decision decision =
        deployDmnModel(camundaClient, dmnModel, dmnModel.getModel().getModelName());
    final long instanceToDeleteKey =
        evaluateDecision(camundaClient, decision.getDecisionKey(), "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(
        camundaClient, f -> f.decisionDefinitionId(decision.getDmnDecisionId()), 1);

    // when
    final DeleteDecisionInstanceResponse response =
        camundaClient.newDeleteDecisionInstanceCommand(instanceToDeleteKey).send().join();

    // then
    assertThat(response).isNotNull();
    waitForDecisionInstanceCount(
        camundaClient, f -> f.decisionDefinitionId(decision.getDmnDecisionId()), 0);
  }

  @Test
  void shouldDeleteOneOfTwoDecisionInstancesByKeyUsingSingleOperation() {
    // given
    final var dmnModel = getDmnModelInstance(Strings.newRandomValidBpmnId());
    final Decision decision =
        deployDmnModel(camundaClient, dmnModel, dmnModel.getModel().getModelName());
    final long instanceToDeleteKey =
        evaluateDecision(camundaClient, decision.getDecisionKey(), "{}").getDecisionEvaluationKey();
    final long instanceToKeepKey =
        evaluateDecision(camundaClient, decision.getDecisionKey(), "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(
        camundaClient, f -> f.decisionDefinitionId(decision.getDmnDecisionId()), 2);

    // when
    final DeleteDecisionInstanceResponse response =
        camundaClient.newDeleteDecisionInstanceCommand(instanceToDeleteKey).send().join();

    // then
    assertThat(response).isNotNull();
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionInstanceKey(instanceToDeleteKey), 0);
    waitForDecisionInstanceCount(camundaClient, f -> f.decisionInstanceKey(instanceToKeepKey), 1);
  }

  @Test
  void shouldReturnNotFoundForNonExistingDecisionInstanceWithSingularDeletion() {
    // given - non-existing decision instance key
    final long nonExistingKey = 99999L;

    // when/then - try to delete non-existing decision instance should throw not found exception
    assertThatExceptionOfType(ProblemException.class)
        .isThrownBy(
            () -> camundaClient.newDeleteDecisionInstanceCommand(nonExistingKey).send().join())
        .satisfies(
            exception -> {
              assertThat(exception.code()).isEqualTo(404);
              assertThat(exception.details().getDetail())
                  .containsIgnoringCase("decision instance")
                  .containsIgnoringCase("not found");
            });
  }
}
