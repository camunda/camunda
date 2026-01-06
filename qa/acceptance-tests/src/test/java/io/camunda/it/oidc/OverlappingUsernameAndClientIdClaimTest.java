/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.oidc;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.ClientDefinition;
import io.camunda.qa.util.auth.TestClient;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.entity.AuthenticationMethod;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * This class tests that the setting camunda.security.authentication.oidc.preferUsernameClaim
 * property works correctly when both client id and username claim are present in an access token.
 *
 * <p>Note that it uses a CamundaClient to make the API request under test. While in realworld
 * scenarios these are used by _clients_, the test's setup is so that the request will be treated as
 * a user in the Orchestration Cluster. This is out of convenice as we lack the ability to perform
 * the OIDC authorization code flow in our tests and therefore cannot authenticate like a user would
 * in real-life.
 *
 * <p>Since the matching of user/client is decouple from the concept of user/client in OIDC, that's
 * okay (the OC can match any token, regardless of originating authentication flow, to be either a
 * user or a client.
 */
@MultiDbTest(setupKeycloak = true)
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class OverlappingUsernameAndClientIdClaimTest {

  static final String PRINCIPAL_NAME = "principal";

  @MultiDbTestApplication
  static final TestCamundaApplication STANDALONE_CAMUNDA =
      new TestCamundaApplication()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthorizationsEnabled()
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setClientIdClaim("client_id"))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setUsernameClaim("client_id"))
          .withSecurityConfig(c -> c.getAuthentication().getOidc().setPreferUsernameClaim(true))
          .withSecurityConfig(c -> c.getInitialization().getUsers().clear())
          .withSecurityConfig(
              c ->
                  c.getInitialization()
                      .getDefaultRoles()
                      .put(
                          "admin", Map.of("users", List.of("admin"), "clients", List.of("admin"))));

  // Injected by the MultiDbTest extension
  private static KeycloakContainer keycloak;

  private static long processInstanceKey;

  @ClientDefinition
  private static final TestClient ADMIN_CLIENT = new TestClient("admin", List.of());

  @ClientDefinition
  private static final TestClient NON_PRIVILEGED_CLIENT = new TestClient(PRINCIPAL_NAME, List.of());

  @BeforeAll
  public static void beforeAll(@TempDir final Path tempDir) throws Exception {
    try (CamundaClient adminClient = createCamundaClient(ADMIN_CLIENT.clientId(), tempDir)) {

      TestHelper.deployResource(adminClient, "process/service_tasks_v1.bpmn");

      processInstanceKey =
          TestHelper.startProcessInstance(adminClient, "service_tasks_v1").getProcessInstanceKey();
      TestHelper.waitForProcessInstances(
          adminClient, q -> q.processInstanceKey(processInstanceKey), 1);

      adminClient
          .newCreateAuthorizationCommand()
          .ownerId(PRINCIPAL_NAME)
          .ownerType(
              OwnerType
                  .USER) // important: we are creating the authorization for a user, not a client
          // so that the test will only succeed if the CamundaClient authenticates
          // as a user
          .resourceId("*")
          .resourceType(ResourceType.PROCESS_DEFINITION)
          .permissionTypes(PermissionType.READ_PROCESS_INSTANCE)
          .send()
          .join();

      Awaitility.await("should wait until authorization is available")
          .atMost(TIMEOUT_DATA_AVAILABILITY)
          .ignoreExceptions() // Ignore exceptions and continue retrying
          .untilAsserted(
              () ->
                  assertThat(
                          adminClient
                              .newAuthorizationSearchRequest()
                              .filter(f -> f.ownerId(PRINCIPAL_NAME))
                              .send()
                              .join()
                              .items())
                      .hasSize(1));
    }
  }

  @Test
  public void shouldTreatOverlappingRequestAsUser(@TempDir final Path tempDir) throws Exception {
    // given
    // an authorization for the user (!) principal (see test setup)
    try (CamundaClient client = createCamundaClient(PRINCIPAL_NAME, tempDir)) {

      // when
      final List<ProcessInstance> processInstances =
          client.newProcessInstanceSearchRequest().send().join().items();

      // then
      // the process instance is returned successfully, because
      // the request was treated as a user request, not a client request
      assertThat(processInstances).hasSize(1);
    }
  }

  private static CamundaClient createCamundaClient(
      final String oAuthClientName, final Path tempDir) {
    return STANDALONE_CAMUNDA
        .newClientBuilder()
        .preferRestOverGrpc(true)
        .credentialsProvider(buildOAuthCredentialsProvider(oAuthClientName, tempDir))
        .build();
  }

  private static CredentialsProvider buildOAuthCredentialsProvider(
      final String oAuthClientName, final Path tempDir) {
    return new OAuthCredentialsProviderBuilder()
        .clientId(oAuthClientName)
        .clientSecret(oAuthClientName) // assumption that they are the same
        .audience("zeebe")
        .authorizationServerUrl(
            keycloak.getAuthServerUrl() + "/realms/camunda/protocol/openid-connect/token")
        .credentialsCachePath(tempDir.resolve("default").toString())
        .build();
  }
}
