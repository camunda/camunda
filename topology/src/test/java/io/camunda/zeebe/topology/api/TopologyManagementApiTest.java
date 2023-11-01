/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Test to verify that server handles requests from the clients. This test uses the actual
// communicationService to ensure that request subscription and handling is done correctly.
final class TopologyManagementApiTest {
  private TopologyManagementRequestSender clientApi;

  private final RecordingChangeCoordinator recordingCoordinator = new RecordingChangeCoordinator();
  private TopologyRequestServer requestServer;
  private AtomixCluster gateway;
  private AtomixCluster coordinator;
  private final ClusterTopology initialTopology = ClusterTopology.init();

  @BeforeEach
  void setup() {
    final var gatewayNode =
        Node.builder().withId("gateway").withPort(SocketUtil.getNextAddress().getPort()).build();
    final var coordinatorNode =
        Node.builder().withId("0").withPort(SocketUtil.getNextAddress().getPort()).build();

    gateway = createClusterNode(gatewayNode, List.of(gatewayNode, coordinatorNode));
    coordinator = createClusterNode(coordinatorNode, List.of(gatewayNode, coordinatorNode));

    final var gatewayStarted = gateway.start();
    final var coordinatorStarted = coordinator.start();
    CompletableFuture.allOf(gatewayStarted, coordinatorStarted).join();

    clientApi =
        new TopologyManagementRequestSender(
            gateway.getCommunicationService(),
            coordinator.getMembershipService().getLocalMember().id(),
            new ProtoBufSerializer());

    requestServer =
        new TopologyRequestServer(
            coordinator.getCommunicationService(),
            new ProtoBufSerializer(),
            new TopologyManagementRequestsHandler(
                recordingCoordinator, new TestConcurrencyControl()),
            new TestConcurrencyControl());

    requestServer.start();
  }

  @AfterEach
  void tearDown() {
    requestServer.close();
    gateway.stop();
    coordinator.stop();
  }

  private AtomixCluster createClusterNode(final Node localNode, final Collection<Node> nodes) {
    return AtomixCluster.builder()
        .withAddress(localNode.address())
        .withMemberId(localNode.id().id())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withMembershipProtocol(new DiscoveryMembershipProtocol())
        .build();
  }

  @Test
  void shouldGetCurrentTopology() {
    // given
    final var expectedTopology =
        initialTopology.addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));
    recordingCoordinator.setCurrentTopology(expectedTopology);

    // when
    final var topology = clientApi.getTopology().join();

    // then
    assertThat(topology).isEqualTo(expectedTopology);
  }

  @Test
  void shouldAddMembers() {
    // given
    final var request = new TopologyManagementRequest.AddMembersRequest(Set.of(MemberId.from("1")));

    // when
    final var changeStatus = clientApi.addMembers(request).join().get();

    // then
    final var expected = new MemberJoinOperation(MemberId.from("1"));
    assertThat(changeStatus.plannedChanges()).containsExactly(expected);
  }

  @Test
  void shouldRemoveMembers() {
    // given
    recordingCoordinator.setCurrentTopology(
        ClusterTopology.init()
            .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of())));
    final var request =
        new TopologyManagementRequest.RemoveMembersRequest(
            Set.of(MemberId.from("1"), MemberId.from("2")));

    // when
    final var changeStatus = clientApi.removeMembers(request).join().get();

    // then
    final List<TopologyChangeOperation> expected =
        List.of(
            new MemberLeaveOperation(MemberId.from("1")),
            new MemberLeaveOperation(MemberId.from("2")));
    assertThat(changeStatus.plannedChanges()).containsExactlyElementsOf(expected);
  }

  @Test
  void shouldJoinPartition() {
    // given
    final var request =
        new TopologyManagementRequest.JoinPartitionRequest(MemberId.from("1"), 1, 3);

    // when
    final var changeStatus = clientApi.joinPartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionJoinOperation(MemberId.from("1"), 1, 3));
  }

  @Test
  void shouldLeavePartition() {
    // given
    final var request = new TopologyManagementRequest.LeavePartitionRequest(MemberId.from("1"), 1);

    // when
    final var changeStatus = clientApi.leavePartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionLeaveOperation(MemberId.from("1"), 1));
  }

  @Test
  void shouldReassignPartitions() {
    // given
    final var request =
        new TopologyManagementRequest.ReassignPartitionsRequest(
            Set.of(MemberId.from("1"), MemberId.from("2")));
    final ClusterTopology currentTopology =
        ClusterTopology.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1), 2, PartitionState.active(1))))
            .addMember(MemberId.from("2"), MemberState.initializeAsActive(Map.of()));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.reassignPartitions(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionJoinOperation(MemberId.from("2"), 2, 1),
            new PartitionLeaveOperation(MemberId.from("1"), 2));
  }

  @Test
  void shouldScaleBrokers() {
    // given
    final var request =
        new TopologyManagementRequest.ScaleRequest(Set.of(MemberId.from("1"), MemberId.from("2")));
    final ClusterTopology currentTopology =
        ClusterTopology.init()
            .addMember(
                MemberId.from("1"),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1), 2, PartitionState.active(1))));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new MemberJoinOperation(MemberId.from("2")),
            new PartitionJoinOperation(MemberId.from("2"), 2, 1),
            new PartitionLeaveOperation(MemberId.from("1"), 2));
  }
}
