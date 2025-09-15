/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.operate;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestRestOperateClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.util.Either;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

/**
 * Covers cases in which a client (i.e. matches) authenticates against the Operate V1 API and
 * internal API (as one class, to save time on Keycloak setup).
 */
@MultiDbTest(setupKeycloak = true)
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OperateApiOidcClientIT {

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
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  List.of("*"))));

  @ClientDefinition
  private static final TestClient PRIVILEGED_CLIENT =
      new TestClient(
          "fooClient",
          List.of(
              new Permissions(
                  ResourceType.PROCESS_DEFINITION,
                  PermissionType.READ_PROCESS_INSTANCE,
                  List.of("*"))));

  @ClientDefinition
  private static final TestClient UNPRIVILEGED_CLIENT = new TestClient("barClient", List.of());

  @BeforeAll
  public static void beforeAll(@TempDir final Path tempDir) throws Exception {
    try (CamundaClient adminClient = createCamundaClient(ADMIN, tempDir)) {

      TestHelper.deployResource(adminClient, "process/service_tasks_v1.bpmn");

      processInstanceKey =
          TestHelper.startProcessInstance(adminClient, "service_tasks_v1").getProcessInstanceKey();
      TestHelper.waitForProcessInstances(
          adminClient, q -> q.processInstanceKey(processInstanceKey), 1);
    }
  }

  @Test
  public void shouldReturnProcessInstanceViaV1ApiToPrivilegedClient(@TempDir final Path tempDir)
      throws Exception {
    // given
    try (TestRestOperateClient operateClient =
        STANDALONE_CAMUNDA.newOperateClient(
            buildOAuthCredentialsProvider(PRIVILEGED_CLIENT, tempDir))) {

      // when
      final HttpResponse<String> searchResponse =
          operateClient.sendV1SearchRequest("v1/process-instances", "{}");

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final Either<Exception, Map> result = operateClient.mapResult(searchResponse, Map.class);

      assertThat(result.isRight()).isTrue();
      final Map<String, Object> responseBody = result.get();

      assertThat(responseBody).containsEntry("total", 1);

      final List<Map<String, Object>> processInstances =
          (List<Map<String, Object>>) responseBody.get("items");

      assertThat(processInstances).hasSize(1);

      final Map<String, Object> processInstance = processInstances.get(0);
      assertThat(processInstance).containsEntry("key", processInstanceKey);
    }
  }

  @Test
  public void shouldReturnProcessInstanceViaInternalApiToPrivilegedClient(
      @TempDir final Path tempDir) throws Exception {
    // given
    try (TestRestOperateClient operateClient =
        STANDALONE_CAMUNDA.newOperateClient(
            buildOAuthCredentialsProvider(PRIVILEGED_CLIENT, tempDir))) {

      // when
      final HttpResponse<String> searchResponse =
          operateClient.sendInternalSearchRequest(
              "api/process-instances", "{\"query\": {\"active\": true, \"running\": true}}");

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final Either<Exception, Map> result = operateClient.mapResult(searchResponse, Map.class);

      assertThat(result.isRight()).isTrue();
      final Map<String, Object> responseBody = result.get();

      assertThat(responseBody).containsEntry("totalCount", 1);

      final List<Map<String, Object>> processInstances =
          (List<Map<String, Object>>) responseBody.get("processInstances");

      assertThat(processInstances).hasSize(1);

      final Map<String, Object> processInstance = processInstances.get(0);
      assertThat(processInstance).containsEntry("id", Long.toString(processInstanceKey));
    }
  }

  @Test
  public void shouldNotReturnProcessInstanceViaV1ApiToUnprivilegedClient(
      @TempDir final Path tempDir) throws Exception {
    // given
    try (TestRestOperateClient operateClient =
        STANDALONE_CAMUNDA.newOperateClient(
            buildOAuthCredentialsProvider(UNPRIVILEGED_CLIENT, tempDir))) {

      // when
      final HttpResponse<String> searchResponse =
          operateClient.sendV1SearchRequest("v1/process-instances", "{}");

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final Either<Exception, Map> result = operateClient.mapResult(searchResponse, Map.class);

      assertThat(result.isRight()).isTrue();
      final Map<String, Object> responseBody = result.get();

      assertThat(responseBody).containsEntry("total", 0);
    }
  }

  @Test
  public void shouldNotReturnProcessInstanceViaInternalApiToUnprivilegedClient(
      @TempDir final Path tempDir) throws Exception {
    // given
    try (TestRestOperateClient operateClient =
        STANDALONE_CAMUNDA.newOperateClient(
            buildOAuthCredentialsProvider(UNPRIVILEGED_CLIENT, tempDir))) {

      // when
      final HttpResponse<String> searchResponse =
          operateClient.sendInternalSearchRequest(
              "api/process-instances", "{\"query\": {\"active\": true, \"running\": true}}");

      // then
      assertThat(searchResponse.statusCode()).isEqualTo(HttpStatus.OK.value());

      final Either<Exception, Map> result = operateClient.mapResult(searchResponse, Map.class);

      assertThat(result.isRight()).isTrue();
      final Map<String, Object> responseBody = result.get();

      assertThat(responseBody).containsEntry("totalCount", 0);
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
