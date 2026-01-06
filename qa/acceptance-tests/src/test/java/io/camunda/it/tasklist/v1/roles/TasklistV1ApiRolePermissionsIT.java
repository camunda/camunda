/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1.roles;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TasklistV1ApiRolePermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  private static final String PROCESS_ID = "processId";
  private static final String ADMIN_USERNAME = "admin";
  private static final String AUTHORIZED_USERNAME = "authorized";
  private static final String UNAUTHORIZED_USERNAME = "unauthorized";

  @UserDefinition
  private static final TestUser ADMIN =
      new TestUser(
          ADMIN_USERNAME,
          ADMIN_USERNAME,
          List.of(
              new Permissions(ROLE, CREATE, List.of("*")),
              new Permissions(ROLE, UPDATE, List.of("*")),
              new Permissions(AUTHORIZATION, CREATE, List.of("*")),
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser AUTHORIZED_USER =
      new TestUser(AUTHORIZED_USERNAME, AUTHORIZED_USERNAME, List.of());

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  private static long processDefinitionKey;
  private static long taskKey;
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeAll
  public static void beforeAll(
      @Authenticated(ADMIN_USERNAME) final CamundaClient adminClient,
      @Authenticated(AUTHORIZED_USERNAME) final CamundaClient authorizedClient,
      @Authenticated(UNAUTHORIZED_USERNAME) final CamundaClient unauthorizedClient)
      throws Exception {
    final var roleId = Strings.newRandomValidIdentityId();
    adminClient.newCreateRoleCommand().roleId(roleId).name("role name").send().join();
    adminClient
        .newCreateAuthorizationCommand()
        .ownerId(roleId)
        .ownerType(OwnerType.ROLE)
        .resourceId("*")
        .resourceType(PROCESS_DEFINITION)
        .permissionTypes(PermissionType.READ_PROCESS_DEFINITION, PermissionType.UPDATE_USER_TASK)
        .send()
        .join();
    adminClient
        .newAssignRoleToUserCommand()
        .roleId(roleId)
        .username(AUTHORIZED_USERNAME)
        .send()
        .join();

    adminClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask()
                .zeebeUserTask()
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join();
    final var processInstanceEvent =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();
    processDefinitionKey = processInstanceEvent.getProcessDefinitionKey();
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var tasks =
                  adminClient
                      .newUserTaskSearchRequest()
                      .filter(
                          t -> t.processInstanceKey(processInstanceEvent.getProcessInstanceKey()))
                      .send()
                      .join()
                      .items();
              assertThat(tasks).describedAs("Wait until the task exists").hasSize(1);
              taskKey = tasks.getFirst().getUserTaskKey();
            });
  }

  @Test
  void shouldBePermittedToGetProcess(
      @Authenticated(AUTHORIZED_USERNAME) final CamundaClient client) {
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var statusCode =
                  getRunningProcessInstance(client, AUTHORIZED_USERNAME, processDefinitionKey);
              assertThat(statusCode)
                  .describedAs("Is authorized to get the process")
                  .isEqualTo(HttpStatus.OK.value());
            });
  }

  @Test
  void shouldBeUnauthorizedToGetProcess(
      @Authenticated(UNAUTHORIZED_USERNAME) final CamundaClient client) {
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var statusCode =
                  getRunningProcessInstance(client, UNAUTHORIZED_USERNAME, processDefinitionKey);
              assertThat(statusCode)
                  .describedAs("Is unauthorized to get the process")
                  .isEqualTo(HttpStatus.FORBIDDEN.value());
            });
  }

  @Test
  void shouldBePermittedToAssignTask(
      @Authenticated(AUTHORIZED_USERNAME) final CamundaClient client) {
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var statusCode = assignTask(client, AUTHORIZED_USERNAME, taskKey);
              assertThat(statusCode)
                  .describedAs("Is authorized to assign the task")
                  .isEqualTo(HttpStatus.OK.value());
            });
  }

  @Test
  void shouldBeUnauthorizedToAssignTask(
      @Authenticated(UNAUTHORIZED_USERNAME) final CamundaClient client) {
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var statusCode = assignTask(client, UNAUTHORIZED_USERNAME, taskKey);
              assertThat(statusCode)
                  .describedAs("Is unauthorized to assign the task")
                  .isEqualTo(HttpStatus.FORBIDDEN.value());
            });
  }

  private int getRunningProcessInstance(
      final CamundaClient client, final String username, final long processDefinitionKey)
      throws URISyntaxException, IOException, InterruptedException {
    final String url =
        client.getConfiguration().getRestAddress()
            + "v1/internal/processes/"
            + processDefinitionKey;

    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, username).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(url))
            .GET()
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    // Send the request and get the response
    return httpClient.send(request, BodyHandlers.ofString()).statusCode();
  }

  private int assignTask(final CamundaClient client, final String username, final long taskId)
      throws URISyntaxException, IOException, InterruptedException {
    final String url =
        client.getConfiguration().getRestAddress() + "v1/tasks/" + taskId + "/assign";

    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, username).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(url))
            .method(
                "PATCH",
                BodyPublishers.ofString(
                    """
                          {
                            "assignee": "%s",
                            "allowOverrideAssignment": true
                          }
                          """
                        .formatted(username)))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .build();

    // Send the request and get the response
    return httpClient.send(request, BodyHandlers.ofString()).statusCode();
  }
}
