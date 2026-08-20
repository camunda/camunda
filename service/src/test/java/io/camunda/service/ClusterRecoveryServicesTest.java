/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class ClusterRecoveryServicesTest {

  private static final String DEFAULT_TENANT = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final String TENANT_B = "tenant-b";
  private static final String TENANT_C = "tenant-c";
  private static final TenantRestoreEnvironment DEFAULT_ENVIRONMENT =
      new TenantRestoreEnvironment("elasticsearch", false);
  private static final TenantRestoreEnvironment TENANT_B_ENVIRONMENT =
      new TenantRestoreEnvironment("rdbms", true);
  private static final TenantRestoreEnvironment TENANT_C_ENVIRONMENT =
      new TenantRestoreEnvironment("opensearch", false);

  private final ClusterConfigurationManagementRequestSender sender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final ClusterRecoveryServices services =
      new ClusterRecoveryServices(
          sender,
          Map.of(
              DEFAULT_TENANT, DEFAULT_ENVIRONMENT,
              TENANT_B, TENANT_B_ENVIRONMENT,
              TENANT_C, TENANT_C_ENVIRONMENT));

  @Test
  void shouldRequestEveryPhysicalTenantWhenNoneIsGiven() {
    // given
    givenModeChangeAccepted();

    // when
    services.changeMode(null, Mode.RECOVERING, false).join();

    // then — the tenants are left for the cluster to resolve from its own configuration
    verify(sender).modeChange(new ModeChangeRequest(Optional.empty(), Mode.RECOVERING, false));
  }

  @Test
  void shouldRequestOnlyTheGivenPhysicalTenant() {
    // given
    givenModeChangeAccepted();

    // when
    services.changeMode(TENANT_B, Mode.RECOVERING, false).join();

    // then
    verify(sender).modeChange(new ModeChangeRequest(Optional.of(TENANT_B), Mode.RECOVERING, false));
  }

  @Test
  void shouldPassDryRunThrough() {
    // given
    givenModeChangeAccepted();

    // when
    services.changeMode(TENANT_B, Mode.PROCESSING, true).join();

    // then
    verify(sender).modeChange(new ModeChangeRequest(Optional.of(TENANT_B), Mode.PROCESSING, true));
  }

  @Test
  void shouldReturnTheAcceptedChange() {
    // given
    givenModeChangeAccepted();

    // when
    final var result = services.changeMode(null, Mode.RECOVERING, false).join();

    // then — a single change covering every tenant
    assertThat(result.isRight()).isTrue();
    assertThat(result.get().changeId()).isEqualTo(7L);
  }

  @Test
  void shouldReturnTheRejectionOfTheCluster() {
    // given
    when(sender.modeChange(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.INVALID_STATE, "a change is ongoing"))));

    // when
    final var result = services.changeMode(null, Mode.RECOVERING, false).join();

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().code()).isEqualTo(ErrorCode.INVALID_STATE);
  }

  @Test
  void shouldRestoreTheRequestedPhysicalTenantWithItsOwnEnvironment() {
    // given — tenant-b runs rdbms with continuous backups, unlike the default tenant
    givenRestoreAccepted();
    final var parameters = new RestoreParameters(List.of(55L), null, null);

    // when
    services.restore(Optional.of(TENANT_B), parameters, Map.of(), false).join();

    // then
    verify(sender)
        .clusterRestore(
            new ClusterRestoreRequest(
                Map.of(TENANT_B, new TenantRestoreArguments(parameters, "rdbms", true)), false));
  }

  @Test
  void shouldRestoreEveryKnownPhysicalTenantWithItsOwnEnvironment() {
    // given — a cluster-wide restore whose override applies to only one tenant: the default tenant
    // and tenant-c share the top-level backup selection, tenant-b restores from an additional,
    // distinct backup given as its own override
    givenRestoreAccepted();
    final var defaultParameters = new RestoreParameters(List.of(100L), null, null);
    final var tenantBParameters = new RestoreParameters(List.of(55L), null, null);

    // when
    services
        .restore(Optional.empty(), defaultParameters, Map.of(TENANT_B, tenantBParameters), true)
        .join();

    // then — every physical tenant of the cluster is named, each with its own environment; the
    // overridden tenant keeps its own backup selection, the other two share the top-level one
    verify(sender)
        .clusterRestore(
            new ClusterRestoreRequest(
                Map.of(
                    DEFAULT_TENANT,
                    new TenantRestoreArguments(defaultParameters, "elasticsearch", false),
                    TENANT_B,
                    new TenantRestoreArguments(tenantBParameters, "rdbms", true),
                    TENANT_C,
                    new TenantRestoreArguments(defaultParameters, "opensearch", false)),
                true));
  }

  @Test
  void shouldDropAnOverrideForAPhysicalTenantTheClusterDoesNotKnow() {
    // given — a cluster-wide restore whose override names a tenant this cluster has no environment
    // for; only the known tenants are ever restored, so the unknown override is simply not carried
    // into the request rather than surfacing as an error. Rejecting it belongs with the
    // cluster-wide
    // fan-out that ClusterRestoreRequestTransformer does not implement yet — it rejects every
    // multi-tenant restore an override could apply to.
    givenRestoreAccepted();
    final var defaultParameters = new RestoreParameters(List.of(1L), null, null);

    // when
    services
        .restore(
            Optional.empty(),
            defaultParameters,
            Map.of("unknown-tenant", new RestoreParameters(List.of(2L), null, null)),
            false)
        .join();

    // then
    verify(sender)
        .clusterRestore(
            new ClusterRestoreRequest(
                Map.of(
                    DEFAULT_TENANT,
                    new TenantRestoreArguments(defaultParameters, "elasticsearch", false),
                    TENANT_B,
                    new TenantRestoreArguments(defaultParameters, "rdbms", true),
                    TENANT_C,
                    new TenantRestoreArguments(defaultParameters, "opensearch", false)),
                false));
  }

  @Test
  void shouldRejectASingleTenantRestoreOfAnUnknownPhysicalTenant() {
    // when — the request never reaches the wire; there is no environment to build it with
    final var result =
        services
            .restore(
                Optional.of("unknown-tenant"),
                new RestoreParameters(List.of(1L), null, null),
                Map.of(),
                false)
            .join();

    // then — reported as an error response so the REST layer can map it to 404
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().code()).isEqualTo(ErrorCode.NOT_FOUND);
    assertThat(result.getLeft().message()).contains("unknown-tenant");
    verify(sender, never()).clusterRestore(any());
  }

  private void givenRestoreAccepted() {
    when(sender.clusterRestore(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new ClusterConfigurationChangeResponse(
                        9L,
                        new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of()),
                        null))));
  }

  private void givenModeChangeAccepted() {
    when(sender.modeChange(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new ClusterConfigurationChangeResponse(
                        7L,
                        new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of()),
                        null))));
  }
}
