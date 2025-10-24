/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.tasklist.v1;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestTasklistClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

@MultiDbTest(setupKeycloak = true)
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class TasklistV1ApiOidcClientIT {

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthorizationsEnabled()
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setClientIdClaim("client_id"))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setUsernameClaim("no_username"))
          .withSecurityConfig(c -> c.getInitialization().getUsers().clear());

  // Injected by the MultiDbTest extension
  private static KeycloakContainer keycloak;

  private static long processInstanceKey;

  @ClientDefinition
  private static final TestClient ADMIN =
      new TestClient(
          "admin",
          List.of(
              new Permissions(ResourceType.RESOURCE, PermissionType.CREATE, List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.CREATE_PROCESS_INSTANCE,
                  List.of("*")),
              new Permissions(
                  ResourceType.PROCESS_DEFINITION, PermissionType.READ_USER_TASK, List.of("*"))));

  @ClientDefinition
  private static final TestClient PRIVILEGED_CLIENT =
      new TestClient(
          "fooClient",
          List.of(
              new Permissions(
                  ResourceType.PROCESS_DEFINITION, PermissionType.READ_USER_TASK, List.of("*"))));

  @ClientDefinition
  private static final TestClient UNPRIVILEGED_CLIENT = new TestClient("barClient", List.of());

  @BeforeAll
  public static void beforeAll(@TempDir final Path tempDir) throws Exception {
    try (CamundaClient adminClient = createCamundaClient(ADMIN, tempDir)) {

      final String processId = "user-task-process";
      final BpmnModelInstance bpmnModel =
          Bpmn.createExecutableProcess(processId)
              .startEvent()
              .userTask()
              .zeebeUserTask()
              .endEvent()
              .done();

      TestHelper.deployResource(adminClient, bpmnModel, "user-task-process.bpmn");

      processInstanceKey =
          TestHelper.startProcessInstance(adminClient, processId).getProcessInstanceKey();
      TestHelper.waitForUserTasks(
          adminClient,
          q -> q.state(UserTaskState.CREATED).processInstanceKey(processInstanceKey),
          1);
    }
  }

  @Test
  public void shouldReturnTaskViaV1ApiToPrivilegedClient(@TempDir final Path tempDir)
      throws Exception {
    // given
    try (final var tasklistClient =
        STANDALONE_CAMUNDA.newTasklistClient(
            buildOAuthCredentialsProvider(PRIVILEGED_CLIENT, tempDir))) {

      // when
      final HttpResponse<String> searchResponse = tasklistClient.searchTasks(processInstanceKey);

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final List<Map<String, Object>> tasks =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(searchResponse.body(), List.class);

      assertThat(tasks).hasSize(1);

      final Map<String, Object> task = tasks.get(0);
      assertThat(task).containsEntry("processInstanceKey", Long.toString(processInstanceKey));
    }
  }

  @Test
  public void shouldNotReturnTaskViaV1ApiToUnprivilegedClient(@TempDir final Path tempDir)
      throws Exception {
    // given
    try (final var tasklistClient =
        STANDALONE_CAMUNDA.newTasklistClient(
            buildOAuthCredentialsProvider(UNPRIVILEGED_CLIENT, tempDir))) {

      // when
      final HttpResponse<String> searchResponse = tasklistClient.searchTasks(processInstanceKey);

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final List<Map<String, Object>> tasks =
          TestRestTasklistClient.OBJECT_MAPPER.readValue(searchResponse.body(), List.class);

      assertThat(tasks).hasSize(0);
    }
  }

  private static CamundaClient createCamundaClient(
      final TestClient testClient, final Path tempDir) {
    return STANDALONE_CAMUNDA
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(buildOAuthCredentialsProvider(testClient, tempDir))
        .build();
  }

  private static CredentialsProvider buildOAuthCredentialsProvider(
      final TestClient testClient, final Path tempDir) {
    return new OAuthCredentialsProviderBuilder()
        .clientId(testClient.clientId())
        .clientSecret(testClient.clientId())
        .audience("zeebe")
        .authorizationServerUrl(
            keycloak.getAuthServerUrl() + "/realms/camunda/protocol/openid-connect/token")
        .credentialsCachePath(tempDir.resolve("default").toString())
        .build();
  }
}
