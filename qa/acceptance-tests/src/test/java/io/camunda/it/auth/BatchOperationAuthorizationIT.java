/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.BATCH;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForScopedProcessInstancesToStart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateBatchOperationResponse;
import io.camunda.client.api.search.enums.BatchOperationItemState;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.response.BatchOperationItems.BatchOperationItem;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
class BatchOperationAuthorizationIT {

  // Process IDs
  public static final String SERVICE_TASKS_V_1 = "service_tasks_v1";
  public static final String SERVICE_TASKS_V_2 = "service_tasks_v2";
  public static final String INCIDENT_PROCESS_V_1 = "incident_process_v1";

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String RESTRICTED_CANCEL = "restrictedCancelUser";
  private static final String RESTRICTED_READ = "restrictedReadUser";
  private static final String FORBIDDEN = "forbiddenUser";

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_DEFINITION, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(BATCH, CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE, List.of("*")),
              new Permissions(BATCH, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED,
          "password",
          List.of(
              new Permissions(BATCH, CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE, List.of("*")),
              new Permissions(BATCH, READ, List.of("*")),
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(SERVICE_TASKS_V_1)),
              new Permissions(
                  PROCESS_DEFINITION, UPDATE_PROCESS_INSTANCE, List.of(SERVICE_TASKS_V_1))));

  @UserDefinition
  private static final TestUser RESTRICTED_CANCEL_USER =
      new TestUser(
          RESTRICTED_CANCEL,
          "password",
          List.of(
              new Permissions(BATCH, CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE, List.of("*")),
              new Permissions(BATCH, READ, List.of("*")),
              new Permissions(
                  PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of(SERVICE_TASKS_V_1))));

  @UserDefinition
  private static final TestUser RESTRICTED_READ_USER =
      new TestUser(
          RESTRICTED_READ,
          "password",
          List.of(
              new Permissions(
                  BATCH, CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser FORBIDDEN_USER = new TestUser(FORBIDDEN, "password", List.of());

  private long serviceTaskV1Key;

  @BeforeAll
  static void setUp(@Authenticated(ADMIN) final CamundaClient camundaClient) {
    final List<String> processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(
        process -> deployResource(camundaClient, String.format("process/%s", process)));
    waitForProcessesToBeDeployed(camundaClient, processes.size());
  }

  String startProcessesWithScope(final CamundaClient camundaClient) {
    final String scopeId = UUID.randomUUID().toString();

    serviceTaskV1Key =
        startScopedProcessInstance(camundaClient, SERVICE_TASKS_V_1, scopeId)
            .getProcessInstanceKey();
    startScopedProcessInstance(camundaClient, SERVICE_TASKS_V_2, scopeId);
    startScopedProcessInstance(camundaClient, INCIDENT_PROCESS_V_1, scopeId);

    waitForScopedProcessInstancesToStart(camundaClient, scopeId, 3);
    return scopeId;
  }

  @Test
  void adminShouldStartBatchOperationWithAllItems(
      @Authenticated(ADMIN) final CamundaClient camundaClient) {
    // given some processes with a scopeId in variables
    final var scopeId = startProcessesWithScope(camundaClient);

    // when we start the batch
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaClient, batchOperationKey, 3);

    // then
    final var batchOperationResponse =
        camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
    assertThat(batchOperationResponse).isNotNull();
    assertThat(batchOperationResponse.getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  void adminShouldQueryBatchOperation(@Authenticated(ADMIN) final CamundaClient camundaClient) {
    // given some processes with a scopeId in variables
    final var scopeId = startProcessesWithScope(camundaClient);

    // when we start the batch
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaClient, batchOperationKey, 3);

    // when
    final var batchOperationResponse =
        camundaClient
            .newBatchOperationSearchRequest()
            .filter(f -> f.batchOperationKey(String.valueOf(batchOperationKey)))
            .send()
            .join();

    // then
    assertThat(batchOperationResponse).isNotNull();
    assertThat(batchOperationResponse.items().getFirst().getOperationsTotalCount()).isEqualTo(3);
  }

  @Test
  void restrictedUserShouldStartBatchOperationWithRestrictedItemsOnly(
      @Authenticated(ADMIN) final CamundaClient camundaAdminClient,
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    // given
    final var scopeId = startProcessesWithScope(camundaAdminClient);

    // when
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaClient, batchOperationKey, 1, BatchOperationState.COMPLETED);

    // then
    waitForBatchOperationItems(
        camundaClient,
        batchOperationKey,
        (items) -> {
          assertThat(items).hasSize(1);
          final var item = items.getFirst();
          assertThat(item.getProcessInstanceKey()).isEqualTo(serviceTaskV1Key);
          assertThat(item.getStatus()).isEqualTo(BatchOperationItemState.COMPLETED);
        });
  }

  @Test
  void restrictedUserShouldStartBatchOperationAndFailOnRestrictedItem(
      @Authenticated(ADMIN) final CamundaClient camundaAdminClient,
      @Authenticated(RESTRICTED_CANCEL) final CamundaClient camundaClient) {
    // given some processes with a scopeId in variables
    final var scopeId = startProcessesWithScope(camundaAdminClient);

    // when we start the batch
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaClient, batchOperationKey, 1, BatchOperationState.COMPLETED);

    // then
    waitForBatchOperationItems(
        camundaClient,
        batchOperationKey,
        (items) -> {
          assertThat(items).hasSize(1);
          final var item = items.getFirst();
          assertThat(item.getProcessInstanceKey()).isEqualTo(serviceTaskV1Key);
          assertThat(item.getStatus()).isEqualTo(BatchOperationItemState.FAILED);
          assertThat(item.getErrorMessage())
              .isEqualTo(
                  "FORBIDDEN: Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, service_tasks_v1]'");
        });
  }

  @Test
  void restrictedUserShouldReadSingleBatchOperation(
      @Authenticated(ADMIN) final CamundaClient camundaAdminClient,
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    // given some processes with a scopeId in variables
    final var scopeId = startProcessesWithScope(camundaAdminClient);

    // when we start the batch
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaClient, batchOperationKey, 1);

    // when
    final var batchOperationResponse =
        camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();

    // then
    assertThat(batchOperationResponse).isNotNull();
  }

  @Test
  void restrictedUserShouldQueryBatchOperation(
      @Authenticated(ADMIN) final CamundaClient camundaAdminClient,
      @Authenticated(RESTRICTED) final CamundaClient camundaClient) {
    // given some processes with a scopeId in variables
    final var scopeId = startProcessesWithScope(camundaAdminClient);

    // when we start the batch
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaClient, batchOperationKey, 1);

    // when
    final var batchOperationResponse =
        camundaClient
            .newBatchOperationSearchRequest()
            .filter(f -> f.batchOperationKey(String.valueOf(batchOperationKey)))
            .send()
            .join();

    // then
    assertThat(batchOperationResponse).isNotNull();
  }

  @Test
  void shouldReturnForbiddenForUnauthorizedBatchOperation(
      @Authenticated(FORBIDDEN) final CamundaClient camundaClient) {
    // when
    final ThrowingCallable executeGet =
        () ->
            camundaClient
                .newCreateBatchOperationCommand()
                .processInstanceCancel()
                .filter(b -> {})
                .send()
                .join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo(
            "Command 'CREATE' rejected with code 'FORBIDDEN': Insufficient permissions to perform operation 'CREATE_BATCH_OPERATION_CANCEL_PROCESS_INSTANCE' on resource 'BATCH'");
  }

  @Test
  void shouldReturnForbiddenForUnauthorizedReadOfBatchOperation(
      @Authenticated(ADMIN) final CamundaClient camundaAdminClient,
      @Authenticated(RESTRICTED_READ) final CamundaClient camundaRestictedClient) {
    // given some processes with a scopeId in variables
    final var scopeId = startProcessesWithScope(camundaAdminClient);

    // when we start the batch
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaAdminClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaAdminClient, batchOperationKey, 3);

    // then we should find nothing with our restricted user
    Awaitility.await("should not return batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              int code = 0;
              try {
                camundaRestictedClient.newBatchOperationGetRequest(batchOperationKey).send().join();
              } catch (final ProblemException e) {
                code = e.code();
              }
              assertThat(code).isEqualTo(403);
            });
  }

  @Test
  void shouldReturnEmptyForUnauthorizedQueryOfBatchOperation(
      @Authenticated(ADMIN) final CamundaClient camundaAdminClient,
      @Authenticated(RESTRICTED_READ) final CamundaClient camundaRestictedClient) {
    // given some processes with a scopeId in variables
    final var scopeId = startProcessesWithScope(camundaAdminClient);

    // when we start the batch
    final var batchOperationCreatedResponse =
        createProcessInstanceCancelBatchOperation(camundaRestictedClient, scopeId);

    // and we wait for it
    assertThat(batchOperationCreatedResponse).isNotNull();
    final var batchOperationKey = batchOperationCreatedResponse.getBatchOperationKey();
    waitForBatchOperation(camundaAdminClient, batchOperationKey, 0);

    // then we should find nothing with our restricted user
    Awaitility.await("should not return batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              final var batchOperationResponse =
                  camundaRestictedClient
                      .newBatchOperationSearchRequest()
                      .filter(f -> f.batchOperationKey(String.valueOf(batchOperationKey)))
                      .send()
                      .join();
              assertThat(batchOperationResponse.items()).isEmpty();
            });
  }

  private static void deployResource(final CamundaClient camundaClient, final String resourceName) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resourceName).send().join();
  }

  private static void waitForProcessesToBeDeployed(
      final CamundaClient camundaClient, final int expectedCount) {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newProcessDefinitionSearchRequest().send().join();
              assertThat(result.items().size()).isEqualTo(expectedCount);
            });
  }

  public static void waitForBatchOperation(
      final CamundaClient camundaClient, final String batchOperationKey, final long itemsCount) {
    Awaitility.await("should wait for started batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
              assertThat(batch).isNotNull();
              assertThat(batch.getOperationsTotalCount()).isEqualTo(itemsCount);
            });
  }

  public static void waitForBatchOperation(
      final CamundaClient camundaClient,
      final String batchOperationKey,
      final long totalItemsCount,
      final BatchOperationState expectedState) {
    Awaitility.await("should wait for started batch operation")
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(100))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var batch =
                  camundaClient.newBatchOperationGetRequest(batchOperationKey).send().join();
              assertThat(batch).isNotNull();
              assertThat(batch.getStatus())
                  .withFailMessage(
                      "Expected batch operation to be in state '%s', but was '%s'",
                      expectedState, batch.getStatus())
                  .isEqualTo(expectedState);
              assertThat(batch.getOperationsTotalCount())
                  .withFailMessage(
                      "Expected batch operation to have %d items, but had %d",
                      totalItemsCount, batch.getOperationsTotalCount())
                  .isEqualTo(totalItemsCount);
            });
  }

  private void waitForBatchOperationItems(
      final CamundaClient camundaClient,
      final String batchOperationKey,
      final Consumer<List<BatchOperationItem>> assertions) {
    Awaitility.await()
        .untilAsserted(
            () ->
                camundaClient
                    .newBatchOperationItemsSearchRequest()
                    .filter(f -> f.batchOperationKey(String.valueOf(batchOperationKey)))
                    .send()
                    .join()
                    .items(),
            assertions);
  }

  private static CreateBatchOperationResponse createProcessInstanceCancelBatchOperation(
      final CamundaClient camundaClient, final String scopeId) {
    return camundaClient
        .newCreateBatchOperationCommand()
        .processInstanceCancel()
        .filter(b -> b.variables(getScopedVariables(scopeId)))
        .send()
        .join();
  }
}
