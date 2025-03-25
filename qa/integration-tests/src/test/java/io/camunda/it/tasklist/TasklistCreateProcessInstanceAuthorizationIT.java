/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.it.util.SearchClientsUtil;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class TasklistCreateProcessInstanceAuthorizationIT {

  private static final String PROCESS_ID = "PROCESS_WITH_USER_TASK";

  private static final String ADMIN_USER_NAME = "foo";
  private static final String ADMIN_USER_PASSWORD = "foo";

  private static final String TEST_USER_NAME = "bar";
  private static final String TEST_USER_PASSWORD = "bar";
  private static long testUserKey;

  @AutoClose private static AuthorizationsUtil adminAuthClient;
  @AutoClose private static CamundaClient adminCamundaClient;
  @AutoClose private static TestRestTasklistClient tasklistRestClient;

  @TestZeebe
  private final TestStandaloneCamunda standaloneCamunda =
      new TestStandaloneCamunda()
          .withCamundaExporter()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  @BeforeEach
  public void beforeAll() {
    final var defaultUser = "demo";
    final var searchClients =
        SearchClientsUtil.createSearchClients(standaloneCamunda.getElasticSearchHostAddress());

    // intermediate state, so that a user exists that has
    // access to the storage to retrieve data
    try (final var intermediateAuthClient =
        AuthorizationsUtil.create(
            standaloneCamunda, standaloneCamunda.getElasticSearchHostAddress())) {
      intermediateAuthClient.awaitUserExistsInElasticsearch(defaultUser);
      intermediateAuthClient.createUserWithPermissions(
          ADMIN_USER_NAME,
          ADMIN_USER_PASSWORD,
          new Permissions(ResourceTypeEnum.RESOURCE, PermissionTypeEnum.CREATE, List.of("*")),
          new Permissions(ResourceTypeEnum.AUTHORIZATION, PermissionTypeEnum.CREATE, List.of("*")),
          new Permissions(
              ResourceTypeEnum.PROCESS_DEFINITION,
              PermissionTypeEnum.READ_PROCESS_DEFINITION,
              List.of(PROCESS_ID)),
          new Permissions(
              ResourceTypeEnum.PROCESS_DEFINITION,
              PermissionTypeEnum.READ_PROCESS_INSTANCE,
              List.of(PROCESS_ID)),
          new Permissions(ResourceTypeEnum.USER, PermissionTypeEnum.CREATE, List.of("*")),
          new Permissions(ResourceTypeEnum.AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*")));
    }

    adminCamundaClient =
        AuthorizationsUtil.createClient(standaloneCamunda, ADMIN_USER_NAME, ADMIN_USER_PASSWORD);
    adminAuthClient = new AuthorizationsUtil(standaloneCamunda, adminCamundaClient, searchClients);
    tasklistRestClient = standaloneCamunda.newTasklistClient();

    // deploy a process as admin user
    deployResource(adminCamundaClient);
    waitForProcessToBeDeployed(PROCESS_ID);

    // create new (non-admin) user
    testUserKey = adminAuthClient.createUser(TEST_USER_NAME, TEST_USER_PASSWORD);
  }

  @Test
  public void shouldNotCreateInstanceWithUnauthorizedUser() {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .createProcessInstance(PROCESS_ID);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToCreateInstance() {
    // given
    adminAuthClient.createPermissions(
        TEST_USER_NAME,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.CREATE_PROCESS_INSTANCE,
            List.of(PROCESS_ID)));

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .createProcessInstance(PROCESS_ID);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(PROCESS_ID);
  }

  @Test
  public void shouldCreateProcessInstanceWithoutAuthentication() {
    // when
    final var response = tasklistRestClient.createProcessInstanceViaPublicForm(PROCESS_ID);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureProcessInstanceCreated(PROCESS_ID);
  }

  private void deployResource(final CamundaClient camundaClient) {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("process/process_public_start.bpmn")
        .send()
        .join();
  }

  private void waitForProcessToBeDeployed(final String processDefinitionId) {
    Awaitility.await("should deploy process and export")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  adminCamundaClient
                      .newProcessDefinitionQuery()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(1);
            });
  }

  private void ensureProcessInstanceCreated(final String processDefinitionId) {
    Awaitility.await(
            "should have started process instance with id %s".formatted(processDefinitionId))
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  adminCamundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
            });
  }
}
