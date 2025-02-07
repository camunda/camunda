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
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.topology.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.topology.api.TopologyCoordinatorSupplier.ClusterTopologyAwareCoordinatorSupplier;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Test to verify that server handles requests from the clients. This test uses the actual
// communicationService to ensure that request subscription and handling is done correctly.
@AutoCloseResources
final class TopologyManagementApiTest {
  private TopologyManagementRequestSender clientApi;
  private final RecordingChangeCoordinator recordingCoordinator = new RecordingChangeCoordinator();
  private TopologyRequestServer requestServer;
  private AtomixCluster gateway;
  private AtomixCluster coordinator;
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");
  @AutoCloseResource private final MeterRegistry registry = new SimpleMeterRegistry();
  private final ClusterTopology initialTopology =
      ClusterTopology.init().addMember(id0, MemberState.initializeAsActive(Map.of()));

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
            new ClusterTopologyAwareCoordinatorSupplier(
                () -> recordingCoordinator.getTopology().join()),
            new ProtoBufSerializer());

    requestServer =
        new TopologyRequestServer(
            coordinator.getCommunicationService(),
            new ProtoBufSerializer(),
            new TopologyManagementRequestsHandler(
                recordingCoordinator, id0, new TestConcurrencyControl()));

    requestServer.start();
  }

  @AfterEach
  void tearDown() {
    requestServer.close();
    gateway.stop();
    coordinator.stop();
  }

  private AtomixCluster createClusterNode(final Node localNode, final Collection<Node> nodes) {
    return AtomixCluster.builder(registry)
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
        initialTopology.addMember(id1, MemberState.initializeAsActive(Map.of()));
    recordingCoordinator.setCurrentTopology(expectedTopology);

    // when
    final var topology = clientApi.getTopology().join();

    // then
    assertThat(topology.get()).isEqualTo(expectedTopology);
  }

  @Test
  void shouldAddMembers() {
    // given
    final var request = new TopologyManagementRequest.AddMembersRequest(Set.of(id1), false);

    // when
    final var changeStatus = clientApi.addMembers(request).join().get();

    // then
    final var expected = new MemberJoinOperation(id1);
    assertThat(changeStatus.plannedChanges()).containsExactly(expected);
  }

  @Test
  void shouldRemoveMembers() {
    // given
    recordingCoordinator.setCurrentTopology(
        initialTopology
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .addMember(id2, MemberState.initializeAsActive(Map.of())));
    final var request = new TopologyManagementRequest.RemoveMembersRequest(Set.of(id1, id2), false);

    // when
    final var changeStatus = clientApi.removeMembers(request).join().get();

    // then
    final List<TopologyChangeOperation> expected =
        List.of(new MemberLeaveOperation(id1), new MemberLeaveOperation(id2));
    assertThat(changeStatus.plannedChanges()).containsExactlyElementsOf(expected);
  }

  @Test
  void shouldJoinPartition() {
    // given
    final var request = new TopologyManagementRequest.JoinPartitionRequest(id1, 1, 3, false);

    // when
    final var changeStatus = clientApi.joinPartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionJoinOperation(id1, 1, 3));
  }

  @Test
  void shouldLeavePartition() {
    // given
    final var request = new TopologyManagementRequest.LeavePartitionRequest(id1, 1, false);

    // when
    final var changeStatus = clientApi.leavePartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges()).containsExactly(new PartitionLeaveOperation(id1, 1));
  }

  @Test
  void shouldReassignPartitions() {
    // given
    final var request =
        new TopologyManagementRequest.ReassignPartitionsRequest(Set.of(id1, id2), false);
    final ClusterTopology currentTopology =
        initialTopology
            .addMember(
                id1,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1), 2, PartitionState.active(1))))
            .addMember(id2, MemberState.initializeAsActive(Map.of()));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.reassignPartitions(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionJoinOperation(id2, 2, 1), new PartitionLeaveOperation(id1, 2));
  }

  @Test
  void shouldScaleBrokers() {
    // given
    final var request = new TopologyManagementRequest.ScaleRequest(Set.of(id0, id1), false);
    final ClusterTopology currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new MemberJoinOperation(id1),
            new PartitionJoinOperation(id1, 2, 1),
            new PartitionLeaveOperation(id0, 2));
  }

  @Test
  void shouldScaleBrokersWithNewReplicationFactor() {
    // given
    final var request =
        new TopologyManagementRequest.ScaleRequest(Set.of(id0, id1), Optional.of(2), false);
    final ClusterTopology currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new MemberJoinOperation(id1),
            new PartitionJoinOperation(id1, 2, 2),
            new PartitionJoinOperation(id1, 1, 1),
            new PartitionReconfigurePriorityOperation(id0, 1, 2));
  }

  @Test
  void shouldRejectScaleRequestWithInvalidReplicationFactor() {
    // given
    final var request =
        new TopologyManagementRequest.ScaleRequest(Set.of(id0, id1), Optional.of(0), false);
    final ClusterTopology currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join();

    // then
    EitherAssert.assertThat(changeStatus)
        .isLeft()
        .left()
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  void shouldReduceReplicationFactorWithoutScalingDown() {
    // given
    final var request =
        new TopologyManagementRequest.ScaleRequest(Set.of(id0, id1), Optional.of(1), false);
    final ClusterTopology currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(2)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1)))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(1)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(2)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionLeaveOperation(id0, 2),
            new PartitionLeaveOperation(id1, 1),
            new PartitionReconfigurePriorityOperation(id0, 1, 1),
            new PartitionReconfigurePriorityOperation(id1, 2, 1));
  }

  @Test
  void shouldForceScaleDown() {
    // given
    final var request = new TopologyManagementRequest.ScaleRequest(Set.of(id0, id2), false);
    final ClusterTopology currentTopology =
        ClusterTopology.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .addMember(id2, MemberState.initializeAsActive(Map.of()))
            .addMember(id3, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2)))
            .updateMember(id2, m -> m.addPartition(2, PartitionState.active(1)))
            .updateMember(id3, m -> m.addPartition(2, PartitionState.active(2)));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.forceScaleDown(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(id0, 1, List.of(id0)),
            new PartitionForceReconfigureOperation(id2, 2, List.of(id2)),
            new MemberRemoveOperation(id0, id1),
            new MemberRemoveOperation(id0, id3));
  }

  @Test
  void shouldReturnInvalidErrorForInvalidRequests() {
    // given
    final var request =
        new TopologyManagementRequest.ScaleRequest(
            Set.of(), false); // invalid request when no brokers
    recordingCoordinator.setCurrentTopology(initialTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join();

    // then
    EitherAssert.assertThat(changeStatus)
        .isLeft()
        .left()
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }
}
