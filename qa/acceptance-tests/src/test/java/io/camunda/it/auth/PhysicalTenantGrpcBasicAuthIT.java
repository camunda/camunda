/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.PT_ADMIN_PASSWORD;
import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.physicalTenantAdminUsername;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.basicauth.BasicAuthCredentialsProviderBuilder;
import io.camunda.qa.util.multidb.MultiDbPhysicalTenants;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Verifies BASIC-auth isolation across physical tenants over gRPC.
 *
 * <ul>
 *   <li>Absent {@code Camunda-Physical-Tenant} header resolves to {@code default}; querying the
 *       default PT's user store confirms correct resolution.
 *   <li>An unknown PT header is rejected with {@code UNAUTHENTICATED} — the PT is not in {@code
 *       knownTenantIds}, a deliberate choice not to leak tenant existence.
 *   <li>Credentials seeded in one tenant's user store are accepted on that tenant and rejected on
 *       another whose store lacks them.
 * </ul>
 */
@MultiDbTest
@MultiDbPhysicalTenants({"tenanta", "tenantb"})
@EnabledIfSystemProperty(
    named = "test.integration.camunda.database.type",
    matches = "rdbms.*$",
    disabledReason = "Physical-tenant secondary storage is RDBMS-only")
final class PhysicalTenantGrpcBasicAuthIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withAuthenticationMethod(AuthenticationMethod.BASIC);

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldRouteAbsentHeaderToDefaultPhysicalTenant() {
    // given — a client with no physicalTenantId sends no Camunda-Physical-Tenant header
    try (final var defaultAdmin =
        defaultGrpcClient(
            InitializationConfiguration.DEFAULT_USER_USERNAME,
            InitializationConfiguration.DEFAULT_USER_PASSWORD)) {
      // when
      final var users =
          defaultAdmin
              .newUsersSearchRequest()
              .filter(f -> f.username(InitializationConfiguration.DEFAULT_USER_USERNAME))
              .send()
              .join()
              .items();
      // then — the gateway resolved to 'default'; the default user is found in that PT's store
      assertThat(users)
          .as("default user found in the default PT's user store — confirms correct PT resolution")
          .hasSize(1)
          .first()
          .extracting(u -> u.getUsername())
          .isEqualTo(InitializationConfiguration.DEFAULT_USER_USERNAME);
    }
  }

  @Test
  void shouldRejectUnknownPhysicalTenantHeader() {
    // given — valid default-PT credentials sent with a Camunda-Physical-Tenant header for a PT
    // that is not registered; credentials are valid to rule out a missing-auth false positive
    try (final var unknownPtClient =
        grpcClient(
            "unknownpt",
            InitializationConfiguration.DEFAULT_USER_USERNAME,
            InitializationConfiguration.DEFAULT_USER_PASSWORD)) {
      // when / then — the gateway rejects because "unknownpt" is not in knownTenantIds
      assertThatThrownBy(() -> unknownPtClient.newTopologyRequest().send().join())
          .as("unknown PT header is rejected — PT is not in knownTenantIds")
          .hasRootCauseInstanceOf(StatusRuntimeException.class)
          .rootCause()
          .satisfies(
              e ->
                  assertThat(((StatusRuntimeException) e).getStatus().getCode())
                      .isEqualTo(Status.Code.UNAUTHENTICATED));
    }
  }

  @Test
  void shouldAuthenticateTenantACredentialsOnlyInTenantA() {
    // given — tenanta-admin credentials, seeded only in PT-A's user store
    // when / then — accepted on PT-A
    try (final var homeClient =
        grpcClient(TENANT_A, physicalTenantAdminUsername(TENANT_A), PT_ADMIN_PASSWORD)) {
      assertThatNoException()
          .as("tenanta-admin is accepted on its home PT")
          .isThrownBy(() -> homeClient.newTopologyRequest().send().join());
    }

    // when / then — rejected on PT-B whose store does not contain these credentials
    try (final var wrongTenant =
        grpcClient(TENANT_B, physicalTenantAdminUsername(TENANT_A), PT_ADMIN_PASSWORD)) {
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
    // given — tenantb-admin credentials, seeded only in PT-B's user store
    // when / then — accepted on PT-B
    try (final var homeClient =
        grpcClient(TENANT_B, physicalTenantAdminUsername(TENANT_B), PT_ADMIN_PASSWORD)) {
      assertThatNoException()
          .as("tenantb-admin is accepted on its home PT")
          .isThrownBy(() -> homeClient.newTopologyRequest().send().join());
    }

    // when / then — rejected on PT-A whose store does not contain these credentials
    try (final var wrongTenant =
        grpcClient(TENANT_A, physicalTenantAdminUsername(TENANT_B), PT_ADMIN_PASSWORD)) {
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

  /**
   * Builds a gRPC-first basic-auth client for the default physical tenant — no {@code
   * physicalTenantId}, so no {@code Camunda-Physical-Tenant} header is sent and the gateway must
   * resolve the absent header to {@code default}.
   */
  private static CamundaClient defaultGrpcClient(final String username, final String password) {
    return BROKER
        .newClientBuilder()
        .preferRestOverGrpc(false)
        .credentialsProvider(basicAuth(username, password))
        .build();
  }

  /**
   * Builds a gRPC-first basic-auth client targeting {@code targetTenantId} with the given
   * credentials. The username is passed explicitly so callers can use credentials from a different
   * PT to prove cross-PT rejection in isolation.
   */
  private static CamundaClient grpcClient(
      final String targetTenantId, final String username, final String password) {
    final String base = BROKER.restAddress().toString().replaceAll("/+$", "");
    final URI restAddress = URI.create(base + "/physical-tenants/" + targetTenantId);
    return BROKER
        .newClientBuilder()
        .physicalTenantId(targetTenantId)
        .preferRestOverGrpc(false)
        .restAddress(restAddress)
        .grpcAddress(BROKER.grpcAddress())
        .credentialsProvider(basicAuth(username, password))
        .build();
  }

  private static CredentialsProvider basicAuth(final String username, final String password) {
    return new BasicAuthCredentialsProviderBuilder()
        .applyEnvironmentOverrides(false)
        .username(username)
        .password(password)
        .build();
  }
}
