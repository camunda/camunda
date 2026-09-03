/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
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
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.RemovePhysicalTenantOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
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
import java.util.TreeMap;
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
            new PartitionJoinOperation(MemberId.from("2"), 1, 2, true),
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
                        new PartitionJoinOperation(MemberId.from("2"), 1, 2, true),
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
  void shouldDecodeAJoinFromBeforeTwoPhaseJoinsAsAJoinOfAVotingMember() throws Exception {
    // given — a pending join as a version before two-phase joins serialized it: the same message
    // without the asLearner field. Derived from a current encoding by clearing that field, so the
    // test follows the message layout instead of pinning bytes.
    final var groupId = "default";
    final var startedAt = Instant.ofEpochSecond(1_700_000_000);
    final var encodedByCurrentVersion =
        protoBufSerializer.encodeCurrentClusterConfiguration(
            configurationWithPendingJoin(groupId, startedAt, true));
    final var proto =
        Topology.CurrentClusterConfiguration.parseFrom(encodedByCurrentVersion).toBuilder();
    final var graphPhase =
        proto
            .getPhasedChangeStateBuilder()
            .getPendingBuilder(0)
            .getPhasesBuilder(0)
            .getPartitionGroupGraphPhaseBuilder();
    final var graph = graphPhase.getGroupGraphsOrThrow(groupId).toBuilder();
    graph
        .getOperationsBuilder(0)
        .getPartitionGroupOperationBuilder()
        .getPartitionJoinBuilder()
        .clearAsLearner();
    graphPhase.putGroupGraphs(groupId, graph.build());
    final var encodedByPreviousVersion = proto.build().toByteArray();

    // when
    final var decoded =
        protoBufSerializer.decodeCurrentClusterConfiguration(encodedByPreviousVersion);

    // then — the operation keeps its original meaning of a complete, single-step join: nothing in a
    // plan from that version would promote a learner
    assertThat(decoded).isEqualTo(configurationWithPendingJoin(groupId, startedAt, false));
  }

  private static CurrentClusterConfiguration configurationWithPendingJoin(
      final String groupId, final Instant startedAt, final boolean asLearner) {
    final var plan =
        new PhasedChangePlan(
            1,
            0,
            List.of(
                PartitionGroupPhase.sequential(
                    Map.of(
                        groupId,
                        List.of(new PartitionJoinOperation(MemberId.from("1"), 1, 1, asLearner))))),
            startedAt);
    return new CurrentClusterConfiguration(
        CurrentClusterConfiguration.INITIAL_VERSION,
        GlobalConfiguration.init(),
        Map.of(),
        new PhasedChangeState(2L, Map.of(plan.id(), plan), List.of()));
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
                PartitionGroupPhase.sequential(
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
  void shouldEncodeAGraphChangeOnTheLegacyConfigurationAsAQueue() {
    // given — a legacy configuration carrying a dependency-graph change, which is what projecting a
    // partition group produces, with one operation already completed
    final var startedAt = Instant.ofEpochSecond(1_700_000_000);
    final var builder = OperationGraph.builder();
    final var first = builder.add(new PartitionJoinOperation(MemberId.from("1"), 1, 1, true));
    builder.add(new PartitionJoinOperation(MemberId.from("2"), 1, 1, true));
    final var graph =
        new DependencyChangePlan(
            7,
            Status.IN_PROGRESS,
            startedAt,
            builder.build(),
            new TreeMap<>(Map.of(first, startedAt.plusSeconds(5))));
    final var configuration =
        ClusterConfiguration.builder()
            .version(3)
            .members(Map.of(MemberId.from("1"), MemberState.initializeAsActive(Map.of())))
            .pendingChanges(Optional.of(graph))
            .build();

    // when
    final var encoded = protoBufSerializer.encode(configuration);
    final var decoded = protoBufSerializer.decodeClusterTopology(encoded, 0, encoded.length);

    // then — the legacy message can only carry a queue, so the graph is flattened into one on the
    // way out. A broker without the graph model reads this field, and would execute the queue one
    // operation at a time.
    assertThat(decoded.pendingChanges()).contains(ClusterChangePlan.flatten(graph));
    assertThat(decoded.pendingChanges().orElseThrow())
        .isInstanceOf(ClusterChangePlan.class)
        .satisfies(
            plan -> {
              assertThat(plan.pendingOperations()).isEqualTo(graph.pendingOperations());
              assertThat(plan.completedOperations()).isEqualTo(graph.completedOperations());
              assertThat(plan.id()).isEqualTo(7);
              // the queue's version is what an old broker merges by: one per completed operation
              assertThat(plan.version()).isEqualTo(2);
            });
  }

  @Test
  void shouldRoundTripAGraphWhoseNodesNameDifferentPartitionGroups() {
    // given — a graph spanning two groups, which is what collapsing phase boundaries into the graph
    // needs and what the per-node group id exists for. Nothing produces one yet; this pins that the
    // wire can carry it, so the follow-up is not blocked on a format change.
    final var builder = OperationGraph.builder();
    final var inTenantA =
        builder.add(
            new UpdateIncarnationNumberOperation(MemberId.from("1")),
            Set.of(),
            Optional.of("tenant-a"));
    builder.add(
        new UpdateIncarnationNumberOperation(MemberId.from("1")),
        Set.of(inTenantA),
        Optional.of("tenant-b"));
    final var graph = builder.build();
    final var plan =
        new DependencyChangePlan(
            11, Status.IN_PROGRESS, Instant.ofEpochSecond(1_700_000_000), graph, new TreeMap<>());
    final var group =
        new PartitionGroupConfiguration(
            2, 0, Map.of(), Optional.empty(), Optional.of(plan), Optional.empty());

    // when
    final var decoded =
        protoBufSerializer.decodePartitionGroupConfiguration(
            protoBufSerializer.encodePartitionGroupConfiguration(group));

    // then — each node keeps its own target, and the crossing edge survives
    assertThat(decoded).isEqualTo(group);
  }

  @Test
  void shouldDecodeAGraphWithoutGroupIdsAsTargetingItsEnclosingSubConfiguration() {
    // given — a graph as written before the per-node group id existed: the field is absent, which
    // means "the sub-configuration holding this graph". Absent must stay absent rather than being
    // guessed at, or such a graph would come back claiming a target it never named.
    final var proto =
        Topology.PartitionGroupConfiguration.newBuilder()
            .setVersion(2)
            .setIncarnationNumber(0)
            .setPendingChanges(
                Topology.DependencyChangePlan.newBuilder()
                    .setId(11)
                    .setStatus(Topology.ChangeStatus.IN_PROGRESS)
                    .setStartedAt(Timestamp.newBuilder().build())
                    .setGraph(
                        Topology.OperationGraph.newBuilder()
                            .addOperations(
                                Topology.PlannedOperation.newBuilder()
                                    .setId(0)
                                    .setPartitionGroupOperation(
                                        Topology.PartitionGroupChangeOperation.newBuilder()
                                            .setMemberId("1")
                                            .setUpdateIncarnationNumber(
                                                Topology.UpdateIncarnationNumberOperation
                                                    .newBuilder())))))
            .build();

    // when
    final var decoded = protoBufSerializer.decodePartitionGroupConfiguration(proto);

    // then
    assertThat(decoded.pendingChanges().orElseThrow().graph().operations().values())
        .allSatisfy(planned -> assertThat(planned.groupId()).isEmpty());
  }

  @Test
  void shouldEncodeAndDecodeAClusterWideGraphChange() {
    // given — the global configuration running a change, whose operations are cluster-wide and so
    // travel through the other arm of PlannedOperation's oneof than a partition group's do
    final var builder = OperationGraph.builder();
    final var first = builder.add(new MemberLeaveOperation(MemberId.from("1")));
    builder.add(new MemberLeaveOperation(MemberId.from("2")), Set.of(first));
    final var plan =
        new DependencyChangePlan(
            9,
            Status.IN_PROGRESS,
            Instant.ofEpochSecond(1_700_000_000),
            builder.build(),
            new TreeMap<>(Map.of(first, Instant.ofEpochSecond(1_700_000_005))));
    final var globalConfiguration =
        new GlobalConfiguration(
            3,
            Optional.of("cluster-x"),
            Map.of(MemberId.from("1"), new BrokerState(1, Instant.EPOCH, BrokerState.State.ACTIVE)),
            Optional.empty(),
            Optional.of(plan),
            Optional.empty());

    // when
    final var decoded =
        protoBufSerializer.decodeGlobalConfiguration(
            protoBufSerializer.encodeGlobalConfiguration(globalConfiguration));

    // then — the graph comes back with its operations, edges and completions intact
    assertThat(decoded).isEqualTo(globalConfiguration);
  }

  @Test
  void shouldRejectADecodedGraphWhoseOperationCannotRunInItsSubConfiguration() {
    // given — a partition group's graph carrying a cluster-wide operation. The oneof lets either
    // kind travel in either scope, but a group cannot run a broker lifecycle step.
    final var proto =
        Topology.PartitionGroupConfiguration.newBuilder()
            .setVersion(2)
            .setIncarnationNumber(0)
            .setPendingChanges(
                Topology.DependencyChangePlan.newBuilder()
                    .setId(11)
                    .setStatus(Topology.ChangeStatus.IN_PROGRESS)
                    .setStartedAt(Timestamp.newBuilder().build())
                    .setGraph(
                        Topology.OperationGraph.newBuilder()
                            .addOperations(
                                Topology.PlannedOperation.newBuilder()
                                    .setId(0)
                                    .setGlobalOperation(
                                        Topology.GlobalChangeOperation.newBuilder()
                                            .setMemberId("1")
                                            .setMemberJoin(
                                                Topology.MemberJoinOperation.newBuilder())))))
            .build();

    // when / then — rejected at decode, not later at the reconciler's cast, so corrupt or
    // forward-versioned state never reaches the execution loop
    assertThatThrownBy(() -> protoBufSerializer.decodePartitionGroupConfiguration(proto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("to be a PartitionGroupOperation");
  }

  @Test
  void shouldRejectADecodedPlannedOperationCarryingNoOperation() {
    // given — a planned operation with neither arm of the oneof set, which no encoder produces; it
    // reaches decode only through corruption or a future encoding this build does not know
    final var proto =
        Topology.GlobalConfiguration.newBuilder()
            .setVersion(1)
            .setPendingChanges(
                Topology.DependencyChangePlan.newBuilder()
                    .setId(1)
                    .setStatus(Topology.ChangeStatus.IN_PROGRESS)
                    .setStartedAt(Timestamp.newBuilder().build())
                    .setGraph(
                        Topology.OperationGraph.newBuilder()
                            .addOperations(Topology.PlannedOperation.newBuilder().setId(0))))
            .build();

    // when / then — rejected rather than skipped: a graph silently missing a step would execute to
    // completion and report success without that step ever having run
    assertThatThrownBy(() -> protoBufSerializer.decodeGlobalConfiguration(proto))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("carries none");
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
