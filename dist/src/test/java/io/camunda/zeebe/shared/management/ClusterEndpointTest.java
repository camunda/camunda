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

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateZonePrioritiesRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.management.cluster.ConfigurationChange;
import io.camunda.zeebe.management.cluster.GetConfigurationChangesResponse;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
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
                "replicationFactor", new String[] {"3"},
                "physicalTenant", new String[] {"tenant-a"}));

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
  class ClusterTopologyEndpoint {

    private CurrentClusterConfiguration singleTenantConfiguration() {
      return CurrentClusterConfiguration.fromLegacy(
          ClusterConfiguration.init()
              .addMember(
                  MemberId.from("0"),
                  MemberState.initializeAsActive(
                      Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))));
    }

    @Test
    void shouldReturn404ForUnknownPhysicalTenant() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      when(sender.getTopology())
          .thenReturn(CompletableFuture.completedFuture(Either.right(singleTenantConfiguration())));

      // when
      final var response = endpoint.clusterTopology("does-not-exist");

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void shouldTreatBlankPhysicalTenantAsAbsent() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      when(sender.getTopology())
          .thenReturn(CompletableFuture.completedFuture(Either.right(singleTenantConfiguration())));

      // when
      final var response = endpoint.clusterTopology("   ");

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void shouldReturnTopologyWhenPhysicalTenantIsAbsent() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      when(sender.getTopology())
          .thenReturn(CompletableFuture.completedFuture(Either.right(singleTenantConfiguration())));

      // when
      final var response = endpoint.clusterTopology(null);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void shouldReturn200ForScopedTenantWhenConfigurationIsUninitialized() {
      // given — an uninitialized configuration has zero partition groups (broker startup, or
      // right after a restore); a request scoped to a physical tenant must still get the
      // uninitialized body, not a 404, since "no groups yet" means "not bootstrapped", not
      // "unknown tenant".
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      when(sender.getTopology())
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(CurrentClusterConfiguration.uninitialized())));

      // when
      final var response = endpoint.clusterTopology("default");

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(200);
      final var body = (GetTopologyResponse) response.getBody();
      assertThat(body).isNotNull();
      assertThat(body.getVersion()).isEqualTo(-1);
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
                      new ClusterConfigurationChangeResponse(
                          1L,
                          new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                              Map.of(), Map.of(), List.of()),
                          null))));

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
                      new ClusterConfigurationChangeResponse(
                          1L,
                          new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                              Map.of(), Map.of(), List.of()),
                          null))));

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

  @Nested
  class ConfigurationChangesEndpoint {

    // ClusterConfiguration.init() starts at version 1, so a change started from it always gets
    // changeId 2 (version + 1); tests below query that fixed id.
    private static ClusterConfiguration configWithPendingChange() {
      return ClusterConfiguration.init()
          .startConfigurationChange(List.of(new MemberJoinOperation(MemberId.from("1"))));
    }

    @Test
    void shouldGetConfigurationChangeById() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      when(sender.getTopology())
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(CurrentClusterConfiguration.fromLegacy(configWithPendingChange()))));

      // when
      final var response = endpoint.getConfigurationChange("2");

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(200);
      final var body = (ConfigurationChange) response.getBody();
      assertThat(body).isNotNull();
      assertThat(body.getId()).isEqualTo(2L);
      assertThat(body.getStatus()).isEqualTo(ConfigurationChange.StatusEnum.IN_PROGRESS);
      verify(sender).getTopology();
    }

    @Test
    void shouldReturn404WhenChangeIdIsUnknown() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      when(sender.getTopology())
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(CurrentClusterConfiguration.fromLegacy(configWithPendingChange()))));

      // when
      final var response = endpoint.getConfigurationChange("999");

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void shouldRejectNonNumericChangeId() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);

      // when
      final var response = endpoint.getConfigurationChange("not-a-number");

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      verifyNoInteractions(sender);
    }

    @Test
    void shouldListConfigurationChanges() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);
      when(sender.getTopology())
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(CurrentClusterConfiguration.fromLegacy(configWithPendingChange()))));

      // when
      final var response = endpoint.listConfigurationChanges();

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(200);
      final var body = (GetConfigurationChangesResponse) response.getBody();
      assertThat(body).isNotNull();
      assertThat(body.getChanges()).extracting(ConfigurationChange::getId).containsExactly(2L);
      verify(sender).getTopology();
    }
  }
}
