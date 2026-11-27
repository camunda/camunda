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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.configuration.data.SecondaryStorage.SecondaryStorageType;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * All SaaS clusters share one issuer and one audience. Only the organization and cluster claims
 * bind a token to its cluster, so the gRPC and the REST API must both check them.
 */
@Testcontainers
@ZeebeIntegration
final class SaasOidcClaimBindingIT {

  @Container
  static final KeycloakContainer KEYCLOAK = DefaultTestContainers.createDefaultKeycloak();

  private static final String REALM = "saas-" + UUID.randomUUID();
  private static final String OWN_ORGANIZATION_ID = UUID.randomUUID().toString();
  private static final String OWN_CLUSTER_ID = UUID.randomUUID().toString();
  private static final String OWN_CLIENT = "own-client";
  private static final String FOREIGN_CLUSTER_CLIENT = "foreign-cluster-client";
  private static final String FOREIGN_ORGANIZATION_CLIENT = "foreign-organization-client";
  // Keycloak nests a claim at each unescaped dot of the claim name.
  private static final String CLUSTER_CLAIM = "https://camunda\\.com/clusterId";
  private static final String ORGANIZATIONS_CLAIM = "https://camunda\\.com/orgs";

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC);

  // Checks tokens without a broker, so it needs no cluster.
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneGateway GATEWAY =
      new TestStandaloneGateway()
          .withAuthenticatedAccess()
          .withAuthenticationMethod(AuthenticationMethod.OIDC);

  @TempDir private static Path credentialsCacheDir;

  @BeforeAll
  static void start() {
    configureRealm();
    BROKER.withSecurityConfig(
        c -> {
          c.getAuthentication()
              .getOidc()
              .setIssuerUri(KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM);
          c.getAuthentication().getOidc().setClientId(OWN_CLIENT);
          c.getAuthentication().getOidc().setRedirectUri("{baseUrl}/sso-callback");
        });
    BROKER
        .withProperty("camunda.security.saas.organization-id", OWN_ORGANIZATION_ID)
        .withProperty("camunda.security.saas.cluster-id", OWN_CLUSTER_ID);
    BROKER.start();

    GATEWAY.withUnifiedConfig(
        c -> {
          c.getData().getSecondaryStorage().setType(SecondaryStorageType.none);
          final var oidc = c.getSecurity().getAuthentication().getOidc();
          oidc.setIssuerUri(KEYCLOAK.getAuthServerUrl() + "/realms/" + REALM);
          oidc.setClientId(OWN_CLIENT);
          oidc.setRedirectUri("{baseUrl}/sso-callback");
        });
    GATEWAY
        .withProperty("camunda.security.saas.organization-id", OWN_ORGANIZATION_ID)
        .withProperty("camunda.security.saas.cluster-id", OWN_CLUSTER_ID);
    GATEWAY.start();

    for (final var api : Api.values()) {
      try (final var client = client(OWN_CLIENT, api)) {
        Awaitility.await(api + " ready")
            .atMost(Duration.ofSeconds(60))
            .ignoreExceptions()
            .untilAsserted(() -> client.newTopologyRequest().send().join());
      }
    }
  }

  @ParameterizedTest
  @EnumSource(Api.class)
  void shouldAcceptTokenOfOwnOrganizationAndCluster(final Api api) {
    // given
    try (final var client = client(OWN_CLIENT, api)) {
      // when / then
      assertThatNoException().isThrownBy(() -> client.newTopologyRequest().send().join());
    }
  }

  @ParameterizedTest(name = "{0} over {1}")
  @CsvSource({
    FOREIGN_CLUSTER_CLIENT + ",REST",
    FOREIGN_CLUSTER_CLIENT + ",EMBEDDED_GATEWAY_GRPC",
    FOREIGN_CLUSTER_CLIENT + ",STANDALONE_GATEWAY_GRPC",
    FOREIGN_ORGANIZATION_CLIENT + ",REST",
    FOREIGN_ORGANIZATION_CLIENT + ",EMBEDDED_GATEWAY_GRPC",
    FOREIGN_ORGANIZATION_CLIENT + ",STANDALONE_GATEWAY_GRPC"
  })
  void shouldRejectTokenOfAnotherOrganizationOrCluster(final String clientId, final Api api) {
    // given
    try (final var client = client(clientId, api)) {
      // when / then
      if (api == Api.REST) {
        assertThatThrownBy(() -> client.newTopologyRequest().send().join())
            .isInstanceOfSatisfying(
                ClientHttpException.class, e -> assertThat(e.code()).isEqualTo(401));
      } else {
        assertThatThrownBy(() -> client.newTopologyRequest().send().join())
            .hasRootCauseInstanceOf(StatusRuntimeException.class)
            .rootCause()
            .satisfies(
                e ->
                    assertThat(((StatusRuntimeException) e).getStatus().getCode())
                        .isEqualTo(Status.Code.UNAUTHENTICATED));
      }
    }
  }

  private static void configureRealm() {
    final var realm = new RealmRepresentation();
    realm.setRealm(REALM);
    realm.setEnabled(true);
    realm.setClients(
        List.of(
            m2mClient(OWN_CLIENT, OWN_ORGANIZATION_ID, OWN_CLUSTER_ID),
            m2mClient(FOREIGN_CLUSTER_CLIENT, OWN_ORGANIZATION_ID, UUID.randomUUID().toString()),
            m2mClient(FOREIGN_ORGANIZATION_CLIENT, UUID.randomUUID().toString(), OWN_CLUSTER_ID)));
    try (final var admin = KEYCLOAK.getKeycloakAdminClient()) {
      admin.realms().create(realm);
    }
  }

  private static ClientRepresentation m2mClient(
      final String clientId, final String organizationId, final String clusterId) {
    final var client = new ClientRepresentation();
    client.setClientId(clientId);
    client.setEnabled(true);
    client.setClientAuthenticatorType("client-secret");
    client.setSecret(clientId + "-secret");
    client.setServiceAccountsEnabled(true);
    client.setProtocolMappers(
        List.of(
            hardcodedClaim(CLUSTER_CLAIM, clusterId, "String"),
            hardcodedClaim(
                ORGANIZATIONS_CLAIM, "[{\"id\":\"%s\"}]".formatted(organizationId), "JSON")));
    return client;
  }

  private static ProtocolMapperRepresentation hardcodedClaim(
      final String name, final String value, final String jsonType) {
    final var mapper = new ProtocolMapperRepresentation();
    mapper.setName(name);
    mapper.setProtocol("openid-connect");
    mapper.setProtocolMapper("oidc-hardcoded-claim-mapper");
    mapper.setConfig(
        Map.of(
            "claim.name",
            name,
            "claim.value",
            value,
            "jsonType.label",
            jsonType,
            "access.token.claim",
            "true"));
    return mapper;
  }

  private static CamundaClient client(final String clientId, final Api api) {
    final TestGateway<?> gateway = api == Api.STANDALONE_GATEWAY_GRPC ? GATEWAY : BROKER;
    return gateway
        .newClientBuilder()
        .preferRestOverGrpc(api == Api.REST)
        // Environment variables must not change the target tenant or the client of the token.
        .applyEnvironmentVariableOverrides(false)
        .credentialsProvider(
            new OAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .clientId(clientId)
                .clientSecret(clientId + "-secret")
                .audience("zeebe")
                .authorizationServerUrl(
                    KEYCLOAK.getAuthServerUrl()
                        + "/realms/"
                        + REALM
                        + "/protocol/openid-connect/token")
                .credentialsCachePath(credentialsCacheDir.resolve(clientId + "-" + api).toString())
                .build())
        .build();
  }

  enum Api {
    REST,
    EMBEDDED_GATEWAY_GRPC,
    STANDALONE_GATEWAY_GRPC
  }
}
