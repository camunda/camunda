/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestGateway;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies BASIC-auth isolation across physical tenants over gRPC via a <em>standalone</em> gateway
 * (embedded gateway disabled on the broker):
 *
 * <ul>
 *   <li>Absent {@code Camunda-Physical-Tenant} header resolves to {@code default} and authenticates
 *       against the default PT's user store.
 *   <li>An unknown PT header is rejected with {@code UNAUTHENTICATED} — the PT is not in {@code
 *       knownTenantIds}, a deliberate choice not to leak tenant existence.
 *   <li>Credentials seeded in one tenant's user store are accepted on that tenant and rejected on
 *       another whose store lacks them.
 * </ul>
 *
 * @see PhysicalTenantGrpcBasicAuthIT for the embedded-gateway counterpart
 */
@ZeebeIntegration
final class PhysicalTenantGrpcBasicAuthStandaloneGatewayIT {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";
  private static final String TENANT_A_PASSWORD = "password-a";
  private static final String TENANT_B_PASSWORD = "password-b";

  private static final PhysicalTenantsITHelper TENANTS =
      builder()
          .withTenant(DEFAULT_TENANT_ID, Storage.rdbmsH2("basic-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("basic-tenanta"))
          .withTenant(TENANT_B, Storage.rdbmsH2("basic-tenantb"))
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configureAdminRoles(
          new TestStandaloneBroker()
              .withAuthenticatedAccess()
              .withBasicAuth()
              // Disable the embedded gateway
              .withGatewayEnabled(false));

  // Standalone gateway wired to the broker's cluster port (only known once the broker is started,
  // see #start); shares the same RDBMS stores.
  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneGateway GATEWAY =
      new TestStandaloneGateway().withAuthenticatedAccess().withBasicAuth();

  private static CamundaClient defaultAdmin;
  private static CamundaClient tenantAAdmin;
  private static CamundaClient tenantBAdmin;

  @BeforeAll
  static void start() {
    // Fresh H2 URLs per run; broker and gateway share the same stores so both must use the same
    // URL.
    final String defaultUrl = rdbmsH2Url("basic-default");
    final String tenantAUrl = rdbmsH2Url("basic-tenanta");
    final String tenantBUrl = rdbmsH2Url("basic-tenantb");

    Storage.rdbms(defaultUrl, "sa", "").applyTo(BROKER, DEFAULT_TENANT_ID);
    Storage.rdbms(tenantAUrl, "sa", "").applyTo(BROKER, TENANT_A);
    Storage.rdbms(tenantBUrl, "sa", "").applyTo(BROKER, TENANT_B);

    configureGateway(DEFAULT_TENANT_ID, defaultUrl);
    configureGateway(TENANT_A, tenantAUrl);
    configureGateway(TENANT_B, tenantBUrl);

    TENANTS.seedBasicAuthAdminUser(BROKER, TENANT_A, TENANT_A_PASSWORD);
    TENANTS.seedBasicAuthAdminUser(BROKER, TENANT_B, TENANT_B_PASSWORD);

    BROKER.start();
    GATEWAY.withClusterConfig(
        c -> c.setInitialContactPoints(List.of(BROKER.address(TestZeebePort.CLUSTER))));
    GATEWAY.start();

    // No physicalTenantId — the gateway must resolve the absent header to the default PT.
    defaultAdmin =
        GATEWAY
            .newClientBuilder()
            .preferRestOverGrpc(false)
            .credentialsProvider(
                new BasicAuthCredentialsProviderBuilder()
                    .applyEnvironmentOverrides(false)
                    .username(InitializationConfiguration.DEFAULT_USER_USERNAME)
                    .password(InitializationConfiguration.DEFAULT_USER_PASSWORD)
                    .build())
            .build();
    tenantAAdmin =
        TENANTS
            .newBasicAuthAdminClientBuilder(GATEWAY, TENANT_A, TENANT_A_PASSWORD)
            .preferRestOverGrpc(false)
            .build();
    tenantBAdmin =
        TENANTS
            .newBasicAuthAdminClientBuilder(GATEWAY, TENANT_B, TENANT_B_PASSWORD)
            .preferRestOverGrpc(false)
            .build();

    awaitAuth(defaultAdmin, "default-admin ready");
    awaitAuth(tenantAAdmin, "tenanta-admin ready");
    awaitAuth(tenantBAdmin, "tenantb-admin ready");
  }

  @AfterAll
  static void closeClients() {
    CloseHelper.quietCloseAll(defaultAdmin, tenantAAdmin, tenantBAdmin);
  }

  @Test
  void shouldRouteAbsentHeaderToDefaultPhysicalTenant() {
    // given — a client with no physicalTenantId sends no Camunda-Physical-Tenant header
    // when / then — topology succeeds; default user is authenticated via the default PT's store
    // (standalone gateway serves topology without the Camunda REST API)
    assertThatNoException()
        .as("default user is authenticated via the default PT's user store")
        .isThrownBy(() -> defaultAdmin.newTopologyRequest().send().join());
  }

  @Test
  void shouldAuthenticateTenantACredentialsOnlyInTenantA() {
    // given — credentials seeded only in PT-A's user store
    // when / then — accepted on PT-A
    assertThatNoException()
        .as("tenanta-admin is accepted on its home PT")
        .isThrownBy(() -> tenantAAdmin.newTopologyRequest().send().join());

    // when / then — rejected on PT-B whose store does not contain these credentials
    try (final var wrongTenant =
        basicAuthClient(GATEWAY, TENANT_B, TENANTS.adminUsername(TENANT_A), TENANT_A_PASSWORD)) {
      assertThatThrownBy(() -> wrongTenant.newTopologyRequest().send().join())
          .as("tenanta-admin exists only in PT-A's store")
          .hasRootCauseInstanceOf(StatusRuntimeException.class)
          .rootCause()
          .satisfies(
              e ->
                  assertThat(((StatusRuntimeException) e).getStatus().getCode())
                      .isEqualTo(Status.Code.UNAUTHENTICATED));
    }
  }

  @Test
  void shouldAuthenticateTenantBCredentialsOnlyInTenantB() {
    // given — credentials seeded only in PT-B's user store
    // when / then — accepted on PT-B
    assertThatNoException()
        .as("tenantb-admin is accepted on its home PT")
        .isThrownBy(() -> tenantBAdmin.newTopologyRequest().send().join());

    // when / then — rejected on PT-A whose store does not contain these credentials
    try (final var wrongTenant =
        basicAuthClient(GATEWAY, TENANT_A, TENANTS.adminUsername(TENANT_B), TENANT_B_PASSWORD)) {
      assertThatThrownBy(() -> wrongTenant.newTopologyRequest().send().join())
          .as("tenantb-admin exists only in PT-B's store")
          .hasRootCauseInstanceOf(StatusRuntimeException.class)
          .rootCause()
          .satisfies(
              e ->
                  assertThat(((StatusRuntimeException) e).getStatus().getCode())
                      .isEqualTo(Status.Code.UNAUTHENTICATED));
    }
  }

  @Test
  void shouldRejectUnknownPhysicalTenantHeader() {
    // given — valid default-PT credentials sent with a Camunda-Physical-Tenant header for a PT
    // that is not registered; credentials are valid to rule out a missing-auth false positive
    try (final var client =
        GATEWAY
            .newClientBuilder()
            .preferRestOverGrpc(false)
            .physicalTenantId("unknownpt")
            .credentialsProvider(
                new BasicAuthCredentialsProviderBuilder()
                    .applyEnvironmentOverrides(false)
                    .username(InitializationConfiguration.DEFAULT_USER_USERNAME)
                    .password(InitializationConfiguration.DEFAULT_USER_PASSWORD)
                    .build())
            .build()) {
      // when / then — the gateway rejects because "unknownpt" is not in knownTenantIds,
      // not because credentials are missing
      assertThatThrownBy(() -> client.newTopologyRequest().send().join())
          .as("unknown PT header is rejected — PT is not in knownTenantIds")
          .hasRootCauseInstanceOf(StatusRuntimeException.class)
          .rootCause()
          .satisfies(
              e ->
                  assertThat(((StatusRuntimeException) e).getStatus().getCode())
                      .isEqualTo(Status.Code.UNAUTHENTICATED));
    }
  }

  private static void configureGateway(final String tenantId, final String url) {
    final boolean isDefault = DEFAULT_TENANT_ID.equals(tenantId);
    if (isDefault) {
      GATEWAY.withUnifiedConfig(
          c -> {
            c.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
            c.getData().getSecondaryStorage().getRdbms().setUrl(url);
            c.getData().getSecondaryStorage().getRdbms().setUsername("sa");
            c.getData().getSecondaryStorage().getRdbms().setPassword("");
          });
    } else {
      GATEWAY.withPtConfig(
          tenantId,
          c -> {
            c.getData().getSecondaryStorage().setType(SecondaryStorageType.rdbms);
            c.getData().getSecondaryStorage().getRdbms().setUrl(url);
            c.getData().getSecondaryStorage().getRdbms().setUsername("sa");
            c.getData().getSecondaryStorage().getRdbms().setPassword("");
          });
      // PhysicalTenantRequiredOverrideValidation requires a security.initialization.* key for
      // every explicitly-configured non-default PT. The gateway never runs initialization — the
      // key's presence is all the validator checks.
      GATEWAY.withPtConfig(
          tenantId,
          c ->
              c.getSecurity()
                  .getInitialization()
                  .setDefaultRoles(
                      Map.of("admin", Map.of("users", List.of(TENANTS.adminUsername(tenantId))))));
    }
  }

  /**
   * Builds a gRPC-first basic-auth client targeting {@code targetTenantId} with the given
   * credentials. The username is passed explicitly so callers can use credentials from a different
   * PT to prove cross-PT rejection in isolation.
   */
  private static CamundaClient basicAuthClient(
      final TestGateway<?> gateway,
      final String targetTenantId,
      final String username,
      final String password) {
    return TENANTS
        .newClientBuilder(gateway, targetTenantId)
        .preferRestOverGrpc(false)
        .credentialsProvider(
            new BasicAuthCredentialsProviderBuilder()
                .applyEnvironmentOverrides(false)
                .username(username)
                .password(password)
                .build())
        .build();
  }

  private static void awaitAuth(final CamundaClient client, final String reason) {
    Awaitility.await(reason)
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(() -> client.newTopologyRequest().send().join());
  }

  private static String rdbmsH2Url(final String prefix) {
    return "jdbc:h2:mem:" + prefix + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL";
  }
}
