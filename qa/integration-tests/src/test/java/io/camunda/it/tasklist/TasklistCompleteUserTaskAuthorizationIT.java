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
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.it.util.SearchClientsUtil;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class TasklistCompleteUserTaskAuthorizationIT {

  private static final String PROCESS_WITH_USER_TASK_PRE_ASSIGNED =
      "PROCESS_WITH_USER_TASK_PRE_ASSIGNED";
  private static final String PROCESS_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED =
      "PROCESS_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED";

  private static final String ADMIN_USER_NAME = "foo";
  private static final String ADMIN_USER_PASSWORD = "foo";

  private static final String TEST_USER_NAME = "bar";
  private static final String TEST_USER_PASSWORD = "bar";
  private static long testUserKey;

  @AutoClose private static AuthorizationsUtil adminAuthClient;
  @AutoClose private static CamundaClient adminCamundaClient;
  @AutoClose private static TestRestTasklistClient tasklistRestClient;

  private long userTaskKey;
  private long userTaskKeyWithJobBasedUserTask;

  @TestZeebe
  private TestStandaloneCamunda standaloneCamunda =
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
              List.of("*")),
          new Permissions(
              ResourceTypeEnum.PROCESS_DEFINITION, PermissionTypeEnum.READ_USER_TASK, List.of("*")),
          new Permissions(
              ResourceTypeEnum.PROCESS_DEFINITION,
              PermissionTypeEnum.READ_PROCESS_INSTANCE,
              List.of("*")),
          new Permissions(
              ResourceTypeEnum.PROCESS_DEFINITION,
              PermissionTypeEnum.CREATE_PROCESS_INSTANCE,
              List.of("*")),
          new Permissions(ResourceTypeEnum.USER, PermissionTypeEnum.CREATE, List.of("*")),
          new Permissions(ResourceTypeEnum.AUTHORIZATION, PermissionTypeEnum.UPDATE, List.of("*")));
    }

    adminCamundaClient =
        AuthorizationsUtil.createClient(standaloneCamunda, ADMIN_USER_NAME, ADMIN_USER_PASSWORD);
    adminAuthClient = new AuthorizationsUtil(standaloneCamunda, adminCamundaClient, searchClients);
    tasklistRestClient = standaloneCamunda.newTasklistClient();

    // deploy a process as admin user
    deployResource(adminCamundaClient, "process/process_with_assigned_user_task.bpmn");
    waitForProcessToBeDeployed(PROCESS_WITH_USER_TASK_PRE_ASSIGNED);

    // deploy process with a job based user task process
    deployResource(adminCamundaClient, "process/process_with_assigned_job_based_user_task.bpmn");
    waitForProcessToBeDeployed(PROCESS_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED);

    // create a process instance
    final var processInstanceKey = createProcessInstance(PROCESS_WITH_USER_TASK_PRE_ASSIGNED);
    userTaskKey = awaitUserTaskBeingAvailable(processInstanceKey);

    // create a process instance with job based user task
    final var processInstanceKeyWithJobBasedUserTask =
        createProcessInstance(PROCESS_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED);
    userTaskKeyWithJobBasedUserTask =
        awaitJobBasedUserTaskBeingAvailable(processInstanceKeyWithJobBasedUserTask);

    // create new (non-admin) user
    testUserKey = adminAuthClient.createUser(TEST_USER_NAME, TEST_USER_PASSWORD);
  }

  @Test
  public void shouldNotCompleteUserTaskWithUnauthorizedUser() {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKey);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldNotCompleteJobBasedUserTaskWithUnauthorizedUser() {
    // given (non-admin) user without any authorizations

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKeyWithJobBasedUserTask);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(403);
  }

  @Test
  public void shouldBeAuthorizedToCompleteUserTask() {
    // given
    adminAuthClient.createPermissions(
        TEST_USER_NAME,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.UPDATE_USER_TASK,
            List.of(PROCESS_WITH_USER_TASK_PRE_ASSIGNED)));

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKey);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureUserTaskIsCompleted(userTaskKey);
  }

  @Test
  public void shouldBeAuthorizedToCompleteJobBasedUserTask() {
    // given
    adminAuthClient.createPermissions(
        TEST_USER_NAME,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.UPDATE_USER_TASK,
            List.of(PROCESS_WITH_JOB_BASED_USERTASK_PRE_ASSIGNED)));

    // when
    final var response =
        tasklistRestClient
            .withAuthentication(TEST_USER_NAME, TEST_USER_PASSWORD)
            .completeUserTask(userTaskKeyWithJobBasedUserTask);

    // then
    assertThat(response).isNotNull();
    assertThat(response.statusCode()).isEqualTo(200);
    ensureJobBasedUserTaskIsCompleted(userTaskKeyWithJobBasedUserTask);
  }

  private void deployResource(final CamundaClient camundaClient, final String resource) {
    camundaClient.newDeployResourceCommand().addResourceFromClasspath(resource).send().join();
  }

  private void waitForProcessToBeDeployed(final String processDefinitionId) {
    Awaitility.await("should deploy process %s and export".formatted(processDefinitionId))
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  adminCamundaClient
                      .newProcessDefinitionSearchRequest()
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

  public static long awaitUserTaskBeingAvailable(final long processInstanceKey) {
    final AtomicLong userTaskKey = new AtomicLong();
    Awaitility.await("should create an user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  adminCamundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.processInstanceKey(processInstanceKey))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
              userTaskKey.set(result.items().getFirst().getUserTaskKey());
            });
    return userTaskKey.get();
  }

  public static long awaitJobBasedUserTaskBeingAvailable(final long processInstanceKey) {
    final AtomicLong userTaskKey = new AtomicLong();
    final var processInstanceQuery =
        SearchQueryBuilders.and(
            SearchQueryBuilders.term(TaskTemplate.PROCESS_INSTANCE_ID, processInstanceKey),
            SearchQueryBuilders.term(
                TaskTemplate.JOIN_FIELD_NAME, TaskJoinRelationshipType.TASK.getType()));
    Awaitility.await("should create a job-based user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = tasklistRestClient.searchJobBasedUserTasks(processInstanceQuery);
              assertThat(result.hits()).hasSize(1);
              userTaskKey.set(result.hits().getFirst().source().getKey());
            });
    return userTaskKey.get();
  }

  public static void ensureUserTaskIsCompleted(final long userTaskKey) {
    Awaitility.await("should create an user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  adminCamundaClient
                      .newUserTaskSearchRequest()
                      .filter(f -> f.userTaskKey(userTaskKey).state(UserTaskState.COMPLETED))
                      .send()
                      .join();
              assertThat(result.items()).hasSize(1);
            });
  }

  public static void ensureJobBasedUserTaskIsCompleted(final long userTaskKey) {
    final var userTaskQuery = SearchQueryBuilders.term(TaskTemplate.KEY, userTaskKey);
    final var stateQuery = SearchQueryBuilders.term(TaskTemplate.STATE, "COMPLETED");
    final var finalQuery = SearchQueryBuilders.and(userTaskQuery, stateQuery);

    Awaitility.await("should create an user task")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = tasklistRestClient.searchJobBasedUserTasks(finalQuery);
              assertThat(result.totalHits()).isGreaterThanOrEqualTo(1L);
            });
  }
}
