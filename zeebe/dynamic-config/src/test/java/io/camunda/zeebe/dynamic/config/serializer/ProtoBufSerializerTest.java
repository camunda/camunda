/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDeleteRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtoBufSerializerTest {

  private final ProtoBufSerializer protoBufSerializer = new ProtoBufSerializer();

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
  void shouldEncodeAndDecodeReassignAllPartitionsRequest() {
    // given
    final var reassignPartitionsRequest =
        new ReassignPartitionsRequest(Set.of(MemberId.from("1"), MemberId.from("2")), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeReassignPartitionsRequest(reassignPartitionsRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeReassignPartitionsRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(reassignPartitionsRequest);
  }

  @Test
  void shouldEncodeAndDecodeJoinPartitionRequest() {
    // given
    final var joinPartitionRequest = new JoinPartitionRequest(MemberId.from("2"), 3, 5, false);

    // when
    final var encodedRequest = protoBufSerializer.encodeJoinPartitionRequest(joinPartitionRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeJoinPartitionRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(joinPartitionRequest);
  }

  @Test
  void shouldEncodeAndDecodeLeavePartitionRequest() {
    // given
    final var leavePartitionRequest = new LeavePartitionRequest(MemberId.from("6"), 2, false);

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
    final var exporterDisableRequest = new ExporterDisableRequest("expId", false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterDisableRequest(exporterDisableRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterDisableRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterDisableRequest);
  }

  @Test
  void shouldEncodeAndDecodeExporterDeleteRequest() {
    // given
    final var exporterDeleteRequest = new ExporterDeleteRequest("expId", false);

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
        new ExporterEnableRequest("expId", Optional.of("expId2"), false);

    // when
    final var encodedRequest =
        protoBufSerializer.encodeExporterEnableRequest(exporterEnableRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodeExporterEnableRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(exporterEnableRequest);
  }

  @Test
  void shouldEncodeAndDecodeClusterScaleRequest() {
    // given
    final var clusterScaleRequest =
        new ClusterScaleRequest(Optional.of(3), Optional.of(15), Optional.of(4), true);

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
    final var purgeRequest = new PurgeRequest(true);

    // when
    final var encodedRequest = protoBufSerializer.encodePurgeRequest(purgeRequest);

    // then
    final var decodedRequest = protoBufSerializer.decodePurgeRequest(encodedRequest);
    assertThat(decodedRequest).isEqualTo(purgeRequest);
  }

  @Test
  void shouldEncodeAndDecodeTopologyChangeResponse() {
    // given
    final var topologyChangeResponse =
        new ClusterConfigurationChangeResponse(
            2,
            Map.of(
                MemberId.from("1"),
                MemberState.initializeAsActive(Map.of()),
                MemberId.from("2"),
                MemberState.initializeAsActive(Map.of())),
            Map.of(MemberId.from("2"), MemberState.initializeAsActive(Map.of())),
            List.of(
                new MemberLeaveOperation(MemberId.from("1")),
                new PartitionJoinOperation(MemberId.from("2"), 1, 2)));

    // when
    final var encodedResponse = protoBufSerializer.encodeResponse(topologyChangeResponse);

    // then
    final var decodedResponse =
        protoBufSerializer.decodeTopologyChangeResponse(encodedResponse).get();
    assertThat(decodedResponse).isEqualTo(topologyChangeResponse);
  }
}
