/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterRestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterZoneMigrationRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceZoneRemoveRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossipState;
import io.camunda.zeebe.dynamic.config.protocol.Requests;
import io.camunda.zeebe.dynamic.config.protocol.Topology;
import io.camunda.zeebe.dynamic.config.protocol.Topology.ExporterStateEnum;
import io.camunda.zeebe.dynamic.config.protocol.Topology.MessageCorrelation;
import io.camunda.zeebe.dynamic.config.protocol.Topology.MessageCorrelation.HashMod;
import io.camunda.zeebe.dynamic.config.protocol.Topology.RoutingState;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.Phase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.dynamic.config.state.RoutingState.RequestHandling;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ProtoBufSerializerTest {

  private final ProtoBufSerializer protoBufSerializer = new ProtoBufSerializer();

  @Test
  void shouldEncodeAndDecodeClusterZoneMigrationRequest() {
    // given
    final var request = new ClusterZoneMigrationRequest("us-west-1", true);

    // when
    final var encodedRequest = protoBufSerializer.encodeClusterZoneMigrationRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeClusterZoneMigrationRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeModeChangeRequestForOnePhysicalTenant() {
    // given
    final var request = new ModeChangeRequest(Optional.of("tenant-b"), Mode.RECOVERING, false);

    // when
    final var encodedRequest = protoBufSerializer.encodeModeChangeRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeModeChangeRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeModeChangeRequestForEveryPhysicalTenant() {
    // given — an absent tenant means every physical tenant
    final var request = new ModeChangeRequest(Optional.empty(), Mode.RECOVERING, false);

    // when
    final var encodedRequest = protoBufSerializer.encodeModeChangeRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeModeChangeRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeClusterRestoreRequestForOnePhysicalTenant() {
    // given
    final var request =
        new ClusterRestoreRequest(
            Map.of(
                "tenant-b",
                new TenantRestoreArguments(
                    new RestoreParameters(List.of(100L, 101L), null, null),
                    "elasticsearch",
                    false)),
            true);

    // when
    final var encodedRequest = protoBufSerializer.encodeClusterRestoreRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeClusterRestoreRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeClusterRestoreRequestWithPerTenantArguments() {
    // given — a cluster-wide restore naming several physical tenants at once
    final var request =
        new ClusterRestoreRequest(
            Map.of(
                "tenant-b",
                new TenantRestoreArguments(
                    new RestoreParameters(List.of(55L), null, null), "rdbms", true),
                "tenant-c",
                new TenantRestoreArguments(
                    new RestoreParameters(List.of(), "2024-02-01T10:00:00Z", null), "rdbms", true)),
            false);

    // when
    final var encodedRequest = protoBufSerializer.encodeClusterRestoreRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeClusterRestoreRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeForceRemoveZoneRequest() {
    // given
    final var request = new ForceZoneRemoveRequest("us-west-1", true);

    // when
    final var encodedRequest = protoBufSerializer.encodeForceRemoveZoneRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeForceRemoveZoneRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeAddZoneRequest() {
    // given
    final var request =
        new AddZoneRequest(
            "us-west-1", 3, 1, Set.of(MemberId.from("1"), MemberId.from("2")), false);

    // when
    final var encodedRequest = protoBufSerializer.encodeAddZoneRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeAddZoneRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeAddMembersRequest() {
    // given
    final var addMembersRequest =
        new AddMembersRequest(Set.of(MemberId.from("1"), MemberId.from("2")), false);

    // when
    final var encodedRequest = protoBufSerializer.encodeAddMembersRequest(addMembersRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeAddMembersRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(addMembersRequest);
  }

  @Test
  void shouldEncodeAndDecodeRemoveMembersRequest() {
    // given
    final var removeMembersRequest =
        new RemoveMembersRequest(Set.of(MemberId.from("1"), MemberId.from("2")), false);

    // when
    final var encodedRequest = protoBufSerializer.encodeRemoveMembersRequest(removeMembersRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeRemoveMembersRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(removeMembersRequest);
  }

  @Test
  void shouldEncodeAndDecodeJoinPartitionRequest() {
    // given
    final var joinPartitionRequest =
        new JoinPartitionRequest(MemberId.from("2"), 3, 5, Optional.empty(), false);

    // when
    final var encodedRequest = protoBufSerializer.encodeJoinPartitionRequest(joinPartitionRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeJoinPartitionRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(joinPartitionRequest);
  }

  @Test
  void shouldEncodeAndDecodeJoinPartitionRequestWithPhysicalTenant() {
    // given
    final var joinPartitionRequest =
        new JoinPartitionRequest(MemberId.from("2"), 3, 5, Optional.of("tenant-a"), false);

    // when
    final var encodedRequest = protoBufSerializer.encodeJoinPartitionRequest(joinPartitionRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeJoinPartitionRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(joinPartitionRequest);
  }

  @Test
  void shouldEncodeAndDecodeLeavePartitionRequest() {
    // given
    final var leavePartitionRequest =
        new LeavePartitionRequest(MemberId.from("6"), 2, Optional.empty(), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeLeavePartitionRequest(leavePartitionRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeLeavePartitionRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(leavePartitionRequest);
  }

  @Test
  void shouldEncodeAndDecodeLeavePartitionRequestWithPhysicalTenant() {
    // given
    final var leavePartitionRequest =
        new LeavePartitionRequest(MemberId.from("6"), 2, Optional.of("tenant-a"), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeLeavePartitionRequest(leavePartitionRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeLeavePartitionRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(leavePartitionRequest);
  }

  @Test
  void shouldEncodeAndDecodeExporterDisableRequest() {
    // given
    final var exporterDisableRequest =
        new ExporterDisableRequest("expId", Optional.of("tenant-a"), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterDisableRequest(exporterDisableRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterDisableRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterDisableRequest);
  }

  @Test
  void shouldEncodeAndDecodeExporterDisableRequestWithoutPhysicalTenantId() {
    // given
    final var exporterDisableRequest = new ExporterDisableRequest("expId", Optional.empty(), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterDisableRequest(exporterDisableRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterDisableRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterDisableRequest);
  }

  @Test
  void shouldEncodeAndDecodeExportingStateChangeRequestForOnePhysicalTenant() {
    // given
    final var request =
        new ExportingStateChangeRequest(
            io.camunda.zeebe.dynamic.config.state.ExportingState.SOFT_PAUSED,
            Optional.of("tenant-b"),
            false);

    // when
    final var encodedRequest = protoBufSerializer.encodeExportingStateChangeRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeExportingStateChangeRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeExportingStateChangeRequestForEveryPhysicalTenant() {
    // given — an absent tenant means every physical tenant
    final var request =
        new ExportingStateChangeRequest(
            io.camunda.zeebe.dynamic.config.state.ExportingState.SOFT_PAUSED,
            Optional.empty(),
            false);

    // when
    final var encodedRequest = protoBufSerializer.encodeExportingStateChangeRequest(request);

    // then
    final var decodedRequest = protoBufSerializer.decodeExportingStateChangeRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(request);
  }

  @Test
  void shouldEncodeAndDecodeExporterDeleteRequest() {
    // given
    final var exporterDeleteRequest =
        new ExporterDeleteRequest("expId", Optional.of("tenanta"), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterDeleteRequest(exporterDeleteRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterDeleteRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterDeleteRequest);
  }

  @Test
  void shouldEncodeAndDecodeExporterDeleteRequestWithoutPhysicalTenantId() {
    // given
    final var exporterDeleteRequest = new ExporterDeleteRequest("expId", Optional.empty(), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterDeleteRequest(exporterDeleteRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterDeleteRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterDeleteRequest);
  }

  @Test
  void shouldEncodeAndDecodeExporterEnableRequest() {
    // given
    final var exporterEnableRequest =
        new ExporterEnableRequest("expId", Optional.of("expId2"), Optional.of("tenant-a"), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterEnableRequest(exporterEnableRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterEnableRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterEnableRequest);
  }

  @Test
  void shouldEncodeAndDecodeExporterEnableRequestWithoutPhysicalTenantId() {
    // given
    final var exporterEnableRequest =
        new ExporterEnableRequest("expId", Optional.empty(), Optional.empty(), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterEnableRequest(exporterEnableRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterEnableRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterEnableRequest);
  }

  @Test
  void shouldEncodeAndDecodeUpdateRoutingStateRequestWithPhysicalTenantId() {
    // given
    final var routingState =
        Optional.of(
            new io.camunda.zeebe.dynamic.config.state.RoutingState(
                1L,
                new RequestHandling.AllPartitions(3),
                new io.camunda.zeebe.dynamic.config.state.RoutingState.MessageCorrelation.HashMod(
                    3)));
    final var updateRoutingStateRequest =
        new UpdateRoutingStateRequest(routingState, Optional.of("tenant-a"), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeUpdateRoutingStateRequest(updateRoutingStateRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeUpdateRoutingStateRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(updateRoutingStateRequest);
  }

  @Test
  void shouldEncodeAndDecodeUpdateRoutingStateRequestWithoutPhysicalTenantId() {
    // given
    final var updateRoutingStateRequest =
        new UpdateRoutingStateRequest(Optional.empty(), Optional.empty(), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeUpdateRoutingStateRequest(updateRoutingStateRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeUpdateRoutingStateRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(updateRoutingStateRequest);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "zoneA"})
  void shouldEncodeAndDecodeClusterScaleRequest(final String zone) {
    // given
    final var zoneOpt = Optional.of(zone).filter(s -> !s.isEmpty());
    final var clusterScaleRequest =
        new ClusterScaleRequest(Optional.of(3), Optional.of(15), Optional.of(4), zoneOpt, true);

    // when
    final var encodedRequest = protoBufSerializer.encodeClusterScaleRequest(clusterScaleRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeClusterScaleRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(clusterScaleRequest);
  }

  @Test
  void shouldEncodeAndDecodeClusterScaleRequestWithPhysicalTenantId() {
    // given
    final var clusterScaleRequest =
        new ClusterScaleRequest(
            Optional.empty(),
            Optional.of(15),
            Optional.of(4),
            Optional.empty(),
            Optional.of("tenant-b"),
            true);

    // when
    final var encodedRequest = protoBufSerializer.encodeClusterScaleRequest(clusterScaleRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeClusterScaleRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(clusterScaleRequest);
  }

  @Test
  void shouldEncodeAndDecodeClusterPatchRequest() {
    // given
    final var clusterPatchRequest =
        new ClusterPatchRequest(
            Set.of(MemberId.from("6"), MemberId.from("7")),
            Set.of(MemberId.from("4"), MemberId.from("5")),
            Optional.of(10),
            Optional.of(4),
            true);

    // when
    final var encodedRequest = protoBufSerializer.encodeClusterPatchRequest(clusterPatchRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeClusterPatchRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(clusterPatchRequest);
  }

  @Test
  void shouldEncodeAndDecodeClusterPatchRequestWithPhysicalTenantId() {
    // given
    final var clusterPatchRequest =
        new ClusterPatchRequest(
            Set.of(), Set.of(), Optional.of(10), Optional.of(4), Optional.of("tenant-b"), true);

    // when
    final var encodedRequest = protoBufSerializer.encodeClusterPatchRequest(clusterPatchRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeClusterPatchRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(clusterPatchRequest);
  }

  @Test
  void shouldEncodeAndDecodeForceRemoveBrokersRequest() {
    // given
    final var forceRemoveBrokersRequest =
        new ForceRemoveBrokersRequest(Set.of(MemberId.from("6"), MemberId.from("7")), true);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeForceRemoveBrokersRequest(forceRemoveBrokersRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeForceRemoveBrokersRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(forceRemoveBrokersRequest);
  }

  @Test
  void shouldEncodeAndDecodePurgeRequest() {
    // given
    final var purgeRequest = new PurgeRequest(Optional.empty(), true);

    // when
    final var encodedRequest = protoBufSerializer.encodePurgeRequest(purgeRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodePurgeRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(purgeRequest);
  }

  @Test
  void shouldEncodeAndDecodePurgeRequestWithPhysicalTenantId() {
    // given
    final var purgeRequest = new PurgeRequest(Optional.of("tenanta"), true);

    // when
    final var encodedRequest = protoBufSerializer.encodePurgeRequest(purgeRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodePurgeRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(purgeRequest);
  }

  @Test
  void shouldEncodeAndDecodeTopologyChangeResponse() {
    // given
    final List<ClusterConfigurationChangeOperation> plannedChanges =
        List.of(
            new MemberLeaveOperation(MemberId.from("1")),
            new PartitionJoinOperation(MemberId.from("2"), 1, 2),
            new ModeChangeOperation(MemberId.from("2"), Mode.RECOVERING),
            new AwaitModeChangeOperation(MemberId.from("2"), Mode.RECOVERING),
            new PartitionPreRestoreOperation(MemberId.from("1"), 1),
            new PartitionRestoreOperation(MemberId.from("1"), 1, new TreeSet<>(List.of(1L, 2L))));
    final List<Phase> phases =
        List.of(
            new GlobalPhase(List.of(new MemberLeaveOperation(MemberId.from("1")))),
            PartitionGroupPhase.sequential(
                Map.of(
                    "default",
                    List.of(
                        new PartitionJoinOperation(MemberId.from("2"), 1, 2),
                        new ModeChangeOperation(MemberId.from("2"), Mode.RECOVERING),
                        new AwaitModeChangeOperation(MemberId.from("2"), Mode.RECOVERING)),
                    "anothertenant",
                    List.of(
                        new PartitionPreRestoreOperation(MemberId.from("1"), 1),
                        new PartitionRestoreOperation(
                            MemberId.from("1"), 1, new TreeSet<>(List.of(1L, 2L)))))));
    final var currentMultiConfiguration = CurrentClusterConfiguration.init();
    final var expectedMultiConfiguration =
        CurrentClusterConfiguration.fromLegacy(
            ClusterConfiguration.init()
                .addMember(MemberId.from("9"), MemberState.initializeAsActive(Map.of())));
    final var topologyChangeResponse =
        new ClusterConfigurationChangeResponse(
            2,
            new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                Map.of(
                    MemberId.from("1"),
                    MemberState.initializeAsActive(Map.of()),
                    MemberId.from("2"),
                    MemberState.initializeAsActive(Map.of())),
                Map.of(MemberId.from("2"), MemberState.initializeAsActive(Map.of())),
                plannedChanges),
            new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
                currentMultiConfiguration, expectedMultiConfiguration, phases));

    // when
    final var encodedResponse = protoBufSerializer.encodeResponse(topologyChangeResponse);

    // then
    final var decodedResponse =
        protoBufSerializer.decodeTopologyChangeResponse(encodedResponse).get();
    assertThat(decodedResponse).isEqualTo(topologyChangeResponse);
  }

  @Test
  void shouldEncodeAndDecodeTopologyChangeResponseWithoutMultiConfiguration() {
    // given — a response with no new multi-partition-group data (e.g. produced by a caller that
    // doesn't populate it) round-trips with a null response.
    final var topologyChangeResponse =
        new ClusterConfigurationChangeResponse(
            2,
            new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                Map.of(), Map.of(), List.of()),
            null);

    // when
    final var encodedResponse = protoBufSerializer.encodeResponse(topologyChangeResponse);

    // then
    final var decodedResponse =
        protoBufSerializer.decodeTopologyChangeResponse(encodedResponse).get();
    assertThat(decodedResponse).isEqualTo(topologyChangeResponse);
  }

  @Test
  void shouldDecodeNullResponseWhenMultiConfigurationIsOnlyPartiallyPresent() {
    // given — a wire message where only the current multi-configuration field is set, not the
    // expected one (e.g. from a peer running a mismatched version). This must not decode into a
    // response with a real current configuration and a default/empty expected configuration.
    final var partial =
        Requests.TopologyChangeResponse.newBuilder()
            .setChangeId(3)
            .setCurrentConfiguration(Topology.CurrentClusterConfiguration.newBuilder().build())
            .build();
    final var encoded =
        Requests.Response.newBuilder().setTopologyChangeResponse(partial).build().toByteArray();

    // when
    final var decoded = protoBufSerializer.decodeTopologyChangeResponse(encoded).get();

    // then — the whole multi-config payload is treated as absent, not partially decoded
    assertThat(decoded.response()).isNull();
  }

  @Test
  void shouldEncodeAndDecodePartitionGroupChangeOperationForPartitionPreRestore() {
    // given
    final var groupId = "default";
    final var plan =
        new PhasedChangePlan(
            1,
            0,
            List.of(
                PartitionGroupPhase.sequential(
                    Map.of(
                        groupId,
                        List.of(new PartitionPreRestoreOperation(MemberId.from("1"), 1))))),
            Instant.now());
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(),
            new PhasedChangeState(2L, Map.of(plan.id(), plan), List.of()));

    // when
    final var encoded = protoBufSerializer.encodeCurrentClusterConfiguration(configuration);

    // then
    final var decoded = protoBufSerializer.decodeCurrentClusterConfiguration(encoded);
    assertThat(decoded).isEqualTo(configuration);
  }

  @Test
  void shouldEncodeAndDecodePartitionGroupChangeOperationForPartitionRestore() {
    // given
    final var groupId = "default";
    final var plan =
        new PhasedChangePlan(
            1,
            0,
            List.of(
                PartitionGroupPhase.sequential(
                    Map.of(
                        groupId,
                        List.of(
                            new PartitionRestoreOperation(
                                MemberId.from("1"), 1, new TreeSet<>(List.of(1L, 2L))))))),
            Instant.now());
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(),
            new PhasedChangeState(2L, Map.of(plan.id(), plan), List.of()));

    // when
    final var encoded = protoBufSerializer.encodeCurrentClusterConfiguration(configuration);

    // then
    final var decoded = protoBufSerializer.decodeCurrentClusterConfiguration(encoded);
    assertThat(decoded).isEqualTo(configuration);
  }

  @Test
  void shouldEncodeAndDecodeARemovedPhysicalTenant() {
    // given — a tombstoned tenant, built from a group that had a member before removal cleared it
    final var group =
        new PartitionGroupConfiguration(
                1,
                0,
                Map.of(
                    MemberId.from("1"),
                    new BrokerPartitionState(
                        1,
                        Instant.EPOCH,
                        Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init())),
                        Mode.PROCESSING)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty())
            .disable()
            .remove();
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of("tenant-a", group),
            new PhasedChangeState(1L, Map.of(), List.of()));

    // when
    final var encoded = protoBufSerializer.encodeCurrentClusterConfiguration(configuration);

    // then — the tombstone, and the cleared assignment behind it, survive a round trip; a restarted
    // broker reading a stale, non-empty assignment back would re-arm the block the removal lifted
    final var decoded = protoBufSerializer.decodeCurrentClusterConfiguration(encoded);
    assertThat(decoded).isEqualTo(configuration);
    assertThat(decoded.partitionGroup("tenant-a").isRemoved()).isTrue();
    assertThat(decoded.partitionGroup("tenant-a").members())
        .describedAs("removal clears the old assignment rather than retaining it")
        .isEmpty();
  }

  @Test
  void shouldEncodeAndDecodePartitionGroupChangeOperationForRemovePhysicalTenant() {
    // given
    final var groupId = "tenant-a";
    final var plan =
        new PhasedChangePlan(
            1,
            0,
            List.of(
                new PartitionGroupParallelPhase(
                    Map.of(
                        groupId, List.of(new RemovePhysicalTenantOperation(MemberId.from("1")))))),
            Instant.now());
    final var configuration =
        new CurrentClusterConfiguration(
            CurrentClusterConfiguration.INITIAL_VERSION,
            GlobalConfiguration.init(),
            Map.of(),
            new PhasedChangeState(2L, Map.of(plan.id(), plan), List.of()));

    // when
    final var encoded = protoBufSerializer.encodeCurrentClusterConfiguration(configuration);

    // then — a coordinator restart mid-plan has to be able to read the operation back
    final var decoded = protoBufSerializer.decodeCurrentClusterConfiguration(encoded);
    assertThat(decoded).isEqualTo(configuration);
  }

  @Test
  void shouldDecodeRoutingStateFrom87Correctly() throws InvalidProtocolBufferException {
    final var routingState =
        RoutingState.newBuilder()
            .addAllActivePartitions(List.of(1, 2, 3))
            .setMessageCorrelation(
                MessageCorrelation.newBuilder()
                    .setHashMod(HashMod.newBuilder().setPartitionCount(3))
                    .build())
            .build();
    final var bytes = routingState.toByteArray();
    final var decodedBytes =
        protoBufSerializer.decodeRoutingState(Topology.RoutingState.parseFrom(bytes));
    assertThat(decodedBytes)
        .isPresent()
        .hasValueSatisfying(
            state -> {
              assertThat(state.requestHandling()).isEqualTo(new RequestHandling.AllPartitions(3));
            });
  }

  @Test
  void shouldDecodeExportingConfigWithoutStateCorrectly() throws InvalidProtocolBufferException {
    // given
    final var serialized =
        Topology.ExportingConfig.newBuilder()
            .putAllExporters(
                Map.of(
                    "exporter-1",
                    Topology.ExporterState.newBuilder()
                        .setState(ExporterStateEnum.ENABLED)
                        .build()))
            .build()
            .toByteArray();

    // when
    final var deserialized =
        protoBufSerializer.decodeExportingConfig(Topology.ExportingConfig.parseFrom(serialized));

    // then
    assertThat(deserialized.state()).isEqualTo(ExportingState.UNKNOWN);
  }

  @Test
  void shouldEncodeAndDecodePartitionStateRecovering() {
    // given
    final var partitionConfig = DynamicPartitionConfig.init();
    final var clusterConfiguration =
        ClusterConfiguration.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig).toRecovering())));
    final var gossipState = new ClusterConfigurationGossipState();
    gossipState.setClusterConfiguration(clusterConfiguration);

    // when
    final var decoded = protoBufSerializer.decode(protoBufSerializer.encode(gossipState));

    // then
    assertThat(decoded.getClusterConfiguration()).isEqualTo(clusterConfiguration);
  }
}
