/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.MemberId;
import io.atomix.cluster.Node;
import io.atomix.cluster.discovery.BootstrapDiscoveryProvider;
import io.atomix.cluster.impl.DiscoveryMembershipProtocol;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.ControllablePartitionDistributor;
import io.camunda.zeebe.dynamic.config.StaticConfiguration;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.PhysicalTenantConfigRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor.NoopClusterChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.ModeChangeExecutor.NoopModeChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PartitionScalingChangeExecutor.NoopPartitionScalingChangeExecutor;
import io.camunda.zeebe.dynamic.config.changes.PhysicalTenantConfigChangeExecutor;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.impl.changes.NoopPartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.impl.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Changes of a physical tenant's configuration across three members with real messaging and gossip:
 * a change sent to any member reaches the coordinator, and every member fetches the configuration,
 * runs the tenant with it and records the version it applied.
 */
final class PhysicalTenantConfigRolloutTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(60);

  @TempDir Path rootDir;

  private final ActorScheduler actorScheduler = ActorScheduler.newActorScheduler().build();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final List<Node> clusterNodes =
      List.of(createNode("0"), createNode("1"), createNode("2"));
  private final Set<MemberId> memberIds =
      clusterNodes.stream().map(node -> MemberId.from(node.id().id())).collect(Collectors.toSet());
  private final Map<Integer, TestMember> members = new ConcurrentHashMap<>();

  @BeforeEach
  void setUp() {
    actorScheduler.start();
    final var started = IntStream.range(0, clusterNodes.size()).mapToObj(this::start).toList();
    started.forEach(CompletableFuture::join);
    Awaitility.await("every member initialises the cluster configuration")
        .atMost(TIMEOUT)
        .until(
            () ->
                members.values().stream()
                    .allMatch(member -> !member.configuration().isUninitialized()));
  }

  @AfterEach
  void tearDown() {
    members.values().forEach(TestMember::close);
    actorScheduler.stop();
    meterRegistry.close();
  }

  @Test
  void shouldRollOutATenantCreatedThroughAMemberThatIsNotTheCoordinator() {
    // given
    final var tenantId = uniqueTenantId();
    final var configuration = bytes("data.snapshot-period=5m");

    // when
    final var response =
        members.get(2).sender().changePhysicalTenantConfig(create(tenantId, configuration)).join();

    // then
    assertThat(response.isRight()).isTrue();
    awaitRolledOut(tenantId, 1);
    assertThat(members.values())
        .allSatisfy(
            member -> assertThat(member.executor().running.get(tenantId)).isEqualTo(configuration));
    final var group = members.get(1).configuration().partitionGroup(tenantId);
    assertThat(group.partitionCount()).isOne();
    assertThat(group.tenantConfig().orElseThrow().owner()).isEqualTo(Owner.API);
  }

  @Test
  void shouldApplyAnUpdateOnEveryMember() {
    // given
    final var tenantId = uniqueTenantId();
    members.get(1).sender().changePhysicalTenantConfig(create(tenantId, bytes("a=1"))).join();
    awaitRolledOut(tenantId, 1);

    // when
    final var response =
        members
            .get(1)
            .sender()
            .changePhysicalTenantConfig(
                PhysicalTenantConfigRequest.update(tenantId, Owner.API, bytes("a=2"), 1))
            .join();

    // then
    assertThat(response.isRight()).isTrue();
    awaitRolledOut(tenantId, 2);
    assertThat(members.values())
        .allSatisfy(
            member -> assertThat(member.executor().running.get(tenantId)).isEqualTo(bytes("a=2")));
  }

  @Test
  void shouldRollOutChangesOfOtherTenantsWhileOneTenantsChangeCannotApply() {
    // given
    final var stuck = uniqueTenantId();
    members.get(0).sender().changePhysicalTenantConfig(create(stuck, bytes("a=1"))).join();
    awaitRolledOut(stuck, 1);
    members.get(1).executor().failing = (tenantId, configuration) -> tenantId.equals(stuck);
    members
        .get(0)
        .sender()
        .changePhysicalTenantConfig(
            PhysicalTenantConfigRequest.update(stuck, Owner.API, bytes("a=2"), 1))
        .join();
    Awaitility.await("the first member applies the stuck change")
        .atMost(TIMEOUT)
        .until(() -> appliedBy(0, stuck) == 2);
    final var created = uniqueTenantId();
    final var updated = uniqueTenantId();
    members.get(2).sender().changePhysicalTenantConfig(create(updated, bytes("u=1"))).join();
    awaitRolledOut(updated, 1);

    // when
    final var create =
        members.get(2).sender().changePhysicalTenantConfig(create(created, bytes("c=1"))).join();
    final var update =
        members
            .get(1)
            .sender()
            .changePhysicalTenantConfig(
                PhysicalTenantConfigRequest.update(updated, Owner.API, bytes("u=2"), 1))
            .join();

    // then
    assertThat(create.isRight()).isTrue();
    assertThat(update.isRight()).isTrue();
    awaitRolledOut(created, 1);
    awaitRolledOut(updated, 2);
    assertThat(appliedBy(1, stuck)).isEqualTo(1);
    assertThat(members.get(1).configuration().partitionGroup(stuck).hasPendingChanges()).isTrue();
  }

  @Test
  void shouldRejectASecondChangeOfATenantWhileItsChangeRollsOut() {
    // given
    final var tenantId = uniqueTenantId();
    members.get(0).sender().changePhysicalTenantConfig(create(tenantId, bytes("a=1"))).join();
    awaitRolledOut(tenantId, 1);
    members.get(1).executor().gate = new CompletableFuture<>();
    members
        .get(0)
        .sender()
        .changePhysicalTenantConfig(
            PhysicalTenantConfigRequest.update(tenantId, Owner.API, bytes("a=2"), 1))
        .join();

    // when
    final var second =
        members
            .get(2)
            .sender()
            .changePhysicalTenantConfig(
                PhysicalTenantConfigRequest.update(tenantId, Owner.API, bytes("a=3"), 2))
            .join();

    // then
    assertThat(second.getLeft())
        .extracting(ErrorResponse::code)
        .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    members.get(1).executor().gate.complete(null);
    awaitRolledOut(tenantId, 2);
  }

  @Test
  void shouldApplyAChangeOnAMemberThatWasDownOnceItReturns() {
    // given
    final var tenantId = uniqueTenantId();
    members.remove(2).close();

    // when
    members.get(0).sender().changePhysicalTenantConfig(create(tenantId, bytes("a=1"))).join();
    Awaitility.await("the members that are up apply the change")
        .atMost(TIMEOUT)
        .until(() -> appliedBy(0, tenantId) == 1 && appliedBy(1, tenantId) == 1);
    start(2).join();

    // then
    awaitRolledOut(tenantId, 1);
    assertThat(members.get(2).executor().running.get(tenantId)).isEqualTo(bytes("a=1"));
  }

  @Test
  void shouldStopADeletedTenantOnEveryMemberAndKeepItsIdReserved() {
    // given
    final var tenantId = uniqueTenantId();
    members.get(0).sender().changePhysicalTenantConfig(create(tenantId, bytes("a=1"))).join();
    awaitRolledOut(tenantId, 1);

    // when
    members
        .get(1)
        .sender()
        .changePhysicalTenantConfig(PhysicalTenantConfigRequest.delete(tenantId, Owner.API))
        .join();

    // then
    Awaitility.await("the tenant is removed")
        .atMost(TIMEOUT)
        .until(() -> members.get(2).configuration().partitionGroup(tenantId).isRemoved());
    assertThat(members.values())
        .allSatisfy(member -> assertThat(member.executor().running).doesNotContainKey(tenantId));
    final var recreate =
        members.get(1).sender().changePhysicalTenantConfig(create(tenantId, bytes("a=1"))).join();
    assertThat(recreate.getLeft().message()).contains("stays reserved");
  }

  private void awaitRolledOut(final String tenantId, final long version) {
    Awaitility.await("version %d of '%s' is applied by every member".formatted(version, tenantId))
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(members.values())
                    .allSatisfy(
                        member -> {
                          final var group = member.configuration().partitionGroup(tenantId);
                          assertThat(group).isNotNull();
                          assertThat(group.pendingChanges()).isEmpty();
                          assertThat(group.tenantConfig())
                              .hasValueSatisfying(
                                  reference -> {
                                    assertThat(reference.version()).isEqualTo(version);
                                    assertThat(reference.isAppliedBy(memberIds)).isTrue();
                                  });
                        }));
  }

  private long appliedBy(final int member, final String tenantId) {
    return members.values().stream()
        .findFirst()
        .map(TestMember::configuration)
        .map(configuration -> configuration.partitionGroup(tenantId))
        .flatMap(PartitionGroupConfiguration::tenantConfig)
        .map(reference -> reference.appliedVersion(MemberId.from(String.valueOf(member))))
        .orElse(0L);
  }

  private CompletableFuture<Void> start(final int id) {
    final var node = clusterNodes.get(id);
    final var cluster =
        AtomixCluster.builder(meterRegistry)
            .withAddress(node.address())
            .withMemberId(node.id().id())
            .withMembershipProvider(new BootstrapDiscoveryProvider(clusterNodes))
            .withMembershipProtocol(new DiscoveryMembershipProtocol())
            .build();
    final var service =
        new ClusterConfigurationManagerServiceImpl(
            rootDir.resolve(node.id().id()),
            cluster.getCommunicationService(),
            cluster.getMembershipService(),
            new ClusterConfigurationGossiperConfig(
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                2,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5)),
            new NoopClusterChangeExecutor(),
            meterRegistry);
    final var executor = new RecordingExecutor(service);
    final var member = new TestMember(cluster, service, executor);
    members.put(id, member);
    cluster.start().join();
    return service
        .start(
            actorScheduler,
            new StaticConfiguration(
                new ControllablePartitionDistributor().withPartitions(defaultPartition()),
                memberIds,
                MemberId.from(node.id().id()),
                List.of(new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1)),
                3,
                Map.of(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, DynamicPartitionConfig.init()),
                "clusterId"),
            Map.of())
        .toCompletableFuture()
        .thenRun(
            () -> {
              registerGroupExecutors(service, PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID);
              service.registerPhysicalTenantConfigChangeExecutor(executor);
            });
  }

  private Set<PartitionMetadata> defaultPartition() {
    final var priorities =
        memberIds.stream().collect(Collectors.toMap(member -> member, member -> 1));
    return Set.of(
        new PartitionMetadata(
            new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, 1),
            memberIds,
            priorities,
            1,
            MemberId.from("0")));
  }

  private static void registerGroupExecutors(
      final ClusterConfigurationManagerServiceImpl service, final String groupId) {
    service.registerModeChangeExecutor(groupId, new NoopModeChangeExecutor());
    service.registerPartitionChangeExecutors(
        groupId, new NoopPartitionChangeExecutor(), new NoopPartitionScalingChangeExecutor());
  }

  private static PhysicalTenantConfigRequest create(
      final String tenantId, final byte[] configuration) {
    return PhysicalTenantConfigRequest.create(tenantId, Owner.API, configuration, 1, Set.of());
  }

  private static byte[] bytes(final String configuration) {
    return configuration.getBytes(StandardCharsets.UTF_8);
  }

  private static String uniqueTenantId() {
    return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private static Node createNode(final String id) {
    return Node.builder().withId(id).withPort(SocketUtil.getNextAddress().getPort()).build();
  }

  private record TestMember(
      AtomixCluster cluster,
      ClusterConfigurationManagerServiceImpl service,
      RecordingExecutor executor) {

    CurrentClusterConfiguration configuration() {
      return service.getClusterConfiguration().join();
    }

    ClusterConfigurationManagementRequestSender sender() {
      return new ClusterConfigurationManagementRequestSender(
          cluster.getCommunicationService(),
          ClusterConfigurationCoordinatorSupplier.from(this::configuration),
          new ProtoBufSerializer(),
          cluster.getMembershipService().getLocalMember().id());
    }

    void close() {
      service.closeAsync().join();
      cluster.stop().join();
    }
  }

  private static final class RecordingExecutor implements PhysicalTenantConfigChangeExecutor {

    private final ClusterConfigurationManagerServiceImpl service;
    private final Map<String, byte[]> running = new ConcurrentHashMap<>();
    private volatile CompletableFuture<Void> gate = CompletableFuture.completedFuture(null);
    private volatile BiPredicate<String, byte[]> failing = (tenantId, configuration) -> false;

    private RecordingExecutor(final ClusterConfigurationManagerServiceImpl service) {
      this.service = service;
    }

    @Override
    public CompletableFuture<Void> apply(
        final String physicalTenantId,
        final PhysicalTenantConfigReference reference,
        final byte[] configuration) {
      if (failing.test(physicalTenantId, configuration)) {
        return CompletableFuture.failedFuture(
            new IllegalStateException("a dependency of the tenant is unreachable"));
      }
      return gate.thenRunAsync(
          () -> {
            registerGroupExecutors(service, physicalTenantId);
            running.put(physicalTenantId, configuration);
          });
    }

    @Override
    public CompletableFuture<Void> stop(final String physicalTenantId) {
      running.remove(physicalTenantId);
      service.removePartitionChangeExecutor(physicalTenantId);
      return CompletableFuture.completedFuture(null);
    }
  }
}
