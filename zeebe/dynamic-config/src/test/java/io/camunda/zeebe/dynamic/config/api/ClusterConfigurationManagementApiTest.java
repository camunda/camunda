/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Test to verify that server handles requests from the clients. This test uses the actual
// communicationService to ensure that request subscription and handling is done correctly.
final class ClusterConfigurationManagementApiTest {
  private ClusterConfigurationManagementRequestSender clientApi;
  private final RecordingChangeCoordinator recordingCoordinator = new RecordingChangeCoordinator();
  private ClusterConfigurationRequestServer requestServer;
  private AtomixCluster gateway;
  private AtomixCluster coordinator;
  private final MemberId id0 = MemberId.from("0");
  private final MemberId id1 = MemberId.from("1");
  private final MemberId id2 = MemberId.from("2");
  private final MemberId id3 = MemberId.from("3");
  private final ClusterConfiguration initialTopology =
      ClusterConfiguration.init().addMember(id0, MemberState.initializeAsActive(Map.of()));
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();

  private final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();

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
        new ClusterConfigurationManagementRequestSender(
            gateway.getCommunicationService(),
            ClusterConfigurationCoordinatorSupplier.of(
                () -> recordingCoordinator.getClusterConfiguration().join()),
            new ProtoBufSerializer());

    requestServer =
        new ClusterConfigurationRequestServer(
            coordinator.getCommunicationService(),
            new ProtoBufSerializer(),
            new ClusterConfigurationManagementRequestsHandler(
                recordingCoordinator, id0, new TestConcurrencyControl(), true));

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
    final var request =
        new ClusterConfigurationManagementRequest.AddMembersRequest(Set.of(id1), false);

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
    final var request =
        new ClusterConfigurationManagementRequest.RemoveMembersRequest(Set.of(id1, id2), false);

    // when
    final var changeStatus = clientApi.removeMembers(request).join().get();

    // then
    final List<ClusterConfigurationChangeOperation> expected =
        List.of(new MemberLeaveOperation(id1), new MemberLeaveOperation(id2));
    assertThat(changeStatus.plannedChanges()).containsExactlyElementsOf(expected);
  }

  @Test
  void shouldJoinPartition() {
    // given
    final var request =
        new ClusterConfigurationManagementRequest.JoinPartitionRequest(id1, 1, 3, false);

    // when
    final var changeStatus = clientApi.joinPartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionJoinOperation(id1, 1, 3));
  }

  @Test
  void shouldLeavePartition() {
    // given
    final var request =
        new ClusterConfigurationManagementRequest.LeavePartitionRequest(id1, 1, false);

    // when
    final var changeStatus = clientApi.leavePartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionLeaveOperation(id1, 1, 1));
  }

  @Test
  void shouldReassignPartitions() {
    // given
    final var request =
        new ClusterConfigurationManagementRequest.ReassignPartitionsRequest(
            Set.of(id1, id2), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .addMember(
                id1,
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(1, partitionConfig),
                        2,
                        PartitionState.active(1, partitionConfig))))
            .addMember(id2, MemberState.initializeAsActive(Map.of()));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.reassignPartitions(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionJoinOperation(id2, 2, 1), new PartitionLeaveOperation(id1, 2, 1));
  }

  @Test
  void shouldScaleBrokers() {
    // given
    final var request = new BrokerScaleRequest(Set.of(id0, id1), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new MemberJoinOperation(id1),
            new PartitionJoinOperation(id1, 2, 1),
            new PartitionLeaveOperation(id0, 2, 1));
  }

  @Test
  void shouldScaleBrokersWithNewReplicationFactor() {
    // given
    final var request = new BrokerScaleRequest(Set.of(id0, id1), Optional.of(2), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .hasSize(4)
        .startsWith(new MemberJoinOperation(id1))
        .contains(new PartitionJoinOperation(id1, 2, 2))
        .containsSequence(
            new PartitionJoinOperation(id1, 1, 1),
            new PartitionReconfigurePriorityOperation(id0, 1, 2));
  }

  @Test
  void shouldRejectScaleRequestWithInvalidReplicationFactor() {
    // given
    final var request = new BrokerScaleRequest(Set.of(id0, id1), Optional.of(0), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

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
    final var request = new BrokerScaleRequest(Set.of(id0, id1), Optional.of(1), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionLeaveOperation(id0, 2, 1),
            new PartitionLeaveOperation(id1, 1, 1),
            new PartitionReconfigurePriorityOperation(id0, 1, 1),
            new PartitionReconfigurePriorityOperation(id1, 2, 1));
  }

  @Test
  void shouldForceScaleDown() {
    // given
    final var request = new BrokerScaleRequest(Set.of(id0, id2), false);
    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .addMember(id2, MemberState.initializeAsActive(Map.of()))
            .addMember(id3, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(id2, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(id3, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));
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
  void shouldScaleClusterByNewClusterSizeAndPartitionCount() {
    // given
    final var request =
        new ClusterScaleRequest(Optional.of(2), Optional.of(3), Optional.empty(), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleCluster(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new MemberJoinOperation(id1),
            new PartitionJoinOperation(id1, 2, 1),
            new PartitionLeaveOperation(id0, 2, 1),
            new StartPartitionScaleUp(id0, 3),
            new PartitionBootstrapOperation(id0, 3, 1, true),
            new AwaitRedistributionCompletion(id0, 3, new TreeSet<>(List.of(3))),
            new AwaitRelocationCompletion(id0, 3, new TreeSet<>(List.of(3))));
  }

  @Test
  void shouldPatchCluster() {
    // given
    final var request =
        new ClusterPatchRequest(Set.of(id1), Set.of(), Optional.of(3), Optional.empty(), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.patchCluster(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new MemberJoinOperation(id1),
            new PartitionJoinOperation(id1, 2, 1),
            new PartitionLeaveOperation(id0, 2, 1),
            new StartPartitionScaleUp(id0, 3),
            new PartitionBootstrapOperation(id0, 3, 1, true),
            new AwaitRedistributionCompletion(id0, 3, new TreeSet<>(List.of(3))),
            new AwaitRelocationCompletion(id0, 3, new TreeSet<>(List.of(3))));
  }

  @Test
  void shouldForceRemoveBrokers() {
    // given
    final var request = new ForceRemoveBrokersRequest(Set.of(id1, id3), false);
    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(id0, MemberState.initializeAsActive(Map.of()))
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .addMember(id2, MemberState.initializeAsActive(Map.of()))
            .addMember(id3, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(id2, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(id3, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.forceRemoveBrokers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(id0, 1, List.of(id0)),
            new PartitionForceReconfigureOperation(id2, 2, List.of(id2)),
            new MemberRemoveOperation(id0, id1),
            new MemberRemoveOperation(id0, id3));
  }

  @Test
  void shouldDisableExporter() {
    // given
    final String exporterId = "exporterId";
    final var request =
        new ClusterConfigurationManagementRequest.ExporterDisableRequest(exporterId, false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    recordingCoordinator.setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.disableExporter(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionDisableExporterOperation(id0, 1, exporterId));
  }

  @Test
  void shouldDeleteExporter() {
    // given
    final String exporterId = "exporterId";
    final var request =
        new ClusterConfigurationManagementRequest.ExporterDeleteRequest(exporterId, false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    recordingCoordinator.setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.deleteExporter(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionDeleteExporterOperation(id0, 1, exporterId));
  }

  @Test
  void shouldEnableExporter() {
    // given
    final String exporterId = "exporterId";
    final var request =
        new ClusterConfigurationManagementRequest.ExporterEnableRequest(
            exporterId, Optional.empty(), false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportersConfig(
                Map.of(exporterId, new ExporterState(1, State.DISABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    recordingCoordinator.setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.enableExporter(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionEnableExporterOperation(id0, 1, exporterId, Optional.empty()));
  }

  @Test
  void shouldReturnInvalidErrorForInvalidRequests() {
    // given
    final var request = new BrokerScaleRequest(Set.of(), false); // invalid request when no brokers
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

  @Test
  void shouldPurgeCluster() {
    // given
    recordingCoordinator.setCurrentTopology(
        initialTopology
            .addMember(id1, MemberState.initializeAsActive(Map.of()))
            .addMember(id2, MemberState.initializeAsActive(Map.of()))
            .updateMember(id0, m -> m.addPartition(0, PartitionState.active(2, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(0, PartitionState.active(1, partitionConfig)))
            .updateMember(id2, m -> m.addPartition(0, PartitionState.active(1, partitionConfig)))
            .updateMember(id0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(id1, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(id2, m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));
    final var request = new PurgeRequest(false);

    // when
    final var changeStatus = clientApi.purge(request).join().get();

    // then
    final var currentConfiguration =
        changeStatus.currentConfiguration().values().stream()
            .map(MemberState::partitions)
            .collect(Collectors.toSet());
    final var expectedConfiguration =
        changeStatus.expectedConfiguration().values().stream()
            .map(MemberState::partitions)
            .collect(Collectors.toSet());

    assertThat(currentConfiguration).containsExactlyElementsOf(expectedConfiguration);
  }
}
