/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.historydeletion;

import static io.camunda.client.api.search.enums.PermissionType.CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.BATCH;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.it.util.TestHelper.deployProcessAndWaitForIt;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToBeCompleted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.configuration.HistoryDeletion;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class DeleteProcessInstanceHistoryAuthorizationIT {

  public static final String PROCESS_ID = "testProcess";

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
          List.of(new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser BATCH_ONLY_USER_DEF =
      new TestUser(
          BATCH_AUTHORIZED_USER,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(
                  BATCH, CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser DELETE_ONLY_USER_DEF =
      new TestUser(
          SINGLE_AUTHORIZED_USER,
          "password",
          List.of(
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, DELETE_PROCESS_INSTANCE, List.of("*"))));

  @BeforeAll
  static void setUp(@Authenticated final CamundaClient adminClient) {
    deployProcessAndWaitForIt(
        adminClient,
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done(),
        PROCESS_ID + ".bpmn");
  }

  @Test
  void shouldBeAuthorizedToDeleteProcessInstanceHistoryWithAuthorizedUser(
      @Authenticated(SINGLE_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToBeCompleted(
        adminClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when
    final var result =
        camundaClient.newDeleteProcessInstanceCommand(processInstanceKey).send().join();

    // then - should not throw an exception
    assertThat(result).isNotNull();
  }

  @Test
  void shouldBeUnauthorizedToDeleteProcessInstanceHistoryWithoutPermissions(
      @Authenticated(UNAUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToBeCompleted(
        adminClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when
    final ThrowingCallable executeDelete =
        () -> camundaClient.newDeleteProcessInstanceCommand(processInstanceKey).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains("Insufficient permissions to perform operation 'DELETE_PROCESS_INSTANCE'");
  }

  @Test
  void shouldBeUnauthorizedToDeleteSingleProcessInstanceHistoryWithOnlyBatchPermissions(
      @Authenticated(BATCH_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final var processInstanceKey =
        startProcessInstance(adminClient, PROCESS_ID).getProcessInstanceKey();
    waitForProcessInstancesToBeCompleted(
        adminClient, f -> f.processInstanceKey(processInstanceKey), 1);

    // when
    final ThrowingCallable executeDelete =
        () -> camundaClient.newDeleteProcessInstanceCommand(processInstanceKey).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains("Insufficient permissions to perform operation 'DELETE_PROCESS_INSTANCE'");
  }

  @Test
  void shouldBeAuthorizedToBatchDeleteProcessInstanceHistoryWithAuthorizedUser(
      @Authenticated(BATCH_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final String scopeId = UUID.randomUUID().toString();
    startScopedProcessInstance(adminClient, PROCESS_ID, scopeId);
    startScopedProcessInstance(adminClient, PROCESS_ID, scopeId);
    waitForProcessInstancesToBeCompleted(
        adminClient, f -> f.variables(getScopedVariables(scopeId)), 2);

    // when
    final var result =
        camundaClient
            .newCreateBatchOperationCommand()
            .deleteProcessInstance()
            .filter(f -> f.variables(getScopedVariables(scopeId)))
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void shouldBeUnauthorizedToBatchDeleteProcessInstanceHistoryWithoutBatchPermissions(
      @Authenticated(UNAUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final String scopeId = UUID.randomUUID().toString();
    startScopedProcessInstance(adminClient, PROCESS_ID, scopeId);
    startScopedProcessInstance(adminClient, PROCESS_ID, scopeId);
    waitForProcessInstancesToBeCompleted(
        adminClient, f -> f.variables(getScopedVariables(scopeId)), 2);

    // when
    final ThrowingCallable executeDelete =
        () ->
            camundaClient
                .newCreateBatchOperationCommand()
                .deleteProcessInstance()
                .filter(f -> f.variables(getScopedVariables(scopeId)))
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains(
            "Insufficient permissions to perform operation 'CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE'");
  }

  @Test
  void shouldBeUnauthorizedToBatchDeleteProcessInstanceHistoryWithOnlySinglePermissions(
      @Authenticated(SINGLE_AUTHORIZED_USER) final CamundaClient camundaClient,
      @Authenticated final CamundaClient adminClient) {
    // given
    final String scopeId = UUID.randomUUID().toString();
    startScopedProcessInstance(adminClient, PROCESS_ID, scopeId);
    startScopedProcessInstance(adminClient, PROCESS_ID, scopeId);
    waitForProcessInstancesToBeCompleted(
        adminClient, f -> f.variables(getScopedVariables(scopeId)), 2);

    // when
    final ThrowingCallable executeDelete =
        () ->
            camundaClient
                .newCreateBatchOperationCommand()
                .deleteProcessInstance()
                .filter(f -> f.variables(getScopedVariables(scopeId)))
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeDelete).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .contains(
            "Insufficient permissions to perform operation 'CREATE_BATCH_OPERATION_DELETE_PROCESS_INSTANCE'");
  }
}
