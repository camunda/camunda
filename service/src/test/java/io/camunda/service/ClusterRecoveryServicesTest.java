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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
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

  private static final String TENANT_B = "tenant-b";

  private final ClusterConfigurationManagementRequestSender sender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final ClusterRecoveryServices services = new ClusterRecoveryServices(sender);

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
