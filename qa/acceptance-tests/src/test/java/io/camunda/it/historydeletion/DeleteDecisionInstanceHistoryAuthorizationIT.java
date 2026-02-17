/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historydeletion;

import static io.camunda.client.api.search.enums.PermissionType.CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE_DECISION_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_DECISION_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.BATCH;
import static io.camunda.client.api.search.enums.ResourceType.DECISION_DEFINITION;
import static io.camunda.it.util.DmnBuilderHelper.getDmnModelInstance;
import static io.camunda.it.util.TestHelper.deployDmnModel;
import static io.camunda.it.util.TestHelper.evaluateDecision;
import static io.camunda.it.util.TestHelper.waitForDecisionInstanceCount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Decision;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DeleteDecisionInstanceHistoryAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withDataConfig(
              config -> {
                final var historyDeletionConfig = new HistoryDeletion();
                historyDeletionConfig.setDelayBetweenRuns(Duration.ofMillis(100));
                historyDeletionConfig.setMaxDelayBetweenRuns(Duration.ofMillis(100));
                config.setHistoryDeletion(historyDeletionConfig);
              });

  private static final String SINGLE_AUTHORIZED_USER = "singleAuthorizedUser";
  private static final String BATCH_AUTHORIZED_USER = "batchAuthorizedUser";
  private static final String UNAUTHORIZED_USER = "unauthorizedUser";

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER_DEF =
      new TestUser(
          UNAUTHORIZED_USER,
          "password",
          List.of(new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser BATCH_ONLY_USER_DEF =
      new TestUser(
          BATCH_AUTHORIZED_USER,
          "password",
          List.of(
              new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*")),
              new Permissions(
                  BATCH, CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser DELETE_ONLY_USER_DEF =
      new TestUser(
          SINGLE_AUTHORIZED_USER,
          "password",
          List.of(
              new Permissions(DECISION_DEFINITION, READ_DECISION_INSTANCE, List.of("*")),
              new Permissions(DECISION_DEFINITION, DELETE_DECISION_INSTANCE, List.of("*"))));

  private static final String DMN_DECISION_ID = "testDecisionDefinition";
  private static long decisionKey;

  @BeforeAll
  static void setUp(@Authenticated final CamundaClient adminClient) {
    final var dmnModel = getDmnModelInstance(DMN_DECISION_ID);
    final Decision decision = deployDmnModel(adminClient, dmnModel, DMN_DECISION_ID);
    decisionKey = decision.getDecisionKey();
  }

  @Test
  void shouldBeAuthorizedToDeleteDecisionInstanceHistoryWithAuthorizedUser(
      @Authenticated(SINGLE_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final var decisionInstanceKey =
        evaluateDecision(adminClient, decisionKey, "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(adminClient, f -> f.decisionInstanceKey(decisionInstanceKey), 1);

    // when
    final var result =
        camundaClient.newDeleteDecisionInstanceCommand(decisionInstanceKey).send().join();

    // then - should not throw an exception
    assertThat(result).isNotNull();
  }

  @Test
  void shouldBeUnauthorizedToDeleteDecisionInstanceHistoryWithoutPermissions(
      @Authenticated(UNAUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final var decisionInstanceKey =
        evaluateDecision(adminClient, decisionKey, "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(adminClient, f -> f.decisionInstanceKey(decisionInstanceKey), 1);

    // when
    final ThrowingCallable executeDelete =
        () -> camundaClient.newDeleteDecisionInstanceCommand(decisionInstanceKey).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains("Insufficient permissions to perform operation 'DELETE_DECISION_INSTANCE'");
  }

  @Test
  void shouldBeUnauthorizedToDeleteSingleDecisionInstanceHistoryWithOnlyBatchPermissions(
      @Authenticated(BATCH_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final var decisionInstanceKey =
        evaluateDecision(adminClient, decisionKey, "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(adminClient, f -> f.decisionInstanceKey(decisionInstanceKey), 1);

    // when
    final ThrowingCallable executeDelete =
        () -> camundaClient.newDeleteDecisionInstanceCommand(decisionInstanceKey).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains("Insufficient permissions to perform operation 'DELETE_DECISION_INSTANCE'");
  }

  @Test
  void shouldBeAuthorizedToBatchDeleteDecisionInstanceHistoryWithAuthorizedUser(
      @Authenticated(BATCH_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final String dmnId = "batchSuccessTestDecisionId";
    final var dmnModelBatch = getDmnModelInstance(dmnId);
    final Decision decisionBatch = deployDmnModel(adminClient, dmnModelBatch, dmnId);
    evaluateDecision(adminClient, decisionBatch.getDecisionKey(), "{}").getDecisionEvaluationKey();
    evaluateDecision(adminClient, decisionBatch.getDecisionKey(), "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(adminClient, f -> f.decisionDefinitionId(dmnId), 2);

    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .deleteDecisionInstance()
            .filter(f -> f.decisionDefinitionId(dmnId))
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void shouldBeUnauthorizedToBatchDeleteDecisionInstanceHistoryWithoutBatchPermissions(
      @Authenticated(UNAUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final String dmnId = "batchUnauthorizedId";
    final var dmnModelBatch = getDmnModelInstance(dmnId);
    final Decision decisionBatch = deployDmnModel(adminClient, dmnModelBatch, dmnId);
    evaluateDecision(adminClient, decisionBatch.getDecisionKey(), "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(adminClient, f -> f.decisionDefinitionId(dmnId), 1);

    // when
    final ThrowingCallable executeDelete =
        () ->
            camundaClient
                .newCreateBatchOperationCommand()
                .deleteDecisionInstance()
                .filter(f -> f.decisionDefinitionId(dmnId))
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains(
            "Insufficient permissions to perform operation 'CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE'");
  }

  @Test
  void shouldBeUnauthorizedToBatchDeleteDecisionInstanceHistoryWithOnlySinglePermissions(
      @Authenticated(SINGLE_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    final String dmnId = "batchOnlySingleUnauthorizedId";
    final var dmnModelBatch = getDmnModelInstance(dmnId);
    final Decision decisionBatch = deployDmnModel(adminClient, dmnModelBatch, dmnId);
    evaluateDecision(adminClient, decisionBatch.getDecisionKey(), "{}").getDecisionEvaluationKey();
    waitForDecisionInstanceCount(adminClient, f -> f.decisionDefinitionId(dmnId), 1);

    // when
    final ThrowingCallable executeDelete =
        () ->
            camundaClient
                .newCreateBatchOperationCommand()
                .deleteDecisionInstance()
                .filter(f -> f.decisionDefinitionId(dmnId))
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains(
            "Insufficient permissions to perform operation 'CREATE_BATCH_OPERATION_DELETE_DECISION_INSTANCE'");
  }
}
