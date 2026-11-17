/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.camunda.zeebe.test.util.Strings;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that gRPC builds the broker request claims with the security config of the targeted
 * physical tenant. The root has no groups claim and authorizations off, while the tenant reads
 * groups from the token and has authorizations on. Only a token group grants the tenant's {@code
 * admin} role, so a command succeeds only when the tenant's settings decide which claims reach the
 * broker.
 */
@Testcontainers
@ZeebeIntegration
final class PhysicalTenantGrpcOidcBrokerClaimsIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String TENANT = "tenanta";
  private static final String REALM = "realm-broker-claims";
  private static final String GROUPS_CLAIM = "groups";
  private static final String ADMIN_GROUP = "ops";
  private static final String MEMBER_CLIENT_ID = "ops-client";
  private static final String MEMBER_CLIENT_SECRET = "ops-secret";
  private static final String OTHER_CLIENT_ID = "other-client";
  private static final String OTHER_CLIENT_SECRET = "other-secret";
  private static final String AUDIENCE = "zeebe";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withAuthenticationMethod(AuthenticationMethod.OIDC));

  private static CamundaClient memberClient;
  private static CamundaClient otherClient;

  @BeforeAll
  static void startBroker(@TempDir final Path tempDir) {
    configureRealm();

    final String issuerUri = KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM;
    // the root reads no groups from the token and has authorizations off, so its settings would
    // send neither groups nor token claims to the broker
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthorizations().setEnabled(false);
          c.getAuthentication().getOidc().setIssuerUri(issuerUri);
          c.getAuthentication().getOidc().setClientId(MEMBER_CLIENT_ID);
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
        });
    BROKER.withPtConfig(
        TENANT,
        c -> {
          c.getSecurity().getAuthorizations().setEnabled(true);
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(issuerUri);
          oidc.setClientId(MEMBER_CLIENT_ID);
          oidc.setRedirectUri("{baseUrl}/login/oauth2/code/oidc");
          oidc.setGroupsClaim(GROUPS_CLAIM);
          c.getSecurity()
              .getInitialization()
              .setDefaultRoles(
                  Map.of(
                      "admin",
                      Map.of(
                          "users",
                          List.of(TENANTS.adminUsername(TENANT)),
                          "groups",
                          List.of(ADMIN_GROUP))));
        });
    BROKER.withProperty(
        "camunda.physical-tenants." + TENANT + ".security.authentication.providers.assigned[0]",
        "oidc");

    BROKER.start();

    memberClient = oidcClient(MEMBER_CLIENT_ID, MEMBER_CLIENT_SECRET, tempDir);
    otherClient = oidcClient(OTHER_CLIENT_ID, OTHER_CLIENT_SECRET, tempDir);
  }

  @AfterAll
  static void closeClients() {
    CloseHelper.quietCloseAll(memberClient, otherClient);
  }

  @Test
  void shouldAuthorizeCommandWithTenantGroupsFromToken() {
    // when
    // the role membership is created when the tenant's partition starts, so wait for it
    Awaitility.await("deploy with a token group that has the admin role")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(() -> deploy(memberClient));

    // then
    // a group without a role is still denied, so the success above comes from the token group
    assertThatThrownBy(() -> deploy(otherClient))
        .hasRootCauseInstanceOf(StatusRuntimeException.class)
        .rootCause()
        .satisfies(
            e ->
                assertThat(((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED));
  }

  private static void deploy(final CamundaClient client) {
    final String processId = Strings.newRandomValidBpmnId();
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent().done(),
            processId + ".bpmn")
        .send()
        .join();
  }

  private static void configureRealm() {
    final var realmRepresentation = new RealmRepresentation();
    realmRepresentation.setRealm(REALM);
    realmRepresentation.setEnabled(true);
    realmRepresentation.setClients(
        List.of(
            clientWithGroup(MEMBER_CLIENT_ID, MEMBER_CLIENT_SECRET, ADMIN_GROUP),
            clientWithGroup(OTHER_CLIENT_ID, OTHER_CLIENT_SECRET, "other")));

    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realmRepresentation);
    }
  }

  private static ClientRepresentation clientWithGroup(
      final String clientId, final String clientSecret, final String group) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(GROUPS_CLAIM + "-mapper");
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name", GROUPS_CLAIM,
            "claim.value", "[\"" + group + "\"]",
            "jsonType.label", "JSON",
            "access.token.claim", "true",
            "id.token.claim", "true"));

    final var clientRepresentation = new ClientRepresentation();
    clientRepresentation.setClientId(clientId);
    clientRepresentation.setEnabled(true);
    clientRepresentation.setClientAuthenticatorType("client-secret");
    clientRepresentation.setSecret(clientSecret);
    clientRepresentation.setServiceAccountsEnabled(true);
    clientRepresentation.setProtocolMappers(List.of(mapper));
    return clientRepresentation;
  }

  private static CamundaClient oidcClient(
      final String clientId, final String clientSecret, final Path credentialsCacheDir) {
    return TENANTS
        .newClientBuilder(BROKER, TENANT)
        .preferRestOverGrpc(false)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .audience(AUDIENCE)
                .authorizationServerUrl(
                    KEYCLOAK.getAuthServerUrl()
                        + "/realms/"
                        + REALM
                        + "/protocol/openid-connect/token")
                .credentialsCachePath(credentialsCacheDir.resolve(clientId).toString())
                .build())
        .build();
  }
}
