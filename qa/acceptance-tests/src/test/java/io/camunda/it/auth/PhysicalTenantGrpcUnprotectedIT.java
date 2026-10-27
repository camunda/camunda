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

import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the unprotected-API branch of {@link
 * io.camunda.zeebe.gateway.interceptors.impl.AuthenticationInterceptor}: when authentication is
 * disabled, a {@code Camunda-Physical-Tenant} header that names a PT not in {@code knownTenantIds}
 * is rejected with {@link Status#NOT_FOUND} rather than {@link Status#UNAUTHENTICATED}, so an
 * unauthenticated caller cannot probe whether a tenant is provisioned.
 *
 * @see PhysicalTenantGrpcBasicAuthIT for the protected-API (basic-auth) counterpart where the same
 *     header yields {@link Status#UNAUTHENTICATED}
 */
@ZeebeIntegration
final class PhysicalTenantGrpcUnprotectedIT {

  private static final String TENANT_A = "tenanta";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  @TestZeebe(autoStart = false, purgeAfterEach = false)
  private static final TestStandaloneBroker BROKER =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  @BeforeAll
  static void startBroker() {
    BROKER.start();
  }

  @Test
  void shouldRejectUnknownPhysicalTenantWithNotFound() {
    // given — a gRPC client targeting a PT not registered in knownTenantIds; no credentials
    // needed because auth is disabled on this broker
    try (final var client =
        BROKER.newClientBuilder().preferRestOverGrpc(false).physicalTenantId("unknownpt").build()) {
      // when / then — the interceptor returns NOT_FOUND (not UNAUTHENTICATED), proving the
      // unprotected-API guard fires without leaking whether the tenant is provisioned
      assertThatThrownBy(() -> client.newTopologyRequest().send().join())
          .as("unknown PT on an unprotected gRPC API yields NOT_FOUND, not UNAUTHENTICATED")
          .hasRootCauseInstanceOf(StatusRuntimeException.class)
          .rootCause()
          .satisfies(
              e ->
                  assertThat(((StatusRuntimeException) e).getStatus().getCode())
                      .isEqualTo(Status.Code.NOT_FOUND));
    }
  }
}
