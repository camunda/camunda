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
import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceRemoveBrokersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ForceZoneRemoveRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PurgeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PostScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.PreScalingOperation;
import io.camunda.zeebe.dynamic.config.state.GlobalChangeOperation.UpdatePartitionDistributorConfigOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class ClusterConfigurationManagementApiTestBase {
  protected final MemberId coordinatorId;
  protected ClusterConfigurationManagementRequestSender clientApi;
  protected final RecordingChangeCoordinator recordingCoordinator =
      new RecordingChangeCoordinator();
  protected final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  private final ClusterConfiguration initialTopology;
  private ClusterConfigurationRequestServer requestServer;
  private List<ClusterConfigurationRequestServer> extraRequestServers = List.of();
  private AtomixCluster gateway;
  private AtomixCluster coordinator;
  private List<AtomixCluster> extraNodes = List.of();
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();
  private final Function<Integer, MemberId> memberFactory;

  ClusterConfigurationManagementApiTestBase(final Function<Integer, MemberId> memberFactory) {
    this.memberFactory = memberFactory;
    coordinatorId = memberFactory.apply(0);
    // the physical coordinator node is always coordinatorId, so the recorded topology's
    // coordinator member must be the same id for requests to route to the started node
    initialTopology =
        ClusterConfiguration.init()
            .addMember(coordinatorId, MemberState.initializeAsActive(Map.of()));
  }

  @BeforeEach
  void setup() {
    // seed the coordinator so that the "no topology yet" default still resolves to the physical
    // coordinator node, rather than falling back to member "0"
    recordingCoordinator.setCurrentTopology(initialTopology);

    final var gatewayId = MemberId.from("gateway");
    final var gatewayNode =
        Node.builder()
            .withId(gatewayId.id())
            .withPort(SocketUtil.getNextAddress().getPort())
            .build();
    final var coordinatorNode =
        Node.builder()
            .withId(coordinatorId.id())
            .withPort(SocketUtil.getNextAddress().getPort())
            .build();
    final var extraNodeEntries =
        extraPhysicalMembers().stream()
            .map(
                id ->
                    Map.entry(
                        id,
                        Node.builder()
                            .withId(id.id())
                            .withPort(SocketUtil.getNextAddress().getPort())
                            .build()))
            .toList();
    final var nodes =
        Stream.concat(
                Stream.of(gatewayNode, coordinatorNode),
                extraNodeEntries.stream().map(Map.Entry::getValue))
            .toList();

    gateway = createClusterNode(gatewayId, gatewayNode, nodes);
    coordinator = createClusterNode(coordinatorId, coordinatorNode, nodes);
    final var extraClusters =
        extraNodeEntries.stream()
            .map(
                entry ->
                    Map.entry(
                        entry.getKey(), createClusterNode(entry.getKey(), entry.getValue(), nodes)))
            .toList();
    extraNodes = extraClusters.stream().map(Map.Entry::getValue).toList();

    final var gatewayStarted = gateway.start();
    final var coordinatorStarted = coordinator.start();
    final var extraStarted = extraNodes.stream().map(AtomixCluster::start).toList();
    CompletableFuture.allOf(
            Stream.concat(Stream.of(gatewayStarted, coordinatorStarted), extraStarted.stream())
                .toArray(CompletableFuture[]::new))
        .join();

    clientApi =
        new ClusterConfigurationManagementRequestSender(
            gateway.getCommunicationService(),
            ClusterConfigurationCoordinatorSupplier.of(
                () -> recordingCoordinator.getClusterConfiguration().join()),
            new ProtoBufSerializer());

    final var validatorRegistry = new RequestValidatorRegistry();
    validatorRegistry.registerValidator(
        null,
        new ClusterConfigurationRequestValidator<RestoreRequest, RestoreRequest>() {
          @Override
          public Class<RestoreRequest> requestType() {
            return RestoreRequest.class;
          }

          @Override
          public Either<Exception, RestoreRequest> validate(final RestoreRequest request) {
            return Either.right(request);
          }
        });

    requestServer =
        new ClusterConfigurationRequestServer(
            coordinator.getCommunicationService(),
            new ProtoBufSerializer(),
            new ClusterConfigurationManagementRequestsHandler(
                recordingCoordinator,
                coordinatorId,
                new TestConcurrencyControl(),
                validatorRegistry));
    requestServer.start();

    // extra nodes may be resolved as the coordinator for a given request (see
    // extraPhysicalMembers), so they need their own request server too
    extraRequestServers =
        extraClusters.stream()
            .map(
                entry ->
                    new ClusterConfigurationRequestServer(
                        entry.getValue().getCommunicationService(),
                        new ProtoBufSerializer(),
                        new ClusterConfigurationManagementRequestsHandler(
                            recordingCoordinator,
                            entry.getKey(),
                            new TestConcurrencyControl(),
                            validatorRegistry)))
            .toList();
    extraRequestServers.forEach(ClusterConfigurationRequestServer::start);
  }

  @AfterEach
  void tearDown() {
    requestServer.close();
    extraRequestServers.forEach(ClusterConfigurationRequestServer::close);
    gateway.stop();
    coordinator.stop();
    extraNodes.forEach(AtomixCluster::stop);
  }

  /**
   * Extra physical broker nodes to start alongside the coordinator, so that {@code
   * communicationService} can route requests to a member other than {@link #coordinatorId}. Used by
   * tests where the coordinator resolved at request time (e.g. force-remove-zone routing around the
   * removed zone) differs from the physical coordinator node.
   */
  protected List<MemberId> extraPhysicalMembers() {
    return List.of();
  }

  /**
   * Builds the physical cluster member for {@code localId}. Atomix's own {@link
   * io.atomix.cluster.Member} validates that the zone it was configured with matches the zone
   * embedded in the {@link MemberId}, so a zone-aware {@code localId} (e.g. {@code zone-a_0})
   * requires setting the zone explicitly on the {@link ClusterConfig}; {@code AtomixClusterBuilder}
   * has no public setter for it.
   */
  private AtomixCluster createClusterNode(
      final MemberId localId, final Node localNode, final Collection<Node> nodes) {
    final var clusterConfig = new ClusterConfig();
    clusterConfig.getNodeConfig().setId(localId).setZoneId(localId.zone());
    return AtomixCluster.builder(clusterConfig, registry)
        .withAddress(localNode.address())
        .withMembershipProvider(new BootstrapDiscoveryProvider(nodes))
        .withMembershipProtocol(new DiscoveryMembershipProtocol())
        .build();
  }

  @Test
  void shouldGetCurrentTopology() {
    // given
    final var expectedTopology =
        initialTopology.addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()));
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
        new ClusterConfigurationManagementRequest.AddMembersRequest(
            Set.of(memberFactory.apply(1)), false);

    // when
    final var changeStatus = clientApi.addMembers(request).join().get();

    // then
    final var expected = new MemberJoinOperation(memberFactory.apply(1));
    assertThat(changeStatus.plannedChanges()).containsExactly(expected);
  }

  @Test
  void shouldRemoveMembers() {
    // given
    recordingCoordinator.setCurrentTopology(
        initialTopology
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(2), MemberState.initializeAsActive(Map.of())));
    final var request =
        new ClusterConfigurationManagementRequest.RemoveMembersRequest(
            Set.of(memberFactory.apply(1), memberFactory.apply(2)), false);

    // when
    final var changeStatus = clientApi.removeMembers(request).join().get();

    // then
    final List<ClusterConfigurationChangeOperation> expected =
        List.of(
            new MemberLeaveOperation(memberFactory.apply(1)),
            new MemberLeaveOperation(memberFactory.apply(2)));
    assertThat(changeStatus.plannedChanges()).containsExactlyElementsOf(expected);
  }

  @Test
  void shouldJoinPartition() {
    // given
    final var request =
        new ClusterConfigurationManagementRequest.JoinPartitionRequest(
            memberFactory.apply(1), 1, 3, false);

    // when
    final var changeStatus = clientApi.joinPartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionJoinOperation(memberFactory.apply(1), 1, 3));
  }

  @Test
  void shouldLeavePartition() {
    // given
    final var request =
        new ClusterConfigurationManagementRequest.LeavePartitionRequest(
            memberFactory.apply(1), 1, false);

    // when
    final var changeStatus = clientApi.leavePartition(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(new PartitionLeaveOperation(memberFactory.apply(1), 1, 1));
  }

  @Test
  void shouldReassignPartitions() {
    // given
    final var request =
        new ClusterConfigurationManagementRequest.ReassignPartitionsRequest(
            Set.of(memberFactory.apply(1), memberFactory.apply(2)), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .addMember(
                memberFactory.apply(1),
                MemberState.initializeAsActive(
                    Map.of(
                        1,
                        PartitionState.active(1, partitionConfig),
                        2,
                        PartitionState.active(1, partitionConfig))))
            .addMember(memberFactory.apply(2), MemberState.initializeAsActive(Map.of()));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.reassignPartitions(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionJoinOperation(memberFactory.apply(2), 2, 1),
            new PartitionLeaveOperation(memberFactory.apply(1), 2, 1));
  }

  @Test
  void shouldScaleBrokers() {
    // given
    final var request =
        new BrokerScaleRequest(Set.of(memberFactory.apply(0), memberFactory.apply(1)), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))),
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 1),
            new PartitionLeaveOperation(memberFactory.apply(0), 2, 1),
            new PostScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))));
  }

  @Test
  void shouldScaleBrokersWithNewReplicationFactor() {
    // given
    final var request =
        new BrokerScaleRequest(
            Set.of(memberFactory.apply(0), memberFactory.apply(1)), Optional.of(2), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .hasSize(6)
        .startsWith(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))))
        .endsWith(
            new PostScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))))
        .contains(
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 2))
        .containsSequence(
            new PartitionJoinOperation(memberFactory.apply(1), 1, 1),
            new PartitionReconfigurePriorityOperation(memberFactory.apply(0), 1, 2));
  }

  @Test
  void shouldRejectScaleRequestWithInvalidReplicationFactor() {
    // given
    final var request =
        new BrokerScaleRequest(
            Set.of(memberFactory.apply(0), memberFactory.apply(1)), Optional.of(0), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

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
        new BrokerScaleRequest(
            Set.of(memberFactory.apply(0), memberFactory.apply(1)), Optional.of(1), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionLeaveOperation(memberFactory.apply(0), 2, 1),
            new PartitionLeaveOperation(memberFactory.apply(1), 1, 1),
            new PartitionReconfigurePriorityOperation(memberFactory.apply(0), 1, 1),
            new PartitionReconfigurePriorityOperation(memberFactory.apply(1), 2, 1));
  }

  @Test
  void shouldForceScaleDown() {
    // given
    final var request =
        new BrokerScaleRequest(Set.of(memberFactory.apply(0), memberFactory.apply(2)), false);
    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(memberFactory.apply(0), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(2), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(3), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                memberFactory.apply(2),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(3),
                m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.forceScaleDown(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(
                memberFactory.apply(0), 1, Set.of(memberFactory.apply(0))),
            new PartitionForceReconfigureOperation(
                memberFactory.apply(2), 2, Set.of(memberFactory.apply(2))),
            new MemberRemoveOperation(memberFactory.apply(0), memberFactory.apply(1)),
            new MemberRemoveOperation(memberFactory.apply(0), memberFactory.apply(3)));
  }

  @Test
  void shouldScaleClusterByNewClusterSizeAndPartitionCount() {
    // given
    final var request =
        new ClusterScaleRequest(
            Optional.of(2),
            Optional.of(3),
            Optional.empty(),
            Optional.ofNullable(coordinatorId.zone()),
            false);
    final var topologyWithPartitions =
        initialTopology
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));
    final ClusterConfiguration currentTopology =
        Optional.ofNullable(coordinatorId.zone())
            .map(
                zone ->
                    topologyWithPartitions.setPartitionDistributorConfig(
                        new ZoneAwareConfig(List.of(new ZoneSpec(zone, 1, 1)))))
            .orElse(topologyWithPartitions);

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleCluster(request).join();
    EitherAssert.assertThat(changeStatus).isRight();

    // then
    assertThat(changeStatus.get().plannedChanges())
        .containsExactly(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))),
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 1),
            new PartitionLeaveOperation(memberFactory.apply(0), 2, 1),
            new StartPartitionScaleUp(memberFactory.apply(0), 3),
            new PartitionBootstrapOperation(memberFactory.apply(0), 3, 1, true),
            new AwaitRedistributionCompletion(memberFactory.apply(0), 3, new TreeSet<>(List.of(3))),
            new AwaitRelocationCompletion(memberFactory.apply(0), 3, new TreeSet<>(List.of(3))),
            new PostScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))));
  }

  @Test
  void shouldPatchCluster() {
    // given
    final var request =
        new ClusterPatchRequest(
            Set.of(memberFactory.apply(1)), Set.of(), Optional.of(3), Optional.empty(), false);
    final ClusterConfiguration currentTopology =
        initialTopology
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)));

    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.patchCluster(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))),
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 1),
            new PartitionLeaveOperation(memberFactory.apply(0), 2, 1),
            new StartPartitionScaleUp(memberFactory.apply(0), 3),
            new PartitionBootstrapOperation(memberFactory.apply(0), 3, 1, true),
            new AwaitRedistributionCompletion(memberFactory.apply(0), 3, new TreeSet<>(List.of(3))),
            new AwaitRelocationCompletion(memberFactory.apply(0), 3, new TreeSet<>(List.of(3))),
            new PostScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))));
  }

  @Test
  void shouldForceRemoveBrokers() {
    // given
    final var request =
        new ForceRemoveBrokersRequest(
            Set.of(memberFactory.apply(1), memberFactory.apply(3)), false);
    final ClusterConfiguration currentTopology =
        ClusterConfiguration.init()
            .addMember(memberFactory.apply(0), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(2), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(3), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                memberFactory.apply(2),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(3),
                m -> m.addPartition(2, PartitionState.active(2, partitionConfig)));
    recordingCoordinator.setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.forceRemoveBrokers(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(
                memberFactory.apply(0), 1, Set.of(memberFactory.apply(0))),
            new PartitionForceReconfigureOperation(
                memberFactory.apply(2), 2, Set.of(memberFactory.apply(2))),
            new MemberRemoveOperation(memberFactory.apply(0), memberFactory.apply(1)),
            new MemberRemoveOperation(memberFactory.apply(0), memberFactory.apply(3)));
  }

  @Test
  void shouldForceRemoveZone() {
    // given
    // memberFactory.apply(0) is a bare (non-zoned) member so that the request is routed to the
    // coordinator that the
    // test's real communicationService actually knows about; the zone members below are the ones
    // exercised by the force-remove-zone logic itself.
    final var zoneA0 = MemberId.from("zone-a", 0);
    final var zoneA1 = MemberId.from("zone-a", 1);
    final var zoneB0 = MemberId.from("zone-b", 0);
    final var zoneB1 = MemberId.from("zone-b", 1);
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(memberFactory.apply(0), MemberState.initializeAsActive(Map.of()))
            .addMember(zoneA0, MemberState.initializeAsActive(Map.of()))
            .addMember(zoneA1, MemberState.initializeAsActive(Map.of()))
            .addMember(zoneB0, MemberState.initializeAsActive(Map.of()))
            .addMember(zoneB1, MemberState.initializeAsActive(Map.of()))
            .updateMember(zoneB0, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(zoneA0, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(zoneB1, m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(zoneA1, m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .setPartitionDistributorConfig(
                new ZoneAwareConfig(
                    List.of(new ZoneSpec("zone-a", 2, 1), new ZoneSpec("zone-b", 2, 2))));
    recordingCoordinator.setCurrentTopology(currentTopology);
    final var request = new ForceZoneRemoveRequest("zone-a", false);

    // when
    final var changeStatus = clientApi.forceRemoveZone(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionForceReconfigureOperation(zoneB0, 1, Set.of(zoneB0)),
            new PartitionForceReconfigureOperation(zoneB1, 2, Set.of(zoneB1)),
            new MemberRemoveOperation(memberFactory.apply(0), zoneA0),
            new MemberRemoveOperation(memberFactory.apply(0), zoneA1),
            new UpdatePartitionDistributorConfigOperation(
                memberFactory.apply(0),
                new ZoneAwareConfig(List.of(new ZoneSpec("zone-b", 2, 2)))));
  }

  @Test
  void shouldAddZone() {
    // given
    // memberFactory.apply(0) and memberFactory.apply(1) are bare (non-zoned) members so that the
    // request is routed to the coordinator
    // that the test's real communicationService actually knows about; the cluster is mid zone
    // migration, with a zone-aware distribution config persisted but brokers not yet re-tagged.
    final var zoneB0 = MemberId.from("zone-b", 0);
    final var currentTopology =
        ClusterConfiguration.init()
            .addMember(memberFactory.apply(0), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .setPartitionDistributorConfig(
                new ZoneAwareConfig(List.of(new ZoneSpec("zone-a", 1, 1))));
    recordingCoordinator.setCurrentTopology(currentTopology);
    final var request = new AddZoneRequest("zone-b", 1, 2, Set.of(zoneB0), false);

    // when
    final var changeStatus = clientApi.addZone(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges()).contains(new MemberJoinOperation(zoneB0));
    assertThat(changeStatus.plannedChanges())
        .filteredOn(UpdatePartitionDistributorConfigOperation.class::isInstance)
        .extracting(op -> ((UpdatePartitionDistributorConfigOperation) op).config())
        .containsExactly(
            new ZoneAwareConfig(
                List.of(new ZoneSpec("zone-a", 1, 1), new ZoneSpec("zone-b", 1, 2))));
  }

  @Test
  void shouldDisableExporter() {
    // given
    final String exporterId = "exporterId";
    final var request =
        new ClusterConfigurationManagementRequest.ExporterDisableRequest(exporterId, false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            memberFactory.apply(0),
            m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    recordingCoordinator.setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.disableExporter(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionDisableExporterOperation(memberFactory.apply(0), 1, exporterId));
  }

  @Test
  void shouldDeleteExporter() {
    // given
    final String exporterId = "exporterId";
    final var request =
        new ClusterConfigurationManagementRequest.ExporterDeleteRequest(exporterId, false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            memberFactory.apply(0),
            m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    recordingCoordinator.setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.deleteExporter(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionDeleteExporterOperation(memberFactory.apply(0), 1, exporterId));
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
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.DISABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            memberFactory.apply(0),
            m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    recordingCoordinator.setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.enableExporter(request).join().get();

    // then
    assertThat(changeStatus.plannedChanges())
        .containsExactly(
            new PartitionEnableExporterOperation(
                memberFactory.apply(0), 1, exporterId, Optional.empty()));
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
  void shouldValidateRestoreWhenClusterRecovering() {
    // given
    recordingCoordinator.setCurrentTopology(
        ClusterConfiguration.init()
            .addMember(
                memberFactory.apply(0), MemberState.initializeAsActive(Map.of()).toRecovering()));
    final var request =
        new RestoreRequest(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
            List.of(100L, 101L),
            null,
            null,
            "elasticsearch",
            false,
            false);

    // when
    final var result = clientApi.restore(request).join();

    // then
    EitherAssert.assertThat(result).isRight();
  }

  @Test
  void shouldRejectRestoreWhenClusterNotRecovering() {
    // given
    recordingCoordinator.setCurrentTopology(initialTopology); // member is ACTIVE
    final var request =
        new RestoreRequest(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
            List.of(100L),
            null,
            null,
            "elasticsearch",
            false,
            false);

    // when
    final var result = clientApi.restore(request).join();

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .satisfies(
            error -> {
              assertThat(error.code()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
              assertThat(error.message())
                  .isEqualTo("Restore is only allowed while the cluster is in recovery mode.");
            });
  }

  @Test
  void shouldPurgeCluster() {
    // given
    recordingCoordinator.setCurrentTopology(
        initialTopology
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(2), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(0, PartitionState.active(2, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(0, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(2),
                m -> m.addPartition(0, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                memberFactory.apply(2),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));
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
