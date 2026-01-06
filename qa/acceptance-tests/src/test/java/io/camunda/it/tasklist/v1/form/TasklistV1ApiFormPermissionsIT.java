/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1.form;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_USER_TASK;
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
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.JsonUtil;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TasklistV1ApiFormPermissionsIT {
  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication().withAuthorizationsEnabled().withBasicAuth();

  private static final String PROCESS_ID = "processId";
  private static final String ADMIN_USERNAME = "admin1";
  private static final String UNAUTHORIZED_USERNAME = "unauthorized1";
  private static long processDefinitionKey;
  private static String formId;
  private static long taskKey;
  private static TestRestTasklistClient authorizedClient;
  private static TestRestTasklistClient unauthorizedClient;

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
              new Permissions(PROCESS_DEFINITION, READ_USER_TASK, List.of("*"))));

  @UserDefinition
  private static final TestUser UNAUTHORIZED =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  @BeforeAll
  public static void beforeAll(@Authenticated(ADMIN_USERNAME) final CamundaClient adminClient)
      throws Exception {
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var form =
                  adminClient
                      .newDeployResourceCommand()
                      .addResourceFromClasspath("form/form.form")
                      .send()
                      .join()
                      .getForm()
                      .getFirst();
              formId = form.getFormId();
            });

    final var deployment =
        adminClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(PROCESS_ID)
                    .startEvent()
                    .userTask()
                    .zeebeFormId(formId)
                    .zeebeUserTask()
                    .endEvent()
                    .done(),
                "process.bpmn")
            .send()
            .join();
    processDefinitionKey = deployment.getProcesses().getFirst().getProcessDefinitionKey();

    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));
    final var processInstanceEvent =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(json)
            .send()
            .join();
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
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var form = adminClient.newUserTaskGetFormRequest(taskKey).send().join();
              assertThat(form).describedAs("Wait until the form exists").isNotNull();
            });
    await()
        .atMost(CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
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
                  .hasSize(6); // 5 created here + 1 default permission
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
  void shouldBeAuthorizedToGetForm() throws JsonProcessingException {
    final var response =
        authorizedClient.getRequest(
            "v1/forms/%s?processDefinitionKey=%s".formatted(formId, processDefinitionKey));
    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    final var form =
        TestRestTasklistClient.OBJECT_MAPPER.readValue(response.body(), FormResponse.class);
    assertThat(form).isNotNull();
    assertThat(form.getId()).isEqualTo(formId);
  }

  @Test
  void shouldBeUnauthorizedToGetForm() throws JsonProcessingException {
    final var response =
        unauthorizedClient.getRequest(
            "v1/forms/%s?processDefinitionKey=%s".formatted(formId, processDefinitionKey));
    assertThat(response.statusCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    assertThat(response.body())
        .contains("User does not have permission to read resource. Please check your permissions.");
  }
}
