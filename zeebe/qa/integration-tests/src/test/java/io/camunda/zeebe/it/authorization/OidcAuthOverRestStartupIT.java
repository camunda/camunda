/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpForward.Scheme;
import org.slf4j.event.Level;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ZeebeIntegration
public class OidcAuthOverRestStartupIT {

  private static final String DEFAULT_USER_ID = UUID.randomUUID().toString();
  private static final String KEYCLOAK_REALM = "camunda";
  private static final String DEFAULT_CLIENT_ID = "zeebe";
  private static final String DEFAULT_CLIENT_SECRET = "secret";
  private static final String USER_ID_CLAIM_NAME = "sub";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @Container
  private static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  @AutoClose private static CamundaClient client;

  private final ClientAndServer keycloakProxy =
      ClientAndServer.startClientAndServer(new Configuration().logLevel(Level.ERROR));

  @TestZeebe(awaitCompleteTopology = false)
  private final TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withCamundaExporter("http://" + CONTAINER.getHttpHostAddress())
          .withSecurityConfig(
              c -> {
                c.getAuthorizations().setEnabled(true);

                final var oidcConfig = c.getAuthentication().getOidc();
                String issuerUri =
                    "http://localhost:"
                        + keycloakProxy.getLocalPort()
                        + "/realms/"
                        + KEYCLOAK_REALM;
                oidcConfig.setIssuerUri(issuerUri);
                // The following two properties are only needed for the webapp login flow which we
                // don't test here.
                oidcConfig.setClientId("example");
                oidcConfig.setRedirectUri("example.com");
                c.getInitialization()
                    .setMappingRules(
                        List.of(
                            new ConfiguredMappingRule(
                                DEFAULT_USER_ID, USER_ID_CLAIM_NAME, DEFAULT_USER_ID)));
                c.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER_ID)));
              });

  @BeforeAll
  static void setupKeycloak() {
    final var defaultClient = new ClientRepresentation();
    defaultClient.setClientId(DEFAULT_CLIENT_ID);
    defaultClient.setEnabled(true);
    defaultClient.setClientAuthenticatorType("client-secret");
    defaultClient.setSecret(DEFAULT_CLIENT_SECRET);
    defaultClient.setServiceAccountsEnabled(true);

    final var defaultUser = new UserRepresentation();
    defaultUser.setId(DEFAULT_USER_ID);
    defaultUser.setUsername("zeebe-service-account");
    defaultUser.setServiceAccountClientId(DEFAULT_CLIENT_ID);
    defaultUser.setEnabled(true);

    final var realm = new RealmRepresentation();
    realm.setRealm("camunda");
    realm.setEnabled(true);
    realm.setClients(List.of(defaultClient));
    realm.setUsers(List.of(defaultUser));

    try (final var keycloak = KEYCLOAK.getKeycloakAdminClient()) {
      keycloak.realms().create(realm);
    }
  }

  @BeforeEach
  void beforeEach(@TempDir final Path tempDir) {

    client =
        CamundaClient.newClientBuilder()
            .grpcAddress(broker.grpcAddress())
            .restAddress(broker.restAddress())
            .usePlaintext()
            .preferRestOverGrpc(true)
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .credentialsProvider(
                new OAuthCredentialsProviderBuilder()
                    .clientId(DEFAULT_CLIENT_ID)
                    .clientSecret(DEFAULT_CLIENT_SECRET)
                    .audience("zeebe")
                    .authorizationServerUrl(
                        KEYCLOAK.getAuthServerUrl()
                            + "/realms/"
                            + KEYCLOAK_REALM
                            + "/protocol/openid-connect/token")
                    .credentialsCachePath(tempDir.resolve("default").toString())
                    .build())
            .build();
  }

  @Test
  void shouldStartWhenKeycloakIsNotAvailableOnStartup() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when/then
    // keycloak is not available, so we expect the error
    assertThatThrownBy(
            () ->
                client
                    .newDeployResourceCommand()
                    .addProcessModel(
                        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                        "process.bpmn")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining(
            "Authentication service unavailable: Unable to connect to the configured Identity Provider (OIDC). "
                + "Please try again later or contact your administrator.");

    // when
    // point proxy to keycloak
    keycloakProxy
        .when(request())
        .forward(
            HttpForward.forward()
                .withScheme(Scheme.HTTP)
                .withHost(KEYCLOAK.getHost())
                .withPort(KEYCLOAK.getHttpPort()));

    Awaitility.await()
        .pollDelay(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var deploymentEvent =
                  client
                      .newDeployResourceCommand()
                      .addProcessModel(
                          Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
                          "process.bpmn")
                      .send()
                      .join();

              // then
              assertThat(deploymentEvent.getProcesses().getFirst().getBpmnProcessId())
                  .isEqualTo(processId);
            });
  }
}
