/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.broker.system.monitoring.HealthTreeMetrics;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.protocol.record.PartitionRole;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.SchedulingHints;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.transport.impl.AtomixServerTransport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecoveryPartitionManagerTest {

  private static final String GROUP = PartitionManagerImpl.DEFAULT_GROUP_NAME;
  private static final int PARTITION_ID = 1;
  private static final int PARTITION_ID_2 = 2;
  private static final String BROKER_COMPONENT_NAME = "Broker-0";

  private ActorScheduler actorScheduler;
  private Actor controlActor;
  private Member localMember;
  private MemberId localMemberId;
  private ClusterConfigurationService clusterConfigurationService;
  private ClusterServices clusterServices;
  private RecoveryPartitionManager partitionManager;
  private TopologyManagerImpl topologyManager;
  private BrokerInfo brokerInfo;
  private AtomixServerTransport transport;
  private SimpleMeterRegistry meterRegistry;
  private BrokerHealthCheckService healthCheckService;

  @BeforeEach
  void setUp() {
    actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();

    controlActor = new Actor() {};
    actorScheduler.submitActor(controlActor).join();

    localMember = new Member(new MemberConfig());
    localMemberId = localMember.id();

    final var membershipService = mock(ClusterMembershipService.class);
    when(membershipService.getLocalMember()).thenReturn(localMember);

    clusterServices = mock(ClusterServices.class);
    when(clusterServices.getMembershipService()).thenReturn(membershipService);

    final var metadata = localPartitionMetadata(PARTITION_ID);
    final var metadata2 = localPartitionMetadata(PARTITION_ID_2);
    clusterConfigurationService = mock(ClusterConfigurationService.class);
    when(clusterConfigurationService.getPartitionDistribution(any()))
        .thenReturn(new PartitionDistribution(Set.of(metadata, metadata2)));
    when(clusterConfigurationService.getCurrentClusterConfiguration())
        .thenReturn(CurrentClusterConfiguration.uninitialized());

    brokerInfo = new BrokerInfo(0, null, "localhost:26501").setPartitionGroup(GROUP);
    topologyManager = new TopologyManagerImpl(membershipService, brokerInfo);
    actorScheduler.submitActor(topologyManager).join();

    transport = mock(AtomixServerTransport.class);
    when(transport.subscribe(any(), any(), any()))
        .thenReturn(CompletableActorFuture.completed(null));
    when(transport.unsubscribe(any(), any())).thenReturn(CompletableActorFuture.completed(null));

    meterRegistry = new SimpleMeterRegistry();

    // real health check service, named Broker-0 to match BROKER_COMPONENT_NAME, so the tests can
    // observe the readiness and health the recovery manager reports to the broker probes
    healthCheckService =
        new BrokerHealthCheckService(
            MemberId.from("0"), new HealthTreeMetrics(meterRegistry), Set.of(GROUP));
    actorScheduler.submitActor(healthCheckService).join();
    healthCheckService.setBrokerStarted();

    partitionManager = buildManager(new BrokerCfg(), actorScheduler);
  }

  private RecoveryPartitionManager buildManager(
      final BrokerCfg brokerCfg, final ActorSchedulingService schedulingService) {
    return new RecoveryPartitionManager(
        GROUP,
        brokerCfg,
        brokerInfo,
        controlActor,
        clusterConfigurationService,
        clusterServices.getMembershipService(),
        schedulingService,
        meterRegistry,
        transport,
        null,
        topologyManager,
        healthCheckService);
  }

  private PartitionMetadata localPartitionMetadata(final int partitionId) {
    return new PartitionMetadata(
        new PartitionId(GROUP, partitionId),
        Set.of(localMemberId),
        Map.of(localMemberId, 1),
        1,
        localMemberId);
  }

  @AfterEach
  void tearDown() {
    if (partitionManager != null) {
      partitionManager.stop().join();
    }
    if (healthCheckService != null) {
      healthCheckService.closeAsync().join();
    }
    if (controlActor != null) {
      controlActor.closeAsync().join();
    }
    actorScheduler.stop();
  }

  @Test
  void shouldDeactivateLocalPartitionsOnStart() {
    // when
    controlActor.run(() -> partitionManager.start());

    // then
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionRoles())
                              .containsEntry(PARTITION_ID, PartitionRole.INACTIVE)
                              .containsEntry(PARTITION_ID_2, PartitionRole.INACTIVE));
            });
  }

  @Test
  void shouldNotStartRaftOrZeebePartitions() {
    // when
    controlActor.run(() -> partitionManager.start());

    // then
    assertThat(partitionManager.getRaftPartitions()).isEmpty();
    assertThat(partitionManager.getZeebePartitions()).isEmpty();
    assertThat(partitionManager.getRaftPartition(PARTITION_ID)).isNull();
  }

  @Test
  void shouldCreateAndCloseBackupStoreAcrossRepeatedStartStopCycles(@TempDir final Path tempDir) {
    // given
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().getBackup().setStore(BackupStoreType.FILESYSTEM);
    brokerCfg.getData().getBackup().getFilesystem().setBasePath(tempDir.toString());
    partitionManager = buildManager(brokerCfg, actorScheduler);

    // when/then: each cycle must create a fresh backup store and fully close the previous one;
    // a leaked or half-closed store would make the next start()/stop() hang or fail
    for (int i = 0; i < 2; i++) {
      assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));
      assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  @Test
  void shouldToleratePartialStartFailureAndStillDeactivateAllLocalPartitions() {
    // given: partition 2's recovery steps fail to schedule, so only partition 1 recovers
    partitionManager =
        buildManager(
            new BrokerCfg(), new FailingActorSchedulingService(actorScheduler, PARTITION_ID_2));

    // when
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));

    // then: start() still succeeds overall, but both partitions - including the one that
    // failed to start - are marked INACTIVE so nothing assumes partition 2 is serving traffic
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionRoles())
                              .containsEntry(PARTITION_ID, PartitionRole.INACTIVE)
                              .containsEntry(PARTITION_ID_2, PartitionRole.INACTIVE));
            });

    // and: only the partition that failed to start is reported as UNHEALTHY, since it never
    // recovered and needs the restore to be retried; the one that succeeded is reported HEALTHY
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionHealthStatuses())
                              .containsEntry(PARTITION_ID, PartitionHealthStatus.HEALTHY)
                              .containsEntry(PARTITION_ID_2, PartitionHealthStatus.UNHEALTHY));
            });
  }

  @Test
  void shouldReportHealthyForSuccessfullyRecoveredPartitions() {
    // when
    controlActor.run(() -> partitionManager.start());

    // then - both partitions started successfully, so both are reported healthy
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionHealthStatuses())
                              .containsEntry(PARTITION_ID, PartitionHealthStatus.HEALTHY)
                              .containsEntry(PARTITION_ID_2, PartitionHealthStatus.HEALTHY));
            });
  }

  @Test
  void shouldReportRecoveringHealthMetricAfterStartCompletes() {
    // when
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));

    // then - each successfully recovered partition's zeebe.health gauge reports the recovering
    // code (distinct from 1=healthy/0=unhealthy/-1=dead), since these partitions are inactive and
    // not yet processing, just kept alive for read-only backup access during broker restore
    assertThat(healthGaugeValue(PARTITION_ID)).isEqualTo(2);
    assertThat(healthGaugeValue(PARTITION_ID_2)).isEqualTo(2);
  }

  @Test
  void shouldRemoveHealthGaugeOnStop() {
    // given - the gauge exists once recovery has started
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));
    assertThat(healthGaugeValue(PARTITION_ID)).isEqualTo(2);

    // when
    assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(10));

    // then - the gauge is fully unregistered rather than left stale; Micrometer's
    // Gauge.builder(...).register() silently ignores a re-registration under the same name+tags,
    // so leaving it behind would make the next HealthMetrics registered for this partition (e.g.
    // by the normal ZeebePartition once the cluster exits recovery mode) permanently stuck at 2
    assertThat(
            meterRegistry
                .find("zeebe.health")
                .tags("physicalTenant", GROUP, "partition", String.valueOf(PARTITION_ID))
                .gauge())
        .isNull();
  }

  @Test
  void shouldReportRecoveringInHealthTreeAfterStartCompletes() {
    // when
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));

    // then - the partition also appears as a node in the component health tree that feeds the
    // "Health status timeline" panel, under the same id and path the normal ZeebePartition uses,
    // so the row stays in place across the mode switch instead of disappearing during recovery
    assertThat(healthTreeGaugeValue(PARTITION_ID)).isEqualTo(2);
    assertThat(healthTreeGaugeValue(PARTITION_ID_2)).isEqualTo(2);
  }

  @Test
  void shouldRemoveHealthTreeNodeOnStop() {
    // given
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));
    assertThat(healthTreeGaugeValue(PARTITION_ID)).isEqualTo(2);

    // when
    assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(10));

    // then - the node is unregistered, so the normal ZeebePartition can claim the same id/path
    // once the cluster leaves recovery mode
    assertThat(
            meterRegistry
                .find("zeebe.broker.health.nodes")
                .tags("physicalTenant", GROUP, "partition", String.valueOf(PARTITION_ID))
                .gauge())
        .isNull();
  }

  private static String partitionComponentName(final int partitionId) {
    return "Partition-%s-%d".formatted(GROUP, partitionId);
  }

  private double healthTreeGaugeValue(final int partitionId) {
    final var componentName = partitionComponentName(partitionId);
    return meterRegistry
        .get("zeebe.broker.health.nodes")
        .tags(
            "physicalTenant",
            GROUP,
            "partition",
            String.valueOf(partitionId),
            "id",
            componentName,
            "path",
            BROKER_COMPONENT_NAME + "/" + componentName)
        .gauge()
        .value();
  }

  private double healthGaugeValue(final int partitionId) {
    return meterRegistry
        .get("zeebe.health")
        .tags("physicalTenant", GROUP, "partition", String.valueOf(partitionId))
        .gauge()
        .value();
  }

  @Test
  void shouldReportBrokerReadyAndHealthyWhileRecovering() {
    // when
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));

    // then - a broker in recovery mode is ready and healthy by design: it must keep accepting
    // management traffic (restore requests) and must not be restarted by the Kubernetes probes,
    // even though its partitions never join Raft
    await()
        .untilAsserted(
            () -> {
              assertThat(healthCheckService.isBrokerReady()).isTrue();
              assertThat(healthCheckService.isBrokerHealthy()).isTrue();
            });
  }

  @Test
  void shouldReportBrokerUnhealthyWhenAPartitionFailsToRecover() {
    // given: partition 2's recovery steps fail to schedule, so only partition 1 recovers
    partitionManager =
        buildManager(
            new BrokerCfg(), new FailingActorSchedulingService(actorScheduler, PARTITION_ID_2));

    // when
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));

    // then - the broker stays ready so the restore can be retried through the management API,
    // but the failed partition must surface through the health status
    await()
        .untilAsserted(
            () -> {
              assertThat(healthCheckService.isBrokerReady()).isTrue();
              assertThat(healthCheckService.isBrokerHealthy()).isFalse();
            });
  }

  @Test
  void shouldReportBrokerHealthyWhilePartitionsAreStillRecovering() {
    // given - partition 2 never finishes starting, so the start future stays pending and the
    // callback that reports the recovery outcome never runs
    partitionManager =
        buildManager(
            new BrokerCfg(), new HangingActorSchedulingService(actorScheduler, PARTITION_ID_2));

    // when
    final var startResult = partitionManager.start();

    // then - the broker is ready and healthy for the whole of the recovery, not only once it
    // settles: the health monitor derives the broker's status from the components it holds, so a
    // partition with no component at all would report the broker unhealthy exactly while it is
    // recovering
    await()
        .untilAsserted(
            () -> {
              assertThat(healthCheckService.isBrokerReady()).isTrue();
              assertThat(healthCheckService.isBrokerHealthy()).isTrue();
            });
    // and - the recovery really is still in flight, so the assertions above cover the window
    // between entering recovery mode and the partitions having recovered
    assertThat(startResult.isDone()).isFalse();
  }

  @Test
  void shouldReportBrokerUnhealthyWhenTheBackupStoreCannotBeCreated() {
    // given - a filesystem backup store with no base path configured cannot be created
    final var brokerCfg = new BrokerCfg();
    brokerCfg.getData().getBackup().setStore(BackupStoreType.FILESYSTEM);
    partitionManager = buildManager(brokerCfg, actorScheduler);

    // when
    assertThat(partitionManager.start()).failsWithin(Duration.ofSeconds(10));

    // then - the broker stays ready so the configuration can be fixed and the restore retried,
    // but without a backup store no partition can recover, so it must not claim to be healthy
    await()
        .untilAsserted(
            () -> {
              assertThat(healthCheckService.isBrokerReady()).isTrue();
              assertThat(healthCheckService.isBrokerHealthy()).isFalse();
            });
  }

  @Test
  void shouldNotRegisterHealthComponentsAfterStop() {
    // given - partition 2's recovery is held up, so the callback that reports the recovery
    // outcome is still outstanding
    final var gate = new CompletableActorFuture<Void>();
    partitionManager =
        buildManager(
            new BrokerCfg(), new GatedActorSchedulingService(actorScheduler, gate, PARTITION_ID_2));
    final var startResult = partitionManager.start();
    await()
        .untilAsserted(
            () ->
                assertThat(healthCheckService.getHealthReport().children())
                    .containsOnlyKeys(
                        partitionComponentName(PARTITION_ID),
                        partitionComponentName(PARTITION_ID_2)));

    // when - the mode transition stops this manager, and only then does partition 2's recovery
    // fail. stop() does not wait for start(): a transition completes as soon as the next
    // manager's start is initiated, so both run on the same actor with no ordering between them
    assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(10));
    gate.completeExceptionally(new RuntimeException("Injected failure for partition 2"));
    assertThat(startResult).failsWithin(Duration.ofSeconds(10));

    // then - the outcome this manager reports afterwards never reaches the health tree. Such a
    // component would take the slot the next manager's ZeebePartition needs and sit there frozen,
    // so the broker would report a dead recovery's health for as long as it runs
    await()
        .during(Duration.ofSeconds(1))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(healthCheckService.getHealthReport().children()).isEmpty());
  }

  @Test
  void shouldResetReadinessAndHealthOnStop() {
    // given - the broker reports ready and healthy while recovering
    assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));
    await().untilAsserted(() -> assertThat(healthCheckService.isBrokerReady()).isTrue());

    // when
    assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(10));

    // then - the tenant and its recovery health components are unregistered, so readiness and
    // health are gated on the next partition manager (e.g. processing mode after exiting
    // recovery) genuinely installing its partitions rather than on recovery leftovers
    await()
        .untilAsserted(
            () -> {
              assertThat(healthCheckService.isBrokerReady()).isFalse();
              assertThat(healthCheckService.isBrokerHealthy()).isFalse();
            });
  }

  @Test
  void shouldDeactivateAllLocalPartitionsAndFailStartWhenAllPartitionsFail() {
    // given: both partitions fail to schedule, so none recover
    partitionManager =
        buildManager(
            new BrokerCfg(),
            new FailingActorSchedulingService(actorScheduler, PARTITION_ID, PARTITION_ID_2));

    // when
    final var startFuture = partitionManager.start();

    // then: start() fails since no partition recovered, but both partitions are still marked
    // INACTIVE so nothing assumes they are serving traffic
    assertThat(startFuture).failsWithin(Duration.ofSeconds(10));
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionRoles())
                              .containsEntry(PARTITION_ID, PartitionRole.INACTIVE)
                              .containsEntry(PARTITION_ID_2, PartitionRole.INACTIVE));
            });

    // and: both partitions are reported as UNHEALTHY, since neither recovered - this is the
    // signal that the mode-change bookkeeping (which only checks the INACTIVE role above)
    // otherwise misses
    await()
        .untilAsserted(
            () -> {
              final var publishedInfos = BrokerInfo.allFromProperties(localMember.properties());
              assertThat(publishedInfos)
                  .anySatisfy(
                      info ->
                          assertThat(info.getPartitionHealthStatuses())
                              .containsEntry(PARTITION_ID, PartitionHealthStatus.UNHEALTHY)
                              .containsEntry(PARTITION_ID_2, PartitionHealthStatus.UNHEALTHY));
            });
  }

  /**
   * Hands out {@code gate} as the submitted actor's future, so the gated partition's recovery only
   * settles once the test completes it.
   */
  private static final class GatedActorSchedulingService implements ActorSchedulingService {
    private final ActorSchedulingService delegate;
    private final ActorFuture<Void> gate;
    private final Set<Integer> gatedPartitionIds;

    private GatedActorSchedulingService(
        final ActorSchedulingService delegate,
        final ActorFuture<Void> gate,
        final Integer... gatedPartitionIds) {
      this.delegate = delegate;
      this.gate = gate;
      this.gatedPartitionIds = Set.of(gatedPartitionIds);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor) {
      return shouldGate(actor) ? gate : delegate.submitActor(actor);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor, final SchedulingHints schedulingHints) {
      return shouldGate(actor) ? gate : delegate.submitActor(actor, schedulingHints);
    }

    private boolean shouldGate(final Actor actor) {
      return gatedPartitionIds.stream().anyMatch(id -> actor.getName().endsWith("-" + id));
    }
  }

  /**
   * Leaves the submitted actor's future pending forever, so the partition never finishes starting.
   */
  private static final class HangingActorSchedulingService implements ActorSchedulingService {
    private final ActorSchedulingService delegate;
    private final Set<Integer> hangingPartitionIds;

    private HangingActorSchedulingService(
        final ActorSchedulingService delegate, final Integer... hangingPartitionIds) {
      this.delegate = delegate;
      this.hangingPartitionIds = Set.of(hangingPartitionIds);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor) {
      return shouldHang(actor) ? new CompletableActorFuture<>() : delegate.submitActor(actor);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor, final SchedulingHints schedulingHints) {
      return shouldHang(actor)
          ? new CompletableActorFuture<>()
          : delegate.submitActor(actor, schedulingHints);
    }

    private boolean shouldHang(final Actor actor) {
      return hangingPartitionIds.stream().anyMatch(id -> actor.getName().endsWith("-" + id));
    }
  }

  private static final class FailingActorSchedulingService implements ActorSchedulingService {
    private final ActorSchedulingService delegate;
    private final Set<Integer> failingPartitionIds;

    private FailingActorSchedulingService(
        final ActorSchedulingService delegate, final Integer... failingPartitionIds) {
      this.delegate = delegate;
      this.failingPartitionIds = Set.of(failingPartitionIds);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor) {
      return shouldFail(actor) ? failure() : delegate.submitActor(actor);
    }

    @Override
    public ActorFuture<Void> submitActor(final Actor actor, final SchedulingHints schedulingHints) {
      return shouldFail(actor) ? failure() : delegate.submitActor(actor, schedulingHints);
    }

    private boolean shouldFail(final Actor actor) {
      return failingPartitionIds.stream().anyMatch(id -> actor.getName().endsWith("-" + id));
    }

    private ActorFuture<Void> failure() {
      return CompletableActorFuture.completedExceptionally(
          new RuntimeException("Injected failure for partition(s) " + failingPartitionIds));
    }
  }

  @Nested
  class Start {

    private AtomicReference<ActorFuture<Void>> startFuture;

    @BeforeEach
    void setUp() {
      startFuture = new AtomicReference<>();
    }

    @Test
    void shouldCompleteStartFutureOnSuccess() {
      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));

      // then
      awaitStart();
      assertThat(startFuture.get().isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldCompleteImmediatelyWhenNoLocalPartitions() {
      // given
      when(clusterConfigurationService.getPartitionDistribution(any()))
          .thenReturn(new PartitionDistribution(Set.of()));

      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));

      // then
      await()
          .atMost(Duration.ofSeconds(1))
          .until(() -> startFuture.get() != null && startFuture.get().isDone());
      assertThat(startFuture.get().isCompletedExceptionally()).isFalse();
    }

    @Test
    void shouldFailStartWhenDeactivationFails() {
      // given
      topologyManager.closeAsync().join();

      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));

      // then
      awaitStart();
      assertThat(startFuture.get().isCompletedExceptionally()).isTrue();
    }

    @Test
    void shouldStopCleanlyAfterDeactivationFailure() {
      // given
      topologyManager.closeAsync().join();

      // when
      controlActor.run(() -> startFuture.set(partitionManager.start()));
      awaitStart();

      // then
      assertThat(startFuture.get().isCompletedExceptionally()).isTrue();
      assertThat(partitionManager.stop()).succeedsWithin(Duration.ofSeconds(5));
    }

    private void awaitStart() {
      await()
          .atMost(Duration.ofSeconds(10))
          .until(() -> startFuture.get() != null && startFuture.get().isDone());
    }
  }

  @Nested
  class PreRestore {

    @Test
    void shouldDeleteLocalPartitionData(@TempDir final Path tempDir) {
      // given
      final var brokerCfg = new BrokerCfg();
      brokerCfg.getData().setDirectory(tempDir.toString());
      partitionManager = buildManager(brokerCfg, actorScheduler);
      controlActor.run(() -> partitionManager.start());
      await().atMost(Duration.ofSeconds(10)).until(() -> true); // let start() settle
      final var partitionDir =
          tempDir.resolve(GROUP).resolve("partitions").resolve(String.valueOf(PARTITION_ID));
      writeMarkerFile(partitionDir);

      // when
      final var future = new AtomicReference<ActorFuture<Void>>();
      controlActor.run(() -> future.set(partitionManager.preRestore(PARTITION_ID)));

      // then
      await().atMost(Duration.ofSeconds(10)).until(() -> future.get() != null);
      assertThat(future.get()).succeedsWithin(Duration.ofSeconds(10));
      assertThat(partitionDir).isEmptyDirectory();
    }

    @Test
    void shouldBeIdempotentWhenDirectoryIsAlreadyEmpty() {
      // given: no local partitions, so preRestore's target directory is never created, and
      // start() only needs to set up the restoreExecutor for this to be a no-op deletion
      when(clusterConfigurationService.getPartitionDistribution(any()))
          .thenReturn(new PartitionDistribution(Set.of()));
      controlActor.run(() -> partitionManager.start());
      await().atMost(Duration.ofSeconds(10)).until(() -> true); // let start() settle

      // when
      final var future = new AtomicReference<ActorFuture<Void>>();
      controlActor.run(() -> future.set(partitionManager.preRestore(PARTITION_ID)));

      // then
      await().atMost(Duration.ofSeconds(10)).until(() -> future.get() != null);
      assertThat(future.get()).succeedsWithin(Duration.ofSeconds(10));
    }

    @Test
    void shouldFailWhenNotStarted() {
      // when
      final var future = new AtomicReference<ActorFuture<Void>>();
      controlActor.run(() -> future.set(partitionManager.preRestore(PARTITION_ID)));

      // then
      await().atMost(Duration.ofSeconds(10)).until(() -> future.get() != null);
      assertThat(future.get()).failsWithin(Duration.ofSeconds(10));
    }

    private void writeMarkerFile(final Path partitionDir) {
      try {
        Files.createDirectories(partitionDir);
        Files.writeString(partitionDir.resolve("marker.txt"), "data");
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Nested
  class Restore {

    @Test
    void shouldFailForUnknownPartition(@TempDir final Path tempDir) {
      // given: a properly configured backup store, so start() succeeds and backupStore is
      // genuinely non-null - this test must exercise the "not a local partition of group" branch,
      // not the "no backup store configured" one
      final var brokerCfg = new BrokerCfg();
      brokerCfg.getData().getBackup().setStore(BackupStoreType.FILESYSTEM);
      brokerCfg.getData().getBackup().getFilesystem().setBasePath(tempDir.toString());
      partitionManager = buildManager(brokerCfg, actorScheduler);
      assertThat(partitionManager.start()).succeedsWithin(Duration.ofSeconds(10));

      // when: restoring a partition id that is not one of the manager's local partitions
      // (only PARTITION_ID and PARTITION_ID_2 are local per this class's setup)
      final var future = new AtomicReference<ActorFuture<Void>>();
      controlActor.run(() -> future.set(partitionManager.restore(999, new TreeSet<>(List.of(1L)))));

      // then
      await().atMost(Duration.ofSeconds(10)).until(() -> future.get() != null);
      assertThat(future.get()).failsWithin(Duration.ofSeconds(10));
      assertThat(future.get().getException())
          .hasMessageContaining("not a local partition of group");
    }

    @Test
    void shouldDeletePartitionDataWhenRestoreFails(@TempDir final Path tempDir) {
      // given: a filesystem backup store with no backups taken, so restore is guaranteed to fail
      final var brokerCfg = new BrokerCfg();
      brokerCfg.getData().setDirectory(tempDir.toString());
      brokerCfg.getData().getBackup().setStore(BackupStoreType.FILESYSTEM);
      brokerCfg
          .getData()
          .getBackup()
          .getFilesystem()
          .setBasePath(tempDir.resolve("backups").toString());
      partitionManager = buildManager(brokerCfg, actorScheduler);
      controlActor.run(() -> partitionManager.start());
      await().atMost(Duration.ofSeconds(10)).until(() -> true);
      final var partitionDir =
          tempDir.resolve(GROUP).resolve("partitions").resolve(String.valueOf(PARTITION_ID));

      // when
      final var future = new AtomicReference<ActorFuture<Void>>();
      controlActor.run(
          () -> future.set(partitionManager.restore(PARTITION_ID, new TreeSet<>(List.of(1L)))));

      // then
      await().atMost(Duration.ofSeconds(10)).until(() -> future.get() != null);
      assertThat(future.get()).failsWithin(Duration.ofSeconds(10));
      assertThat(partitionDir).isEmptyDirectory();
    }
  }
}
