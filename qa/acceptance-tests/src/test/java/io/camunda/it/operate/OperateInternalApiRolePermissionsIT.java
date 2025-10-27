/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.UPDATE;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static io.camunda.client.api.search.enums.ResourceType.ROLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.Profile;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.http.HttpStatus;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateInternalApiRolePermissionsIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthorizationsEnabled()
          .withBasicAuth()
          .withAdditionalProfile(Profile.OPERATE);

  private static final String BASE_PATH = "api/process-instances";
  private static final String PROCESS_ID = "processId";
  private static final String ADMIN_USERNAME = "admin";
  private static final String AUTHORIZED_USERNAME = "authorized";
  private static final String UNAUTHORIZED_USERNAME = "unauthorized";
  private static final ObjectMapper OBJECT_MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
              new Permissions(PROCESS_DEFINITION, READ_PROCESS_INSTANCE, List.of("*"))));

  @UserDefinition
  private static final TestUser AUTHORIZED_USER =
      new TestUser(AUTHORIZED_USERNAME, AUTHORIZED_USERNAME, List.of());

  @UserDefinition
  private static final TestUser UNAUTHORIZED_USER =
      new TestUser(UNAUTHORIZED_USERNAME, UNAUTHORIZED_USERNAME, List.of());

  private static long processInstanceKey;
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
        .permissionTypes(
            PermissionType.READ_PROCESS_INSTANCE, PermissionType.UPDATE_PROCESS_INSTANCE)
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
            Bpmn.createExecutableProcess(PROCESS_ID).startEvent().userTask().endEvent().done(),
            "process.bpmn")
        .send()
        .join();
    processInstanceKey =
        adminClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join()
            .getProcessInstanceKey();
    await()
        .untilAsserted(
            () ->
                assertThat(
                        adminClient
                            .newProcessInstanceSearchRequest()
                            .filter(f -> f.processInstanceKey(processInstanceKey))
                            .send()
                            .join()
                            .items())
                    .describedAs("Wait until the process instance is exported")
                    .hasSize(1));
  }

  @Test
  void shouldBePermittedToSearchUsingInternalApi(
      @Authenticated(AUTHORIZED_USERNAME) final CamundaClient client) throws Exception {
    final var count = searchRunningProcessInstances(client, AUTHORIZED_USERNAME);
    assertThat(count.totalCount).describedAs("Has retrieved the running instance").isEqualTo(1);
  }

  @Test
  void shouldBeUnauthorizedToSearchUsingInternalApi(
      @Authenticated(UNAUTHORIZED_USERNAME) final CamundaClient client) throws Exception {
    final var count = searchRunningProcessInstances(client, UNAUTHORIZED_USERNAME);
    // Searching when unauthorized results in no instances returned, not an unauthorized error
    assertThat(count.totalCount).describedAs("Has not retrieved any instances").isEqualTo(0);
  }

  @Test
  void shouldBePermittedToGetUsingInternalApi(
      @Authenticated(AUTHORIZED_USERNAME) final CamundaClient client) throws Exception {
    final var statusCode =
        getRunningProcessInstance(client, AUTHORIZED_USERNAME, processInstanceKey);
    assertThat(statusCode)
        .describedAs("Is authorized to get the process instance")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToGetUsingInternalApi(
      @Authenticated(UNAUTHORIZED_USERNAME) final CamundaClient client) throws Exception {
    final var statusCode =
        getRunningProcessInstance(client, UNAUTHORIZED_USERNAME, processInstanceKey);
    assertThat(statusCode)
        .describedAs("Is unauthorized to get the process instance")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void shouldBePermittedToAddVariable(
      @Authenticated(AUTHORIZED_USERNAME) final CamundaClient client) throws Exception {
    final var statusCode =
        addVariableToProcessInstance(client, AUTHORIZED_USERNAME, processInstanceKey);
    assertThat(statusCode)
        .describedAs("Is authorized to add variable")
        .isEqualTo(HttpStatus.OK.value());
  }

  @Test
  void shouldBeUnauthorizedToAddVariable(
      @Authenticated(UNAUTHORIZED_USERNAME) final CamundaClient client) throws Exception {
    final var statusCode =
        addVariableToProcessInstance(client, UNAUTHORIZED_USERNAME, processInstanceKey);
    assertThat(statusCode)
        .describedAs("Is unauthorized to add variable")
        .isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  private ResponseCount searchRunningProcessInstances(
      final CamundaClient client, final String username)
      throws URISyntaxException, IOException, InterruptedException {
    final String url = client.getConfiguration().getRestAddress() + BASE_PATH;

    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, username).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(url))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
              {
                "query": {
                  "bpmnProcessId": "%s",
                  "running": true,
                  "active": true
                }
              }"""
                        .formatted(PROCESS_ID)))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .header("Content-Type", "application/json")
            .build();

    // Send the request and get the response
    final var response = httpClient.send(request, BodyHandlers.ofString());
    return OBJECT_MAPPER.readValue(response.body(), ResponseCount.class);
  }

  private int getRunningProcessInstance(
      final CamundaClient client, final String username, final long processInstanceKey)
      throws URISyntaxException, IOException, InterruptedException {
    final String url =
        client.getConfiguration().getRestAddress() + BASE_PATH + "/" + processInstanceKey;

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

  private int addVariableToProcessInstance(
      final CamundaClient client, final String username, final long processInstanceKey)
      throws URISyntaxException, IOException, InterruptedException {
    final String url =
        client.getConfiguration().getRestAddress()
            + BASE_PATH
            + "/"
            + processInstanceKey
            + "/operation";

    final var encodedCredentials =
        Base64.getEncoder().encodeToString("%s:%s".formatted(username, username).getBytes());
    final HttpRequest request =
        HttpRequest.newBuilder()
            .uri(new URI(url))
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    """
              {
                "operationType": "ADD_VARIABLE",
                "name": "addVariable",
                "variableScopeId": "%s",
                "variableName": "%s",
                "variableValue": "%s"
              }"""
                        .formatted(processInstanceKey, UUID.randomUUID(), UUID.randomUUID())))
            .header("Authorization", "Basic %s".formatted(encodedCredentials))
            .header("Content-Type", "application/json")
            .build();
    // Send the request and get the response
    return httpClient.send(request, BodyHandlers.ofString()).statusCode();
  }

  private record ResponseCount(int totalCount) {}
}
