/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression test for camunda/camunda#65633 on a cluster without physical tenants, configured as
 * the multi-IdP docs describe: the default slot holds only the claims, and two named providers on
 * separate Keycloak realms are the identity providers. 8.9 used these global claims for every
 * token.
 *
 * <ul>
 *   <li>{@code alpha} sets no claims, so all its claims come from the default slot.
 *   <li>{@code beta} sets only its own {@code username-claim}, so its client-id claim still comes
 *       from the default slot.
 * </ul>
 *
 * <p>An M2M client is admin only by its client id. If a provider did not use the global {@code
 * client-id-claim}, its token would resolve to a user named after {@code sub}, and the deployment
 * would be rejected.
 */
@Testcontainers
@ZeebeIntegration
final class OidcNamedProvidersGlobalClaimsIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String REALM_ALPHA = "realm-global-alpha";
  private static final String REALM_BETA = "realm-global-beta";
  private static final String CLIENT_ID_CLAIM = "app_client";
  private static final String BETA_USERNAME_CLAIM = "beta_user";
  private static final String ALPHA_M2M = "alpha-app";
  private static final String BETA_M2M = "beta-app";
  private static final String USERNAME = "login-user";
  private static final String PASSWORD = "login-password";

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC)
          .withAuthorizationsEnabled();

  private static Path tempDir;

  @BeforeAll
  static void startBroker(@TempDir final Path dir) {
    tempDir = dir;
    createRealm(REALM_ALPHA, "alpha", ALPHA_M2M, List.of());
    createRealm(
        REALM_BETA, "beta", BETA_M2M, List.of(userClaim(BETA_USERNAME_CLAIM, "beta-login-user")));

    BROKER.withSecondaryStorageType(SecondaryStorageType.rdbms);
    BROKER.withDataConfig(
        data -> {
          final var rdbms = data.getSecondaryStorage().getRdbms();
          rdbms.setUrl(
              "jdbc:h2:mem:global-claims-"
                  + UUID.randomUUID()
                  + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
          rdbms.setUsername("sa");
          rdbms.setPassword("");
        });
    // the global claims, as the multi-IdP docs say; the default slot is not a provider itself
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication().getOidc().setUsernameClaim("preferred_username");
          c.getAuthentication().getOidc().setClientIdClaim(CLIENT_ID_CLAIM);
          c.getInitialization().setMappingRules(List.of());
          c.getInitialization()
              .setDefaultRoles(
                  Map.of(
                      "admin",
                      Map.of("users", List.of(), "clients", List.of(ALPHA_M2M, BETA_M2M))));
        });
    provider("alpha", REALM_ALPHA);
    provider("beta", REALM_BETA);
    BROKER.withProperty(
        "camunda.security.authentication.providers.oidc.beta.username-claim", BETA_USERNAME_CLAIM);

    BROKER.start();

    Awaitility.await("the login is served")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .until(() -> OidcBrowserLogin.isLoginServed(BROKER.restAddress(), "", "beta"));
  }

  @ParameterizedTest(name = "over REST = {0}")
  @ValueSource(booleans = {true, false})
  void shouldResolveTheM2mClientOfAProviderWithoutClaimsWithTheGlobalClaim(final boolean rest) {
    try (final var client = m2mClient(REALM_ALPHA, ALPHA_M2M, rest)) {
      assertDeploys(client, "alpha_" + rest);
    }
  }

  @ParameterizedTest(name = "over REST = {0}")
  @ValueSource(booleans = {true, false})
  void shouldResolveTheM2mClientWithTheGlobalClaimWhenTheProviderSetsOnlyItsUsername(
      final boolean rest) {
    try (final var client = m2mClient(REALM_BETA, BETA_M2M, rest)) {
      assertDeploys(client, "beta_" + rest);
    }
  }

  @Test
  void shouldLogInWithTheGlobalUsernameClaimOnAProviderWithoutClaims() throws Exception {
    // when
    final var username =
        OidcBrowserLogin.loginAndReadUsername(
            BROKER.restAddress(), "", "alpha", USERNAME, PASSWORD);

    // then
    assertThat(username).isEqualTo(USERNAME);
  }

  @Test
  void shouldLogInWithTheUsernameClaimThatTheProviderSets() throws Exception {
    // when
    final var username =
        OidcBrowserLogin.loginAndReadUsername(BROKER.restAddress(), "", "beta", USERNAME, PASSWORD);

    // then
    assertThat(username).isEqualTo("beta-login-user");
  }

  private static void assertDeploys(final CamundaClient client, final String processId) {
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    assertThatNoException()
        .as("the M2M client is admin by its client id, so it may deploy")
        .isThrownBy(
            () ->
                Awaitility.await("deployment of " + processId)
                    .atMost(Duration.ofSeconds(60))
                    .ignoreExceptionsMatching(e -> !e.getMessage().contains("Unauthorized"))
                    .until(
                        () ->
                            client
                                .newDeployResourceCommand()
                                .addProcessModel(process, processId + ".bpmn")
                                .send()
                                .join(),
                        result -> !result.getProcesses().isEmpty()));
  }

  private static void provider(final String id, final String realm) {
    final var prefix = "camunda.security.authentication.providers.oidc." + id + ".";
    BROKER
        .withProperty(prefix + "issuer-uri", KEYCLOAK.getAuthServerUrl() + "/realms/" + realm)
        .withProperty(prefix + "client-id", id + "-login")
        .withProperty(prefix + "client-secret", id + "-login-secret")
        .withProperty(prefix + "redirect-uri", "{baseUrl}/sso-callback");
  }

  private static CamundaClient m2mClient(
      final String realm, final String clientId, final boolean rest) {
    return BROKER
        .newClientBuilder()
        .preferRestOverGrpc(rest)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(clientId + "-secret")
                .audience("zeebe")
                .authorizationServerUrl(
                    KEYCLOAK.getAuthServerUrl()
                        + "/realms/"
                        + realm
                        + "/protocol/openid-connect/token")
                .credentialsCachePath(tempDir.resolve(clientId + "-" + rest).toString())
                .build())
        .build();
  }

  /**
   * A realm with a login client for the browser flow, a service client that carries its client id
   * in {@link #CLIENT_ID_CLAIM}, and one user.
   */
  private static void createRealm(
      final String realmName,
      final String providerId,
      final String m2mClientId,
      final List<ProtocolMapperRepresentation> loginMappers) {
    final var login = new ClientRepresentation();
    login.setClientId(providerId + "-login");
    login.setEnabled(true);
    login.setClientAuthenticatorType("client-secret");
    login.setSecret(providerId + "-login-secret");
    login.setStandardFlowEnabled(true);
    login.setRedirectUris(List.of("*"));
    login.setProtocolMappers(loginMappers);

    final var m2m = new ClientRepresentation();
    m2m.setClientId(m2mClientId);
    m2m.setEnabled(true);
    m2m.setClientAuthenticatorType("client-secret");
    m2m.setSecret(m2mClientId + "-secret");
    m2m.setServiceAccountsEnabled(true);
    m2m.setProtocolMappers(List.of(hardcodedClaim(CLIENT_ID_CLAIM, m2mClientId)));

    final var password = new CredentialRepresentation();
    password.setType(CredentialRepresentation.PASSWORD);
    password.setValue(PASSWORD);
    password.setTemporary(false);
    final var user = new UserRepresentation();
    user.setUsername(USERNAME);
    user.setEnabled(true);
    user.setEmail(USERNAME + "@example.com");
    user.setEmailVerified(true);
    user.setFirstName("Login");
    user.setLastName("User");
    user.setCredentials(List.of(password));

    final var realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    realm.setClients(List.of(login, m2m));
    realm.setUsers(List.of(user));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ProtocolMapperRepresentation userClaim(final String name, final String value) {
    final var mapper = hardcodedClaim(name, value);
    mapper.setConfig(
        Map.of(
            "claim.name", name,
            "claim.value", value,
            "jsonType.label", "String",
            "access.token.claim", "true",
            "id.token.claim", "true",
            "userinfo.token.claim", "true"));
    return mapper;
  }

  private static ProtocolMapperRepresentation hardcodedClaim(
      final String name, final String value) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(name + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name",
            name,
            "claim.value",
            value,
            "jsonType.label",
            "String",
            "access.token.claim",
            "true"));
    return mapper;
  }
}
