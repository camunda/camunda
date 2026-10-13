/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Logs in through the browser authorization code flow on two physical tenants that share the
 * registration id {@code oidc} but use separate identity providers: {@code tenanta} inherits the
 * root's realm, and {@code tenantb} overrides the issuer, client and secret with another realm. So
 * the two have a different issuer and different signing keys.
 *
 * <p>The username comes from a claim that only the access token carries. If a login decoded its
 * access token with the settings of another tenant, the decode fails and the login falls back to
 * the ID token, which does not have the claim. The decoders are kept until the application stops,
 * so the first login decides for the later ones, as it did before camunda-security-library#720.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantOidcLoginSeparateIdpsIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String REALM_A = "realm-sep-a";
  private static final String REALM_B = "realm-sep-b";
  private static final String CLIENT_A = "camunda-sep-a";
  private static final String CLIENT_B = "camunda-sep-b";
  private static final String SECRET_A = "secret-a";
  private static final String SECRET_B = "secret-b";
  private static final String USERNAME = "login-user";
  private static final String PASSWORD = "login-password";
  private static final String ACCESS_TOKEN_CLAIM = "access_token_user";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.rdbmsH2("sep-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("sep-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("sep-tenantb"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  @BeforeAll
  static void startBroker() {
    createRealm(REALM_A, CLIENT_A, SECRET_A, "alice-a");
    createRealm(REALM_B, CLIENT_B, SECRET_B, "bob-b");

    BROKER.withSecurityConfig(
        c -> {
          final var oidc = c.getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri(REALM_A));
          oidc.setClientId(CLIENT_A);
          oidc.setClientSecret(SECRET_A);
          oidc.setUsernameClaim(ACCESS_TOKEN_CLAIM);
          oidc.setRedirectUri("{baseUrl}/sso-callback");
        });
    assignDefaultSlot(TENANT_A);
    assignDefaultSlot(TENANT_B);
    BROKER.withPtConfig(
        TENANT_B,
        c -> {
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri(REALM_B));
          oidc.setClientId(CLIENT_B);
          oidc.setClientSecret(SECRET_B);
        });

    BROKER.start();

    Awaitility.await("the tenant login is served")
        .atMost(Duration.ofSeconds(120))
        .ignoreExceptions()
        .until(
            () ->
                OidcBrowserLogin.isLoginServed(
                    BROKER.restAddress(), "/physical-tenants/" + TENANT_B, "oidc"));
  }

  @Test
  void shouldDecodeTheAccessTokenOfEachTenantWithItsOwnIdentityProvider() throws Exception {
    // given the first login creates the access-token decoder for the registration id "oidc"
    final var usernameOnTenantA = loginAndReadUsername("/physical-tenants/" + TENANT_A);

    // when a tenant on another identity provider logs in with the same registration id
    final var usernameOnTenantB = loginAndReadUsername("/physical-tenants/" + TENANT_B);
    final var usernameOnTenantAAgain = loginAndReadUsername("/physical-tenants/" + TENANT_A);

    // then each tenant reads the claim from its own access token
    assertThat(usernameOnTenantA).isEqualTo("alice-a");
    assertThat(usernameOnTenantB).isEqualTo("bob-b");
    assertThat(usernameOnTenantAAgain).isEqualTo("alice-a");
  }

  private static String loginAndReadUsername(final String pathPrefix) throws Exception {
    return OidcBrowserLogin.loginAndReadUsername(
        BROKER.restAddress(), pathPrefix, "oidc", USERNAME, PASSWORD);
  }

  private static String issuerUri(final String realm) {
    return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
  }

  private static void assignDefaultSlot(final String tenantId) {
    BROKER.withProperty(
        "camunda.physical-tenants." + tenantId + ".security.authentication.providers.assigned[0]",
        "oidc");
  }

  private static void createRealm(
      final String realmName,
      final String clientId,
      final String secret,
      final String accessTokenUser) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(secret);
    client.setStandardFlowEnabled(true);
    client.setRedirectUris(List.of("*"));
    client.setProtocolMappers(List.of(accessTokenOnlyClaim(accessTokenUser)));

    final var password = new CredentialRepresentation();
    password.setType(CredentialRepresentation.PASSWORD);
    password.setValue(PASSWORD);
    password.setTemporary(false);
    final var user = new UserRepresentation();
    user.setUsername(USERNAME);
    user.setEnabled(true);
    user.setEmail("login-user@example.com");
    user.setEmailVerified(true);
    user.setFirstName("Login");
    user.setLastName("User");
    user.setCredentials(List.of(password));

    final var realm = new RealmRepresentation();
    realm.setRealm(realmName);
    realm.setEnabled(true);
    realm.setClients(List.of(client));
    realm.setUsers(List.of(user));

    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  /**
   * A claim only in the access token, not in the ID token or the UserInfo response, so that the
   * fallback to the ID token cannot provide it.
   */
  private static ProtocolMapperRepresentation accessTokenOnlyClaim(final String value) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(ACCESS_TOKEN_CLAIM + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name", ACCESS_TOKEN_CLAIM,
            "claim.value", value,
            "jsonType.label", "String",
            "access.token.claim", "true",
            "id.token.claim", "false",
            "userinfo.token.claim", "false"));
    return mapper;
  }
}
