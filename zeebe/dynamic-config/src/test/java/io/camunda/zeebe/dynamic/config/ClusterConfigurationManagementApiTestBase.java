/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddZoneRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.BrokerScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterPatchRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ClusterScaleRequest;
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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreResolvedRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdatePartitionDistributorConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.UpdateRoutingStateRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestsHandler;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestServer;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationRequestValidator;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeCoordinatorImpl;
import io.camunda.zeebe.dynamic.config.changes.GlobalConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopClusterMembershipChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionGroupConfigurationChangeAppliersImpl;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.RestoreChangeExecutor.NoopRestoreChangeExecutor;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.BrokerPartitionState;
import io.camunda.zeebe.dynamic.config.state.BrokerState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
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
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.RoundRobinConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneAwareConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionDistributorConfig.ZoneSpec;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionBootstrapOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDeleteExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDemoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionDisableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionEnableExporterOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPromoteOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRedistributionCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.AwaitRelocationCompletion;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ScaleUpOperation.StartPartitionScaleUp;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateRoutingState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.RoutingState;
import io.camunda.zeebe.dynamic.config.util.RequestValidatorRegistry;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.test.util.asserts.EitherAssert;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
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
import org.junit.jupiter.api.io.TempDir;

/**
 * Drives every {@link io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementApi}
 * endpoint through the real request handler, {@link ConfigurationChangeCoordinatorImpl} and {@link
 * ClusterConfigurationManagerImpl}, over the real {@code communicationService} â€” i.e. against the
 * new multi-partition-group model end to end, the same stack production runs.
 */
abstract class ClusterConfigurationManagementApiTestBase {
  private static final String TENANT_B = "tenant-b";

  protected final MemberId coordinatorId;
  protected ClusterConfigurationManagementRequestSender clientApi;
  protected final DynamicPartitionConfig partitionConfig = DynamicPartitionConfig.init();
  protected ClusterConfigurationManagerImpl manager;
  private final ClusterConfiguration initialTopology;
  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final Function<Integer, MemberId> memberFactory;
  private ClusterConfigurationRequestServer requestServer;
  private List<ClusterConfigurationRequestServer> extraRequestServers = List.of();
  private AtomixCluster gateway;
  private AtomixCluster coordinator;
  private List<AtomixCluster> extraNodes = List.of();
  @AutoClose private final MeterRegistry registry = new SimpleMeterRegistry();
  @TempDir private Path tmp;

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
    final var persistedConfiguration =
        PersistedCurrentClusterConfiguration.ofFile(
            tmp.resolve("config.meta"), new ProtoBufSerializer());
    manager =
        new ClusterConfigurationManagerImpl(
            executor,
            coordinatorId,
            persistedConfiguration,
            new TopologyManagerMetrics(registry),
            Duration.ofMillis(1),
            Duration.ofMillis(1));
    manager.setCurrentConfigurationGossiper(ignored -> {});
    manager.registerGlobalChangeAppliers(
        new GlobalConfigurationChangeAppliersImpl(
            new NoopClusterMembershipChangeExecutor(), new NoopClusterChangeExecutor()));
    manager.registerPartitionGroupChangeAppliers(
        CurrentClusterConfiguration.DEFAULT_GROUP,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
    // seed the manager so that the "no topology yet" default still resolves to the physical
    // coordinator node, rather than falling back to member "0"
    setCurrentTopology(initialTopology);

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
            ClusterConfigurationCoordinatorSupplier.from(
                () -> manager.getMultiConfiguration().join()),
            new ProtoBufSerializer(),
            gateway.getMembershipService().getLocalMember().id());

    final var validatorRegistry = new RequestValidatorRegistry();
    validatorRegistry.registerValidator(
        null,
        new ClusterConfigurationRequestValidator<RestoreRequest, RestoreResolvedRequest>() {
          @Override
          public Class<RestoreRequest> requestType() {
            return RestoreRequest.class;
          }

          /** Resolves the requested backups on partition 1, the only partition these tests use. */
          @Override
          public Either<Exception, RestoreResolvedRequest> validate(final RestoreRequest request) {
            final var backupIds =
                request.arguments().parameters().backupIds().stream()
                    .mapToLong(Long::longValue)
                    .toArray();
            return Either.right(new RestoreResolvedRequest(Map.of(1, backupIds), false));
          }
        });

    requestServer =
        new ClusterConfigurationRequestServer(
            coordinator.getCommunicationService(),
            new ProtoBufSerializer(),
            new ClusterConfigurationManagementRequestsHandler(
                new ConfigurationChangeCoordinatorImpl(manager, coordinatorId, executor),
                coordinatorId,
                executor,
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
                            new ConfigurationChangeCoordinatorImpl(
                                manager, entry.getKey(), executor),
                            entry.getKey(),
                            executor,
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

  /** Seeds the manager's configuration, as if a coordinator's gossip round had produced it. */
  protected final void setCurrentTopology(final ClusterConfiguration topology) {
    manager
        .updateMultiConfiguration(ignored -> CurrentClusterConfiguration.fromLegacy(topology))
        .join();
  }

  /**
   * Initializes the default group's routing state, a precondition {@link
   * io.camunda.zeebe.dynamic.config.changes.appliers.StartPartitionScaleUpApplier} enforces before
   * a partition-count scale-up can proceed.
   */
  protected final void initializeRoutingState(final int partitionCount) {
    clientApi
        .updateRoutingState(
            new UpdateRoutingStateRequest(
                Optional.of(RoutingState.initializeWithPartitionCount(partitionCount)),
                Optional.empty(),
                false))
        .join()
        .get();
  }

  @Test
  void shouldGetCurrentTopology() {
    // given
    final var expectedTopology =
        initialTopology.addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()));
    setCurrentTopology(expectedTopology);

    // when
    final var topology = clientApi.getTopology().join();

    // then — member-key equality alone would miss a lifecycle-state or per-member metadata
    // regression, so every member's state is checked too; whole-object equality against
    // CurrentClusterConfiguration.fromLegacy(expectedTopology) isn't possible here because the
    // real coordinator bumps its own version counters on every update
    assertThat(topology.get().globalConfiguration().members())
        .containsOnlyKeys(coordinatorId, memberFactory.apply(1))
        .allSatisfy(
            (memberId, state) -> assertThat(state.state()).isEqualTo(BrokerState.State.ACTIVE));
  }

  @Test
  void shouldAddMembers() {
    // given
    final var request = new AddMembersRequest(Set.of(memberFactory.apply(1)), false);

    // when
    final var changeStatus = clientApi.addMembers(request).join().get();

    // then
    final var expected = new MemberJoinOperation(memberFactory.apply(1));
    assertThat(changeStatus.legacyResponse().plannedChanges()).containsExactly(expected);
  }

  @Test
  void shouldRemoveMembers() {
    // given
    setCurrentTopology(
        initialTopology
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(2), MemberState.initializeAsActive(Map.of())));
    final var request =
        new RemoveMembersRequest(Set.of(memberFactory.apply(1), memberFactory.apply(2)), false);

    // when
    final var changeStatus = clientApi.removeMembers(request).join().get();

    // then
    final List<ClusterConfigurationChangeOperation> expected =
        List.of(
            new MemberLeaveOperation(memberFactory.apply(1)),
            new MemberLeaveOperation(memberFactory.apply(2)));
    assertThat(changeStatus.legacyResponse().plannedChanges()).containsExactlyElementsOf(expected);
  }

  @Test
  void shouldJoinPartition() {
    // given — partition 1 already has an active member (the coordinator), so member 1 can join it
    setCurrentTopology(
        initialTopology
            .updateMember(
                coordinatorId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of())));
    final var request =
        new JoinPartitionRequest(memberFactory.apply(1), 1, 3, Optional.empty(), false);

    // when
    final var changeStatus = clientApi.joinPartition(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new PartitionJoinOperation(memberFactory.apply(1), 1, 3, true),
            new PartitionPromoteOperation(memberFactory.apply(1), 1));
  }

  @Test
  void shouldLeavePartition() {
    // given — partition 1 has two replicas so member 1 can leave without dropping below the
    // minimum
    setCurrentTopology(
        initialTopology
            .updateMember(
                coordinatorId, m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .addMember(
                memberFactory.apply(1),
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig)))));
    final var request =
        new LeavePartitionRequest(memberFactory.apply(1), 1, Optional.empty(), false);

    // when
    final var changeStatus = clientApi.leavePartition(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new PartitionDemoteOperation(memberFactory.apply(1), 1),
            new PartitionLeaveOperation(memberFactory.apply(1), 1, 1));
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

    setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))),
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 1, true),
            new PartitionPromoteOperation(memberFactory.apply(1), 2),
            new PartitionDemoteOperation(memberFactory.apply(0), 2),
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

    setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .hasSize(8)
        .startsWith(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))))
        .endsWith(
            new PostScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))))
        .contains(
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 2, true),
            new PartitionPromoteOperation(memberFactory.apply(1), 2))
        .containsSequence(
            new PartitionJoinOperation(memberFactory.apply(1), 1, 1, true),
            new PartitionPromoteOperation(memberFactory.apply(1), 1),
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

    setCurrentTopology(currentTopology);

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

    setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.scaleMembers(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactlyInAnyOrder(
            new PartitionDemoteOperation(memberFactory.apply(0), 2),
            new PartitionLeaveOperation(memberFactory.apply(0), 2, 1),
            new PartitionDemoteOperation(memberFactory.apply(1), 1),
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
    setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.forceScaleDown(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
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

    setCurrentTopology(currentTopology);
    initializeRoutingState(2);

    // when
    final var changeStatus = clientApi.scaleCluster(request).join();
    EitherAssert.assertThat(changeStatus).isRight();

    // then
    assertThat(changeStatus.get().legacyResponse().plannedChanges())
        .containsExactly(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))),
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 1, true),
            new PartitionPromoteOperation(memberFactory.apply(1), 2),
            new PartitionDemoteOperation(memberFactory.apply(0), 2),
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

    setCurrentTopology(currentTopology);
    initializeRoutingState(2);

    // when
    final var changeStatus = clientApi.patchCluster(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new PreScalingOperation(
                memberFactory.apply(0), Set.of(memberFactory.apply(0), memberFactory.apply(1))),
            new MemberJoinOperation(memberFactory.apply(1)),
            new PartitionJoinOperation(memberFactory.apply(1), 2, 1, true),
            new PartitionPromoteOperation(memberFactory.apply(1), 2),
            new PartitionDemoteOperation(memberFactory.apply(0), 2),
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
    setCurrentTopology(currentTopology);

    // when
    final var changeStatus = clientApi.forceRemoveBrokers(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
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
    setCurrentTopology(currentTopology);
    final var request = new ForceZoneRemoveRequest("zone-a", false);

    // when
    final var changeStatus = clientApi.forceRemoveZone(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
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
    setCurrentTopology(currentTopology);
    final var request = new AddZoneRequest("zone-b", 1, 2, Set.of(zoneB0), false);

    // when
    final var changeStatus = clientApi.addZone(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .contains(new MemberJoinOperation(zoneB0));
    assertThat(changeStatus.legacyResponse().plannedChanges())
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
    final var request = new ExporterDisableRequest(exporterId, Optional.empty(), false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            memberFactory.apply(0),
            m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.disableExporter(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new PartitionDisableExporterOperation(memberFactory.apply(0), 1, exporterId));
  }

  @Test
  void shouldApplyDisableExporterEndToEndForLocalMember() {
    // given — the exporter lives on the local (coordinator) member's partition, so the plan
    // drains synchronously against the no-op appliers
    final String exporterId = "exporterId";
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.ENABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            coordinatorId,
            m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    setCurrentTopology(configurationWithExporter);
    final var request = new ExporterDisableRequest(exporterId, Optional.empty(), false);

    // when
    clientApi.disableExporter(request).join().get();

    // then — the plan is applied on the local member and completes
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    final var exporterState =
        config
            .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
            .getMember(coordinatorId)
            .partitions()
            .get(1)
            .config()
            .exporting()
            .exporters()
            .get(exporterId);
    assertThat(exporterState.state()).isEqualTo(State.DISABLED);
  }

  @Test
  void shouldDeleteExporter() {
    // given — the exporter is in CONFIG_NOT_FOUND, the state a delete requires
    final String exporterId = "exporterId";
    final var request = new ExporterDeleteRequest(exporterId, Optional.empty(), false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(
                    exporterId, new ExporterState(1, State.CONFIG_NOT_FOUND, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            memberFactory.apply(0),
            m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.deleteExporter(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new PartitionDeleteExporterOperation(memberFactory.apply(0), 1, exporterId));
  }

  @Test
  void shouldEnableExporter() {
    // given
    final String exporterId = "exporterId";
    final var request =
        new ExporterEnableRequest(exporterId, Optional.empty(), Optional.empty(), false);
    final var partitionConfigWithExporter =
        new DynamicPartitionConfig(
            new ExportingConfig(
                ExportingState.EXPORTING,
                Map.of(exporterId, new ExporterState(1, State.DISABLED, Optional.empty()))));
    final var configurationWithExporter =
        initialTopology.updateMember(
            memberFactory.apply(0),
            m -> m.addPartition(1, PartitionState.active(1, partitionConfigWithExporter)));
    setCurrentTopology(configurationWithExporter);

    // when
    final var changeStatus = clientApi.enableExporter(request).join().get();

    // then
    assertThat(changeStatus.legacyResponse().plannedChanges())
        .containsExactly(
            new PartitionEnableExporterOperation(
                memberFactory.apply(0), 1, exporterId, Optional.empty()));
  }

  @Test
  void shouldReturnInvalidErrorForInvalidRequests() {
    // given
    final var request = new BrokerScaleRequest(Set.of(), false); // invalid request when no brokers
    setCurrentTopology(initialTopology);

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
    // given — the coordinator must host a partition: recovery mode lives on BrokerPartitionState,
    // so a partitionless member is not in the default group and its mode would not survive the
    // round-trip through toLegacyDefault
    setCurrentTopology(
        ClusterConfiguration.init()
            .addMember(
                coordinatorId,
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1, partitionConfig)))
                    .toRecovering()));
    final var request =
        new RestoreRequest(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
            new TenantRestoreArguments(
                new RestoreParameters(List.of(100L, 101L), null, null), "elasticsearch", false),
            false);

    // when
    final var result = clientApi.restore(request).join();

    // then
    EitherAssert.assertThat(result).isRight();
  }

  @Test
  void shouldValidateRestoreAsDryRunWhenClusterRecovering() {
    // given — same fixture as shouldValidateRestoreWhenClusterRecovering, but this drives the
    // coordinator's simulateOperations() path (dryRun=true) instead of applyOperations(): the
    // plan is validated/simulated against the real appliers but never actually applied
    setCurrentTopology(
        ClusterConfiguration.init()
            .addMember(
                coordinatorId,
                MemberState.initializeAsActive(Map.of(1, PartitionState.active(1, partitionConfig)))
                    .toRecovering()));
    final var request =
        new RestoreRequest(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
            new TenantRestoreArguments(
                new RestoreParameters(List.of(100L, 101L), null, null), "elasticsearch", false),
            true);

    // when
    final var result = clientApi.restore(request).join();

    // then — accepted, because toLegacyDefault projects the coordinator back to RECOVERING
    EitherAssert.assertThat(result).isRight();
  }

  @Test
  void shouldRejectRestoreWhenClusterNotRecovering() {
    // given
    setCurrentTopology(initialTopology); // member is ACTIVE
    final var request =
        new RestoreRequest(
            PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID,
            new TenantRestoreArguments(
                new RestoreParameters(List.of(100L), null, null), "elasticsearch", false),
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
                  .isEqualTo(
                      "Restore is only allowed while physical tenant 'default' is in recovery mode.");
            });
  }

  @Test
  void shouldPurgeCluster() {
    // given
    setCurrentTopology(
        initialTopology
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .addMember(memberFactory.apply(2), MemberState.initializeAsActive(Map.of()))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(1, PartitionState.active(2, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(2),
                m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(0),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig)))
            .updateMember(
                memberFactory.apply(1),
                m -> m.addPartition(2, PartitionState.active(2, partitionConfig)))
            .updateMember(
                memberFactory.apply(2),
                m -> m.addPartition(2, PartitionState.active(1, partitionConfig))));
    final var request = new PurgeRequest(Optional.empty(), false);

    // when
    final var changeStatus = clientApi.purge(request).join().get();

    // then
    final var currentConfiguration =
        changeStatus.legacyResponse().currentConfiguration().values().stream()
            .map(MemberState::partitions)
            .collect(Collectors.toSet());
    final var expectedConfiguration =
        changeStatus.legacyResponse().expectedConfiguration().values().stream()
            .map(MemberState::partitions)
            .collect(Collectors.toSet());

    assertThat(currentConfiguration).containsExactlyElementsOf(expectedConfiguration);
  }

  @Test
  void shouldRejectUpdatePartitionDistributionForNonZoneAwareConfig() {
    // given — a non-zone-aware config, which the transformer rejects regardless of coordinator
    // shape
    setCurrentTopology(
        initialTopology.updateMember(
            coordinatorId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));
    final var request = new UpdatePartitionDistributorConfigRequest(new RoundRobinConfig(), false);

    // when
    final var result = clientApi.updatePartitionDistribution(request).join();

    // then
    EitherAssert.assertThat(result)
        .isLeft()
        .left()
        .satisfies(error -> assertThat(error.message()).contains("ZONE_AWARE"));
  }

  @Test
  void shouldCancelTopologyChangeEndToEnd() {
    // given — a pending plan whose operation targets a member other than the coordinator, so it
    // never drains on its own
    final var otherMember = memberFactory.apply(1);
    setCurrentTopology(
        initialTopology
            .addMember(otherMember, MemberState.initializeAsActive(Map.of()))
            .updateMember(
                coordinatorId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));
    manager
        .updateMultiConfiguration(
            c ->
                c.initPlan(
                    List.of(new GlobalPhase(List.of(new MemberLeaveOperation(otherMember))))))
        .join();
    final var changeId =
        manager.getMultiConfiguration().join().phasedChangeState().onlyPending().id();

    // when — cancelled via the management API endpoint
    final var response = clientApi.cancelTopologyChange(new CancelChangeRequest(changeId)).join();

    // then — no pending change remains, on both the response and the real configuration
    EitherAssert.assertThat(response).isRight();
    assertThat(response.get().phasedChangeState().pending()).isEmpty();
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.phasedChangeState().lastChange())
        .hasValueSatisfying(
            last -> assertThat(last.status()).isEqualTo(PhasedChangePlanStatus.CANCELLED));
  }

  @Test
  void shouldApplyModeChangeEndToEndForLocalMember() {
    // given — the coordinator is active and processing in the default group
    setCurrentTopology(
        ClusterConfiguration.init()
            .addMember(
                coordinatorId,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when — switch to recovery mode
    clientApi
        .modeChange(
            new ModeChangeRequest(
                Optional.of(CurrentClusterConfiguration.DEFAULT_GROUP), Mode.RECOVERING, false))
        .join()
        .get();

    // then — the plan is applied and the coordinator is now in recovery mode
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(
            config
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .getMember(coordinatorId)
                .mode())
        .isEqualTo(Mode.RECOVERING);
  }

  @Test
  void shouldApplyModeChangeToEveryPhysicalTenantInOnePlan() {
    // given
    wireTwoPhysicalTenants();

    // when
    clientApi
        .modeChange(new ModeChangeRequest(Optional.empty(), Mode.RECOVERING, false))
        .join()
        .get();

    // then
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(
            config
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .getMember(coordinatorId)
                .mode())
        .isEqualTo(Mode.RECOVERING);
    assertThat(config.partitionGroup(TENANT_B).getMember(coordinatorId).mode())
        .isEqualTo(Mode.RECOVERING);
  }

  @Test
  void shouldApplyModeChangeToOnlyTheRequestedPhysicalTenant() {
    // given
    wireTwoPhysicalTenants();

    // when
    clientApi
        .modeChange(new ModeChangeRequest(Optional.of(TENANT_B), Mode.RECOVERING, false))
        .join()
        .get();

    // then
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.partitionGroup(TENANT_B).getMember(coordinatorId).mode())
        .isEqualTo(Mode.RECOVERING);
    assertThat(
            config
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .getMember(coordinatorId)
                .mode())
        .isEqualTo(Mode.PROCESSING);
  }

  @Test
  void shouldApplyExportingStateChangeEndToEndForLocalMember() {
    // given — the coordinator is active and exporting in the default group
    setCurrentTopology(
        ClusterConfiguration.init()
            .addMember(
                coordinatorId,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig)))));

    // when — pause exporting
    clientApi
        .changeExportingState(
            new ExportingStateChangeRequest(
                ExportingState.PAUSED,
                Optional.of(CurrentClusterConfiguration.DEFAULT_GROUP),
                false))
        .join()
        .get();

    // then — the plan is applied and the coordinator's partition is now paused
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(
            config
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .getMember(coordinatorId)
                .partitions()
                .get(1)
                .config()
                .exporting()
                .state())
        .isEqualTo(ExportingState.PAUSED);
  }

  @Test
  void shouldApplyExportingStateChangeToEveryPhysicalTenantInOnePlan() {
    // given
    wireTwoPhysicalTenants();

    // when
    clientApi
        .changeExportingState(
            new ExportingStateChangeRequest(ExportingState.PAUSED, Optional.empty(), false))
        .join()
        .get();

    // then
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(
            config
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .getMember(coordinatorId)
                .partitions()
                .get(1)
                .config()
                .exporting()
                .state())
        .isEqualTo(ExportingState.PAUSED);
    assertThat(
            config
                .partitionGroup(TENANT_B)
                .getMember(coordinatorId)
                .partitions()
                .get(1)
                .config()
                .exporting()
                .state())
        .isEqualTo(ExportingState.PAUSED);
  }

  @Test
  void shouldApplyExportingStateChangeToOnlyTheRequestedPhysicalTenant() {
    // given
    wireTwoPhysicalTenants();

    // when
    clientApi
        .changeExportingState(
            new ExportingStateChangeRequest(ExportingState.PAUSED, Optional.of(TENANT_B), false))
        .join()
        .get();

    // then
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(
            config
                .partitionGroup(TENANT_B)
                .getMember(coordinatorId)
                .partitions()
                .get(1)
                .config()
                .exporting()
                .state())
        .isEqualTo(ExportingState.PAUSED);
    assertThat(
            config
                .partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP)
                .getMember(coordinatorId)
                .partitions()
                .get(1)
                .config()
                .exporting()
                .state())
        .isEqualTo(ExportingState.UNKNOWN);
  }

  /**
   * Before every physical tenant was considered when planning a membership change, this request was
   * rejected during validation: the plan moved only the default tenant's partitions, so {@code
   * MemberLeaveApplier} — which checks every partition group — refused to let the departing member
   * leave while it still held the other tenant's partition. The whole request failed with "the
   * member still has partitions assigned", leaving the operator no way to remove the broker at all.
   */
  @Test
  void shouldMoveANonDefaultTenantsPartitionOffARemovedBroker() {
    // given — a third member that holds nothing but tenant-b's partition
    final var removedMember = memberFactory.apply(2);
    wireNonDefaultTenantOnLastMember(removedMember);

    // when — that member is removed from the cluster
    final var changeStatus =
        clientApi
            .patchCluster(
                new ClusterPatchRequest(
                    Set.of(), Set.of(removedMember), Optional.empty(), Optional.empty(), false))
            .join()
            .get();

    // then — the request is answered with a plan at all, which is the regression: validation
    // simulates every phase through the same appliers the manager uses, so a plan means
    // MemberLeaveApplier no longer refuses the leave
    final var expected = changeStatus.response().expectedConfiguration();
    assertThat(expected.getMembers()).doesNotContain(removedMember);
    assertThat(expected.partitionGroup(TENANT_B).members())
        .describedAs("tenant-b's partition is reassigned to a retained broker, not dropped")
        .isNotEmpty()
        .doesNotContainKey(removedMember);

    // and the partitions move before the member leaves, not after
    final var phases = changeStatus.response().phases();
    assertThat(phases).hasSize(3);
    assertThat(phases.get(1))
        .asInstanceOf(type(PartitionGroupPhase.class))
        .satisfies(
            phase -> {
              assertThat(phase.groupOperations()).containsKey(TENANT_B);
              assertThat(phase.groupOperations().get(TENANT_B))
                  .contains(new PartitionLeaveOperation(removedMember, 1, 1))
                  .anySatisfy(
                      operation ->
                          assertThat(operation)
                              .asInstanceOf(type(PartitionJoinOperation.class))
                              .extracting(PartitionJoinOperation::memberId)
                              .isNotEqualTo(removedMember));
            });
    assertThat(phases.get(2))
        .asInstanceOf(type(GlobalPhase.class))
        .satisfies(
            phase ->
                assertThat(phase.operations()).contains(new MemberLeaveOperation(removedMember)));
  }

  /**
   * Partition ids restart at 1 in every physical tenant, so before the request named a tenant this
   * join was planned into the default tenant's group — the operator asked for one tenant's
   * partition 1 and got another's.
   */
  @Test
  void shouldJoinAPartitionOfANonDefaultPhysicalTenant() {
    // given — both tenants run a partition numbered 1, held by the coordinator alone
    final var joiningMember = memberFactory.apply(1);
    wireTwoTenantsSharingPartitionOne(Set.of(coordinatorId), joiningMember);

    // when — the member joins partition 1 of tenant-b
    final var changeStatus =
        clientApi
            .joinPartition(
                new JoinPartitionRequest(joiningMember, 1, 3, Optional.of(TENANT_B), false))
            .join()
            .get();

    // then — it replicates tenant-b's partition 1, and the default tenant's identically numbered
    // partition is left alone
    final var expected = changeStatus.response().expectedConfiguration();
    assertThat(expected.partitionGroup(TENANT_B).members()).containsKey(joiningMember);
    assertThat(expected.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).members())
        .doesNotContainKey(joiningMember);
  }

  /**
   * The counterpart of {@link #shouldJoinAPartitionOfANonDefaultPhysicalTenant()}: an unscoped
   * leave used to be the only leave there was, so asking for another tenant's partition 1 removed
   * the replica from the default tenant instead — data loss on a tenant the operator never named.
   */
  @Test
  void shouldLeaveAPartitionOfANonDefaultPhysicalTenant() {
    // given — both tenants run a partition numbered 1, replicated by both members
    final var leavingMember = memberFactory.apply(1);
    wireTwoTenantsSharingPartitionOne(Set.of(coordinatorId, leavingMember), memberFactory.apply(2));

    // when — the member leaves partition 1 of tenant-b
    final var changeStatus =
        clientApi
            .leavePartition(
                new LeavePartitionRequest(leavingMember, 1, Optional.of(TENANT_B), false))
            .join()
            .get();

    // then — it stops replicating tenant-b's partition 1 and keeps the default tenant's
    final var expected = changeStatus.response().expectedConfiguration();
    assertThat(expected.partitionGroup(TENANT_B).members()).doesNotContainKey(leavingMember);
    assertThat(expected.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).members())
        .containsKey(leavingMember);
  }

  @Test
  void shouldRejectAJoinForAnUnknownPhysicalTenant() {
    // given
    final var joiningMember = memberFactory.apply(1);
    wireTwoTenantsSharingPartitionOne(Set.of(coordinatorId), joiningMember);

    // when
    final var changeStatus =
        clientApi
            .joinPartition(
                new JoinPartitionRequest(joiningMember, 1, 3, Optional.of("does-not-exist"), false))
            .join();

    // then
    EitherAssert.assertThat(changeStatus)
        .isLeft()
        .left()
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  @Test
  void shouldRejectALeaveForAnUnknownPhysicalTenant() {
    // given
    final var leavingMember = memberFactory.apply(1);
    wireTwoTenantsSharingPartitionOne(Set.of(coordinatorId, leavingMember), memberFactory.apply(2));

    // when
    final var changeStatus =
        clientApi
            .leavePartition(
                new LeavePartitionRequest(leavingMember, 1, Optional.of("does-not-exist"), false))
            .join();

    // then
    EitherAssert.assertThat(changeStatus)
        .isLeft()
        .left()
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.NOT_FOUND);
  }

  /**
   * Wires a cluster where the default tenant and {@link #TENANT_B} each run a partition numbered 1,
   * both replicated by exactly {@code replicas}. The two groups are deliberately identical: a
   * request that resolves the partition in the wrong group would still find a partition 1 there, so
   * only which group changed can tell the two apart.
   *
   * <p>{@code idleMember} joins the cluster without replicating anything, so it is available to
   * join either partition.
   *
   * <p>Left without a zone-aware distributor config, unlike {@link #wireTwoPhysicalTenants()}:
   * nothing here asks for a placement to be computed, and a zone spec sized for a single broker
   * could not describe this cluster.
   */
  private void wireTwoTenantsSharingPartitionOne(
      final Set<MemberId> replicas, final MemberId idleMember) {
    var topology = initialTopology.addMember(idleMember, MemberState.initializeAsActive(Map.of()));
    for (final var replica : replicas) {
      topology =
          topology.hasMember(replica)
              ? topology.updateMember(
                  replica, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
              : topology.addMember(
                  replica,
                  MemberState.initializeAsActive(
                      Map.of(1, PartitionState.active(1, partitionConfig))));
    }
    setCurrentTopology(topology);
    manager.registerPartitionGroupChangeAppliers(
        TENANT_B,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
    manager
        .updateMultiConfiguration(
            current ->
                new CurrentClusterConfiguration(
                    current.version(),
                    current.globalConfiguration(),
                    Map.of(
                        CurrentClusterConfiguration.DEFAULT_GROUP,
                        current.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP),
                        TENANT_B,
                        new PartitionGroupConfiguration(
                            1,
                            0,
                            replicas.stream()
                                .collect(
                                    Collectors.toMap(
                                        replica -> replica,
                                        replica ->
                                            BrokerPartitionState.initialize(
                                                Map.of(
                                                    1,
                                                    PartitionState.active(1, partitionConfig))))),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty())),
                    current.phasedChangeState()))
        .join();
  }

  /**
   * Wires a three-member cluster where the default tenant's only partition sits on {@link
   * #coordinatorId} and {@link #TENANT_B}'s only partition on {@code lastMember}, so that member
   * holds nothing but a non-default tenant's partition.
   *
   * <p>Deliberately left without a zone-aware distributor config, unlike {@link
   * #wireTwoPhysicalTenants()}: round robin places three members in either variant of this suite,
   * where a zone spec written for a single broker could not.
   */
  private void wireNonDefaultTenantOnLastMember(final MemberId lastMember) {
    setCurrentTopology(
        initialTopology
            .updateMember(
                coordinatorId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig)))
            .addMember(memberFactory.apply(1), MemberState.initializeAsActive(Map.of()))
            .addMember(lastMember, MemberState.initializeAsActive(Map.of())));
    manager.registerPartitionGroupChangeAppliers(
        TENANT_B,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
    manager
        .updateMultiConfiguration(
            current ->
                new CurrentClusterConfiguration(
                    current.version(),
                    current.globalConfiguration(),
                    Map.of(
                        CurrentClusterConfiguration.DEFAULT_GROUP,
                        current.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP),
                        TENANT_B,
                        new PartitionGroupConfiguration(
                            1,
                            0,
                            Map.of(
                                lastMember,
                                BrokerPartitionState.initialize(
                                    Map.of(1, PartitionState.active(1, partitionConfig)))),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty())),
                    current.phasedChangeState()))
        .join();
  }

  /**
   * Wires a cluster with two physical tenants — the default group and {@link #TENANT_B} — both
   * hosted by {@link #coordinatorId} and both processing.
   */
  private void wireTwoPhysicalTenants() {
    final var topology =
        ClusterConfiguration.init()
            .addMember(
                coordinatorId,
                MemberState.initializeAsActive(
                    Map.of(1, PartitionState.active(1, partitionConfig))));
    setCurrentTopology(
        Optional.ofNullable(coordinatorId.zone())
            .map(
                zone ->
                    topology.setPartitionDistributorConfig(
                        new ZoneAwareConfig(List.of(new ZoneSpec(zone, 1, 1)))))
            .orElse(topology));
    manager.registerPartitionGroupChangeAppliers(
        TENANT_B,
        new PartitionGroupConfigurationChangeAppliersImpl(
            new NoopPartitionChangeExecutor(),
            new NoopPartitionScalingChangeExecutor(),
            new NoopModeChangeExecutor(),
            new NoopRestoreChangeExecutor()));
    manager
        .updateMultiConfiguration(
            current ->
                new CurrentClusterConfiguration(
                    current.version(),
                    current.globalConfiguration(),
                    Map.of(
                        CurrentClusterConfiguration.DEFAULT_GROUP,
                        current.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP),
                        TENANT_B,
                        new PartitionGroupConfiguration(
                            1,
                            0,
                            Map.of(
                                coordinatorId,
                                BrokerPartitionState.initialize(
                                    Map.of(1, PartitionState.active(1, partitionConfig)))),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty())),
                    current.phasedChangeState()))
        .join();
  }

  /**
   * Seeds a routing state on each named group: {@code StartPartitionScaleUpApplier} refuses to
   * scale up a group whose routing state is not initialized yet, so the scale-up tests need it
   * seeded on both groups regardless of which one is actually targeted.
   */
  private void seedRoutingState(final int partitionCount, final String... groupIds) {
    manager
        .updateMultiConfiguration(
            current -> {
              var updated = current;
              for (final var groupId : groupIds) {
                updated =
                    updated.updatePartitionGroupConfig(
                        groupId,
                        group ->
                            group.setRoutingState(
                                RoutingState.initializeWithPartitionCount(partitionCount)));
              }
              return updated;
            })
        .join();
  }

  @Test
  void shouldApplyScaleClusterToOnlyTheRequestedPhysicalTenant() {
    // given — both physical tenants start with 1 partition
    wireTwoPhysicalTenants();
    seedRoutingState(1, CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_B);

    // when — scale only tenant-b's partition count up to 2
    clientApi
        .scaleCluster(
            new ClusterScaleRequest(
                Optional.empty(),
                Optional.of(2),
                Optional.empty(),
                Optional.ofNullable(coordinatorId.zone()),
                Optional.of(TENANT_B),
                false))
        .join()
        .get();

    // then — only tenant-b's partition count changed; the default group is untouched
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.partitionGroup(TENANT_B).partitionCount()).isEqualTo(2);
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).partitionCount())
        .isEqualTo(1);
  }

  @Test
  void shouldApplyScaleClusterToOnlyTheDefaultTenantWhenUnscoped() {
    // given — both physical tenants start with 1 partition
    wireTwoPhysicalTenants();
    seedRoutingState(1, CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_B);

    // when — no physicalTenant parameter is given
    clientApi
        .scaleCluster(
            new ClusterScaleRequest(
                Optional.empty(),
                Optional.of(2),
                Optional.empty(),
                Optional.ofNullable(coordinatorId.zone()),
                false))
        .join()
        .get();

    // then — only the default tenant's partition count changed
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).partitionCount())
        .isEqualTo(2);
    assertThat(config.partitionGroup(TENANT_B).partitionCount()).isEqualTo(1);
  }

  @Test
  void shouldApplyPatchClusterToOnlyTheRequestedPhysicalTenant() {
    // given — both physical tenants start with 1 partition
    wireTwoPhysicalTenants();
    seedRoutingState(1, CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_B);

    // when — patch only tenant-b's partition count up to 2
    clientApi
        .patchCluster(
            new ClusterPatchRequest(
                Set.of(), Set.of(), Optional.of(2), Optional.empty(), Optional.of(TENANT_B), false))
        .join()
        .get();

    // then — only tenant-b's partition count changed; the default group is untouched
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.partitionGroup(TENANT_B).partitionCount()).isEqualTo(2);
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).partitionCount())
        .isEqualTo(1);
  }

  @Test
  void shouldApplyPatchClusterToOnlyTheDefaultTenantWhenUnscoped() {
    // given — both physical tenants start with 1 partition
    wireTwoPhysicalTenants();
    seedRoutingState(1, CurrentClusterConfiguration.DEFAULT_GROUP, TENANT_B);

    // when — no physicalTenant parameter is given
    clientApi
        .patchCluster(
            new ClusterPatchRequest(Set.of(), Set.of(), Optional.of(2), Optional.empty(), false))
        .join()
        .get();

    // then — only the default tenant's partition count changed
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).partitionCount())
        .isEqualTo(2);
    assertThat(config.partitionGroup(TENANT_B).partitionCount()).isEqualTo(1);
  }

  @Test
  void shouldPlanUpdateRoutingState() {
    // given
    setCurrentTopology(
        initialTopology.updateMember(
            coordinatorId, m -> m.addPartition(1, PartitionState.active(1, partitionConfig))));
    final var routingState = RoutingState.initializeWithPartitionCount(1);

    // when
    final var response =
        clientApi
            .updateRoutingState(
                new UpdateRoutingStateRequest(Optional.of(routingState), Optional.empty(), false))
            .join()
            .get();

    // then
    assertThat(response.legacyResponse().plannedChanges())
        .containsExactly(new UpdateRoutingState(coordinatorId, Optional.of(routingState)));
  }

  @Test
  void shouldApplyRoutingStateUpdateToOnlyTheRequestedPhysicalTenant() {
    // given
    wireTwoPhysicalTenants();
    final var routingState = RoutingState.initializeWithPartitionCount(1);

    // when
    clientApi
        .updateRoutingState(
            new UpdateRoutingStateRequest(Optional.of(routingState), Optional.of(TENANT_B), false))
        .join()
        .get();

    // then — only tenant-b's routing state changed; the default group's is left untouched
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.partitionGroup(TENANT_B).routingState()).contains(routingState);
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).routingState())
        .isEmpty();
  }

  @Test
  void shouldApplyRoutingStateUpdateToTheDefaultPhysicalTenantWhenUnscoped() {
    // given — unlike mode changes, where an absent physicalTenantId means "every tenant", an
    // unscoped routing-state update keeps writing only the default group
    wireTwoPhysicalTenants();
    final var routingState = RoutingState.initializeWithPartitionCount(1);

    // when
    clientApi
        .updateRoutingState(
            new UpdateRoutingStateRequest(Optional.of(routingState), Optional.empty(), false))
        .join()
        .get();

    // then
    final var config = manager.getMultiConfiguration().join();
    assertThat(config.phasedChangeState().pending()).isEmpty();
    assertThat(config.partitionGroup(CurrentClusterConfiguration.DEFAULT_GROUP).routingState())
        .contains(routingState);
    assertThat(config.partitionGroup(TENANT_B).routingState()).isEmpty();
  }
}
