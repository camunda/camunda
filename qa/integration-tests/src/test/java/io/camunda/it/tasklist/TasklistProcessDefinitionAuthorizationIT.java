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
import io.camunda.qa.util.cluster.TestRestTasklistClient.ProcessDefinitionResponse;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.security.configuration.ConfiguredUser;
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
public class TasklistProcessDefinitionAuthorizationIT {

  private static final String PROCESS_WITH_USER_TASK_PRE_ASSIGNED =
      "PROCESS_WITH_USER_TASK_PRE_ASSIGNED";
  private static final String PROCESS_ID_WITH_JOB_BASED_USERTASK =
      "PROCESS_WITH_JOB_BASED_USERTASK";

  private static final String ADMIN_USER_NAME = "foo";
  private static final String ADMIN_USER_PASSWORD = "foo";

  private static final String TEST_USER_NAME = "bar";
  private static final String TEST_USER_PASSWORD = "bar";
  private static long testUserKey;

  @AutoClose private static AuthorizationsUtil adminAuthClient;
  @AutoClose private static CamundaClient adminCamundaClient;
  @AutoClose private static TestRestTasklistClient tasklistRestClient;

  private static long processDefinitionKey;

  @TestZeebe(awaitCompleteTopology = false)
  private TestStandaloneCamunda broker =
      new TestStandaloneCamunda()
          .withCamundaExporter()
          .withAuthorizationsEnabled()
          .withSecurityConfig(
              c ->
                  c.getInitialization()
                      .getUsers()
                      .add(
                          new ConfiguredUser(
                              ADMIN_USER_NAME, ADMIN_USER_PASSWORD, "Admin", "test@camunda.com")))
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  @BeforeEach
  public void beforeEach() {
    final var searchClients =
        SearchClientsUtil.createSearchClients(broker.getElasticSearchHostAddress());

    // intermediate state, so that a user exists that has
    // access to the storage to retrieve data
    try (final var intermediateAuthClient =
        AuthorizationsUtil.create(broker, broker.getElasticSearchHostAddress())) {
      intermediateAuthClient.awaitUserExistsInElasticsearch(ADMIN_USER_NAME);
    }

    adminCamundaClient =
        AuthorizationsUtil.createClient(broker, ADMIN_USER_NAME, ADMIN_USER_PASSWORD);
    adminAuthClient = new AuthorizationsUtil(broker, adminCamundaClient, searchClients);
    tasklistRestClient = broker.newTasklistClient();

    // deploy a process as admin user
    processDefinitionKey =
        deployResource(adminCamundaClient, "process/process_with_assigned_user_task.bpmn");
    waitForProcessToBeDeployed(PROCESS_WITH_USER_TASK_PRE_ASSIGNED);

    // deploy process with a job based user task process
    deployResource(adminCamundaClient, "process/process_job_based_user_task.bpmn");
    waitForProcessToBeDeployed(PROCESS_ID_WITH_JOB_BASED_USERTASK);

    // create new (non-admin) user
    testUserKey = adminAuthClient.createUser(TEST_USER_NAME, TEST_USER_PASSWORD);
  }

  @Test
  public void shouldNotReturnProcessDefinitionWithUnauthorizedUser() {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .getProcessDefinition(processDefinitionKey);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToRetrieveProcessDefinition() {
    // given
    adminAuthClient.createPermissions(
        TEST_USER_NAME,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.READ_PROCESS_DEFINITION,
            List.of(PROCESS_WITH_USER_TASK_PRE_ASSIGNED)));

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .getProcessDefinition(processDefinitionKey);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  public void shouldReturnNoDefinitionsWithUnauthorizedUser() {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .searchProcessDefinitions();

    // then
    assertThat(response).isNotNull();
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldBeAuthorizedToRetrieveDefinitionOne() {
    // given
    adminAuthClient.createPermissions(
        TEST_USER_NAME,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.CREATE_PROCESS_INSTANCE,
            List.of(PROCESS_WITH_USER_TASK_PRE_ASSIGNED)));

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .searchProcessDefinitions();

    // then
    assertThat(response).isNotNull();
    assertThat(response.stream().map(ProcessDefinitionResponse::bpmnProcessId))
        .containsExactly(PROCESS_WITH_USER_TASK_PRE_ASSIGNED);
  }

  @Test
  public void shouldBeAuthorizedToRetrieveDefinitions() {
    // given
    adminAuthClient.createPermissions(
        TEST_USER_NAME,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.CREATE_PROCESS_INSTANCE,
            List.of(PROCESS_WITH_USER_TASK_PRE_ASSIGNED, PROCESS_ID_WITH_JOB_BASED_USERTASK)));

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .searchProcessDefinitions();

    // then
    assertThat(response).isNotNull();
    assertThat(response.stream().map(ProcessDefinitionResponse::bpmnProcessId))
        .containsExactlyInAnyOrder(
            PROCESS_WITH_USER_TASK_PRE_ASSIGNED, PROCESS_ID_WITH_JOB_BASED_USERTASK);
  }

  private long deployResource(final CamundaClient zeebeClient, final String resource) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resource)
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private void waitForProcessToBeDeployed(final String processDefinitionId) {
    Awaitility.await("should deploy process %s and export".formatted(processDefinitionId))
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

  public static long createProcessInstance(final String processDefinitionId) {
    return adminCamundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(processDefinitionId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }
}
