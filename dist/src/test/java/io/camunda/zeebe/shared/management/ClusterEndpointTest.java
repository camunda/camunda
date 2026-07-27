/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateZonePrioritiesRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig;
import io.camunda.zeebe.management.cluster.PartitionDistributionConfig.TypeEnum;
import io.camunda.zeebe.management.cluster.UpdatePartitionDistributionRequest;
import io.camunda.zeebe.management.cluster.ZoneSpec;
import io.camunda.zeebe.util.Either;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class ClusterEndpointTest {

  @Test
  void shouldAllowKnownQueryParameters() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(
            Map.of(
                "dryRun", new String[] {"true"},
                "force", new String[] {"false"},
                "replicationFactor", new String[] {"3"}));

    // when - then
    assertThatCode(() -> endpoint.validateRequestParameters(request)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectUnknownQueryParameter() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(Map.of("dryRun", new String[] {"true"}, "unknown", new String[] {"x"}));

    // when - then
    assertThatThrownBy(() -> endpoint.validateRequestParameters(request))
        .hasMessage("Unsupported query parameter(s): unknown");
  }

  @Test
  void shouldRejectAndSortMultipleUnknownQueryParameters() {
    // given
    final var endpoint = createEndpoint();
    final var request = mock(HttpServletRequest.class);
    when(request.getParameterMap())
        .thenReturn(Map.of("x", new String[] {"1"}, "y", new String[] {"2"}));

    // when - then
    assertThatThrownBy(() -> endpoint.validateRequestParameters(request))
        .hasMessage("Unsupported query parameter(s): x, y");
  }

  private ClusterEndpoint createEndpoint() {
    return new ClusterEndpoint(mock(ClusterConfigurationManagementRequestSender.class));
  }

  @Nested
  class ModeChangeEndpoint {
    @Test
    void shouldAllowModeQueryParameter() {
      // given
      final var endpoint = createEndpoint();
      final var request = mock(HttpServletRequest.class);
      when(request.getParameterMap()).thenReturn(Map.of("mode", new String[] {"RECOVERING"}));

      // when - then
      assertThatCode(() -> endpoint.validateRequestParameters(request)).doesNotThrowAnyException();
    }

    @Test
    void shouldRequestModeChangeToRecovering() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      final var changeResponse =
          new ClusterConfigurationChangeResponse(1L, Map.of(), Map.of(), List.of());
      when(sender.modeChange(
              new ModeChangeRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, Mode.RECOVERING, false)))
          .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

      // when
      final var response =
          endpoint.updateMode(Mode.RECOVERING, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(202);
      verify(sender)
          .modeChange(
              new ModeChangeRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, Mode.RECOVERING, false));
    }

    @Test
    void shouldRequestModeChangeForGivenPhysicalTenant() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      final var changeResponse =
          new ClusterConfigurationChangeResponse(1L, Map.of(), Map.of(), List.of());
      when(sender.modeChange(new ModeChangeRequest("tenant-a", Mode.RECOVERING, false)))
          .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

      // when
      final var response = endpoint.updateMode(Mode.RECOVERING, "tenant-a", false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(202);
      verify(sender).modeChange(new ModeChangeRequest("tenant-a", Mode.RECOVERING, false));
    }

    @Test
    void shouldRequestModeChangeToProcessing() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      final var changeResponse =
          new ClusterConfigurationChangeResponse(1L, Map.of(), Map.of(), List.of());
      when(sender.modeChange(
              new ModeChangeRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, Mode.PROCESSING, false)))
          .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

      // when
      final var response =
          endpoint.updateMode(Mode.PROCESSING, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(202);
      verify(sender)
          .modeChange(
              new ModeChangeRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, Mode.PROCESSING, false));
    }

    @Test
    void shouldPassDryRunFlagOnModeChange() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      final var changeResponse =
          new ClusterConfigurationChangeResponse(1L, Map.of(), Map.of(), List.of());
      when(sender.modeChange(
              new ModeChangeRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, Mode.RECOVERING, true)))
          .thenReturn(CompletableFuture.completedFuture(Either.right(changeResponse)));

      // when
      endpoint.updateMode(Mode.RECOVERING, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, true);

      // then
      verify(sender)
          .modeChange(
              new ModeChangeRequest(
                  PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, Mode.RECOVERING, true));
    }
  }

  @Nested
  class UpdatePartitionDistributionEndpoint {

    private static PartitionDistributionConfig zoneAwareConfig() {
      return new PartitionDistributionConfig()
          .type(TypeEnum.ZONE_AWARE)
          .zones(List.of(new ZoneSpec().name("zone-a").numberOfReplicas(1).priority(100)));
    }

    @Test
    void shouldSendPartitionDistributionRequestWhenConfigSet() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      final var config = zoneAwareConfig();
      final var expected =
          new UpdatePartitionDistributorConfigRequest(
              ClusterApiUtils.toPartitionDistributorConfig(config), false);
      when(sender.updatePartitionDistribution(expected))
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(
                      new ClusterConfigurationChangeResponse(1L, Map.of(), Map.of(), List.of()))));

      // when
      final var response =
          endpoint.updatePartitionDistribution(
              new UpdatePartitionDistributionRequest().config(config), false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(202);
      verify(sender).updatePartitionDistribution(expected);
    }

    @Test
    void shouldSendZonePrioritiesRequestWhenZonePrioritiesSet() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      final var zoneOrder = List.of("zone-b", "zone-a");
      final var expected = new UpdateZonePrioritiesRequest(zoneOrder, true);
      when(sender.updateZonePriorities(expected))
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(
                      new ClusterConfigurationChangeResponse(1L, Map.of(), Map.of(), List.of()))));

      // when - dryRun flag is forwarded
      final var response =
          endpoint.updatePartitionDistribution(
              new UpdatePartitionDistributionRequest().zonePriorities(zoneOrder), true);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(202);
      verify(sender).updateZonePriorities(expected);
    }

    @Test
    void shouldRejectWhenBothConfigAndZonePrioritiesSet() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);

      // when
      final var response =
          endpoint.updatePartitionDistribution(
              new UpdatePartitionDistributionRequest()
                  .config(zoneAwareConfig())
                  .zonePriorities(List.of("zone-a")),
              false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      verifyNoInteractions(sender);
    }

    @Test
    void shouldRejectWhenNeitherConfigNorZonePrioritiesSet() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);

      // when
      final var response =
          endpoint.updatePartitionDistribution(new UpdatePartitionDistributionRequest(), false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      verifyNoInteractions(sender);
    }
  }
}
