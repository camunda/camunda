/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1.task;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE_USER_TASK;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.CamundaMultiDBExtension;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.TaskSearchResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableSearchResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TasklistV1ApiUserTaskPermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  private static final String PROCESS_ID = "processId2";
  private static final String ADMIN_USERNAME = "admin3";
  private static final String UNAUTHORIZED_USERNAME = "unauthorized3";
  private static long taskKey;
  @AutoClose private static TestRestTasklistClient authorizedClient;
  @AutoClose private static TestRestTasklistClient unauthorizedClient;

  private static final String VARIABLE_NAME = "key";
  private static final String VARIABLE_VALUE = "val";

  @UserDefinition
  private static final TestUser ADMIN =
      new TestUser(
          ADMIN_USERNAME,
          ADMIN_USERNAME,
          List.of(
              new Permissions(AUTHORIZATION, CREATE, List.of("*")),
              new Permissions(AUTHORIZATION, READ, List.of("*")),
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, CREATE_PROCESS_INSTANCE, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*")),
              new Permissions(PROCESS_DEFINITION, UPDATE_USER_TASK, List.of("*")),
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  @BeforeAll
  public static void beforeAll(@Authenticated(ADMIN_USERNAME) final CamundaClient adminClient)
      throws Exception {
    adminClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask()
                .zeebeUserTask()
                .zeebeAssignee(ADMIN_USERNAME)
                .endEvent()
                .done(),
            "process2.bpmn")
        .send()
        .join();

    final var processInstanceEvent =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(Collections.singletonMap(VARIABLE_NAME, VARIABLE_VALUE))
            .send()
            .join();

    waitUntilAsserted(
        () -> {
          final var tasks =
              adminClient
                  .newUserTaskSearchRequest()
                  .filter(t -> t.processInstanceKey(processInstanceEvent.getProcessInstanceKey()))
                  .send()
                  .join()
                  .items();
          assertThat(tasks).describedAs("Wait until the task exists").hasSize(1);
          taskKey = tasks.getFirst().getUserTaskKey();
        });

    waitUntilAsserted(
        () -> {
          final var authorizations =
              adminClient
                  .newAuthorizationSearchRequest()
                  .filter(t -> t.ownerId(ADMIN_USERNAME))
                  .send()
                  .join()
                  .items();
          assertThat(authorizations)
              .describedAs("Wait until the authorizations exist")
              .hasSize(8); // 7 created here + 1 default permission
        });

    waitUntilAsserted(
        () -> {
          final var variables = adminClient.newVariableSearchRequest().send().join().items();
          assertThat(variables).describedAs("Wait until the variable exists").hasSize(1);
        });

    authorizedClient =
        STANDALONE_CAMUNDA
            .newTasklistClient()
            .withAuthentication(ADMIN.username(), ADMIN.password());
    unauthorizedClient =
        STANDALONE_CAMUNDA
            .newTasklistClient()
            .withAuthentication(UNAUTHORIZED.username(), UNAUTHORIZED.password());
  }

  @Test
  void shouldBeAuthorizedToSearchUserTasks() throws JsonProcessingException {
    final var response = authorizedClient.searchRequest("v1/tasks/search", null);
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var userTasks =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(response.body(), TaskSearchResponse[].class);
    assertThat(userTasks).hasSize(1);
  }

  @Test
  void shouldBeUnauthorizedToSearchUserTasks() throws JsonProcessingException {
    final var response = unauthorizedClient.searchRequest("v1/tasks/search", null);
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var userTasks =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(response.body(), TaskSearchResponse[].class);
    assertThat(userTasks).isEmpty();
  }

  @Test
  void shouldBeAuthorizedToGetUserTasks() throws JsonProcessingException {
    final var response = authorizedClient.getRequest("v1/tasks/" + taskKey);
    final var userTask =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(response.body(), TaskResponse.class);
    assertThat(userTask).isNotNull();
    assertThat(Long.valueOf(userTask.getId())).isEqualTo(taskKey);
  }

  @Test
  void shouldBeUnauthorizedToGetUserTasks() {
    final var response = unauthorizedClient.getRequest("v1/tasks/" + taskKey);
    assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(response.body()).contains("User does not have permission to perform on this task.");
  }

  @Test
  void shouldBeAuthorizedToSearchUserTaskVariables() throws JsonProcessingException {
    final var response =
        authorizedClient.searchRequest("v1/tasks/" + taskKey + "/variables/search", null);
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var userTasks =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(
            response.body(), VariableSearchResponse[].class);
    assertThat(userTasks).hasSize(1);
  }

  @Test
  void shouldBeUnauthorizedToSearchUserTaskVariables() throws JsonProcessingException {
    final var response =
        unauthorizedClient.searchRequest("v1/tasks/" + taskKey + "/variables/search", null);
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var userTasks =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(
            response.body(), VariableSearchResponse[].class);
    assertThat(userTasks).isEmpty();
  }

  @Test
  void shouldBeAuthorizedToSaveDraftVariables() {
    // when
    // overwriting the existing variable so that the assertions of
    // #shouldBeAuthorizedToSearchUserTaskVariables doesn't break
    final HttpResponse<String> response =
        authorizedClient.saveDraftVariables(taskKey, VARIABLE_NAME, "\"someOtherValue\"");

    // then
    assertThat(response.statusCode()).isEqualTo(HttpStatus.NO_CONTENT.value());
  }

  @Test
  void shouldBeUnauthorizedToSaveDraftVariables() {
    // when
    final HttpResponse<String> response =
        unauthorizedClient.saveDraftVariables(taskKey, VARIABLE_NAME, "\"someOtherValue\"");

    // then
    assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(response.body()).contains("User does not have permission to perform on this task.");
  }

  private static void waitUntilAsserted(ThrowingRunnable runnable) {
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(runnable);
  }
}
