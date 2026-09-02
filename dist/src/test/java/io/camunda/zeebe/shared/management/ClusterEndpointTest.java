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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateZonePrioritiesRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationJsonSerializer;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.management.cluster.AddZoneRequest;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequest;
import io.camunda.zeebe.management.cluster.ClusterConfigPatchRequestPartitions;
import io.camunda.zeebe.management.cluster.ConfigurationChange;
import io.camunda.zeebe.management.cluster.Error;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
  class PurgeEndpoint {

    @Test
    void shouldPurgeEveryPhysicalTenantWhenParameterIsAbsent() {
      // given
      final var sender = senderAcceptingPurge();
      final var endpoint = new ClusterEndpoint(sender);

      // when
      endpoint.purge(false, null).join();

      // then
      verify(sender).purge(new PurgeRequest(Optional.empty(), false));
    }

    @Test
    void shouldPurgeOnlyTheGivenPhysicalTenant() {
      // given
      final var sender = senderAcceptingPurge();
      final var endpoint = new ClusterEndpoint(sender);

      // when
      endpoint.purge(true, "tenant-a").join();

      // then
      verify(sender).purge(new PurgeRequest(Optional.of("tenant-a"), true));
    }

    @Test
    void shouldTreatABlankPhysicalTenantAsAbsent() {
      // given
      final var sender = senderAcceptingPurge();
      final var endpoint = new ClusterEndpoint(sender);

      // when
      endpoint.purge(false, "  ").join();

      // then
      verify(sender).purge(new PurgeRequest(Optional.empty(), false));
    }

    private ClusterConfigurationManagementRequestSender senderAcceptingPurge() {
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      when(sender.purge(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(
                      new ClusterConfigurationChangeResponse(
                          1L,
                          new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                              Map.of(), Map.of(), List.of()),
                          null))));
      return sender;
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
      final var config = ClusterConfiguration.init();
      return ClusterConfiguration.builder()
          .from(config)
          .version(config.version() + 1)
          .pendingChanges(
              Optional.of(
                  DependencyChangePlan.sequential(
                      config.version() + 1, List.of(new MemberJoinOperation(MemberId.from("1"))))))
          .build();
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

  @Nested
  class DumpEndpoint {

    @Test
    void shouldDumpTheConfigurationAsJson() {
      // given
      final var configuration = configuration();
      final var endpoint = new ClusterEndpoint(senderReturning(configuration));

      // when
      final var response = endpoint.dumpConfigurationAsJson();

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(200);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertThat(response.getBody())
          .asInstanceOf(InstanceOfAssertFactories.STRING)
          .isEqualTo(ClusterConfigurationJsonSerializer.toJson(configuration));
    }

    @Test
    void shouldDumpTheSameConfigurationAsProtobuf() {
      // given
      final var configuration = configuration();
      final var endpoint = new ClusterEndpoint(senderReturning(configuration));

      // when
      final var response = endpoint.dumpConfigurationAsProtobuf();

      // then — the two encodings describe the same configuration, which is the point of deriving
      // both from the schema the cluster itself uses
      assertThat(response.getStatusCode().value()).isEqualTo(200);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROTOBUF);
      assertThat(response.getBody())
          .isEqualTo(new ProtoBufSerializer().encodeCurrentClusterConfiguration(configuration));
    }

    @Test
    void shouldOfferProtobufAsAnAttachment() {
      // given — a bare curl would otherwise write binary straight to the terminal
      final var endpoint = new ClusterEndpoint(senderReturning(configuration()));

      // when
      final var response = endpoint.dumpConfigurationAsProtobuf();

      // then
      assertThat(response.getHeaders().getContentDisposition().isAttachment()).isTrue();
    }

    private CurrentClusterConfiguration configuration() {
      return CurrentClusterConfiguration.fromLegacy(
          ClusterConfiguration.init()
              .addMember(
                  MemberId.from("0"),
                  MemberState.initializeAsActive(
                      Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())))));
    }

    private ClusterConfigurationManagementRequestSender senderReturning(
        final CurrentClusterConfiguration configuration) {
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      when(sender.getTopology())
          .thenReturn(CompletableFuture.completedFuture(Either.right(configuration)));
      return sender;
    }
  }

  @Nested
  class PatchClusterEndpoint {

    @Test
    void shouldScaleTheGivenPhysicalTenant() {
      // given
      final var sender = senderAcceptingPatch();
      final var endpoint = new ClusterEndpoint(sender);

      // when
      endpoint.updateClusterConfiguration(false, false, partitionCountRequest(3), "tenant-a");

      // then
      verify(sender)
          .patchCluster(
              new ClusterPatchRequest(
                  Set.of(),
                  Set.of(),
                  Optional.of(3),
                  Optional.empty(),
                  Optional.of("tenant-a"),
                  false));
    }

    @Test
    void shouldScaleTheDefaultPhysicalTenantWhenParameterIsAbsent() {
      // given
      final var sender = senderAcceptingPatch();
      final var endpoint = new ClusterEndpoint(sender);

      // when
      endpoint.updateClusterConfiguration(false, false, partitionCountRequest(3), null);

      // then — an absent tenant is not passed on at all, which the transformer reads as the
      // default tenant
      verify(sender)
          .patchCluster(
              new ClusterPatchRequest(
                  Set.of(), Set.of(), Optional.of(3), Optional.empty(), Optional.empty(), false));
    }

    @Test
    void shouldTreatABlankPhysicalTenantAsAbsent() {
      // given
      final var sender = senderAcceptingPatch();
      final var endpoint = new ClusterEndpoint(sender);

      // when
      endpoint.updateClusterConfiguration(false, false, partitionCountRequest(3), "  ");

      // then
      verify(sender)
          .patchCluster(
              new ClusterPatchRequest(
                  Set.of(), Set.of(), Optional.of(3), Optional.empty(), Optional.empty(), false));
    }

    private ClusterConfigPatchRequest partitionCountRequest(final int count) {
      return new ClusterConfigPatchRequest()
          .partitions(new ClusterConfigPatchRequestPartitions().count(count));
    }

    private ClusterConfigurationManagementRequestSender senderAcceptingPatch() {
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      when(sender.patchCluster(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(
                      new ClusterConfigurationChangeResponse(
                          1L,
                          new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                              Map.of(), Map.of(), List.of()),
                          null))));
      return sender;
    }
  }

  @Nested
  class AddZoneEndpoint {

    @Test
    void shouldDeriveZonedBrokerIdsFromNumberOfBrokers() {
      // given
      final var sender = senderAcceptingAddZone();
      final var endpoint = new ClusterEndpoint(sender);

      // when
      final var response =
          endpoint.addZone(
              "zone-a",
              new AddZoneRequest().numberOfReplicas(2).priority(100).numberOfBrokers(3),
              false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(202);
      verify(sender)
          .addZone(
              new ClusterConfigurationManagementRequest.AddZoneRequest(
                  "zone-a",
                  2,
                  100,
                  Set.of(
                      MemberId.from("zone-a", 0),
                      MemberId.from("zone-a", 1),
                      MemberId.from("zone-a", 2)),
                  false));
    }

    @Test
    void shouldRejectWhenBothBrokersAndNumberOfBrokersSet() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);

      // when
      final var response =
          endpoint.addZone(
              "zone-a",
              new AddZoneRequest()
                  .numberOfReplicas(1)
                  .priority(100)
                  .brokers(List.of(new BrokerId.String("zone-a_0")))
                  .numberOfBrokers(1),
              false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      verifyNoInteractions(sender);
    }

    @Test
    void shouldRejectWhenNeitherBrokersNorNumberOfBrokersSet() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);

      // when
      final var response =
          endpoint.addZone(
              "zone-a",
              new AddZoneRequest().numberOfReplicas(1).priority(100).brokers(List.of()),
              false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      verifyNoInteractions(sender);
    }

    @Test
    void shouldRejectNonPositiveNumberOfBrokers() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);

      // when
      final var response =
          endpoint.addZone(
              "zone-a",
              new AddZoneRequest().numberOfReplicas(1).priority(100).numberOfBrokers(0),
              false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      verifyNoInteractions(sender);
    }

    @Test
    void shouldRejectInvalidZoneIdWhenDerivingFromNumberOfBrokers() {
      // given
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      final var endpoint = new ClusterEndpoint(sender);

      // when
      // underscore is reserved as the zone/nodeIdx separator, so it's not a valid zone character
      final var response =
          endpoint.addZone(
              "zone_a",
              new AddZoneRequest().numberOfReplicas(1).priority(100).numberOfBrokers(1),
              false);

      // then
      assertThat(response.getStatusCode().value()).isEqualTo(400);
      assertThat(((Error) response.getBody()).getMessage())
          .contains("alphanumeric")
          .contains("hyphens");
      verifyNoInteractions(sender);
    }

    private ClusterConfigurationManagementRequestSender senderAcceptingAddZone() {
      final var sender = mock(ClusterConfigurationManagementRequestSender.class);
      when(sender.addZone(any()))
          .thenReturn(
              CompletableFuture.completedFuture(
                  Either.right(
                      new ClusterConfigurationChangeResponse(
                          1L,
                          new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                              Map.of(), Map.of(), List.of()),
                          null))));
      return sender;
    }
  }
}
