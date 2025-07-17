/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static io.camunda.zeebe.dynamic.config.ClusterConfigurationAssert.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers.MemberOperationApplier;
import io.camunda.zeebe.dynamic.config.changes.NoopConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.dynamic.config.serializer.ClusterConfigurationSerializer;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.util.Either;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClusterConfigurationManagerTest {

  @TempDir Path tempDir;
  AtomicReference<ClusterConfiguration> gossipState = new AtomicReference<>();
  private final ClusterConfigurationSerializer serializer = new ProtoBufSerializer();
  private final Consumer<ClusterConfiguration> gossipHandler = gossipState::set;

  private final ClusterConfiguration initialTopology =
      ClusterConfiguration.init()
          .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));
  private final ClusterConfigurationInitializer successInitializer =
      () -> CompletableActorFuture.completed(initialTopology);
  private final MemberId localMemberId = MemberId.from("1");

  private PersistedClusterConfiguration persistedClusterConfiguration;
  @AutoClose private MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TopologyManagerMetrics topologyMetrics = new TopologyManagerMetrics(meterRegistry);

  @BeforeEach
  void init() {
    persistedClusterConfiguration =
        PersistedClusterConfiguration.ofFile(tempDir.resolve("topology.temp"), serializer);
  }

  private ActorFuture<ClusterConfigurationManagerImpl> startTopologyManager(
      final ClusterConfigurationInitializer clusterConfigurationInitializer) {
    return startTopologyManager(
        clusterConfigurationInitializer, new NoopConfigurationChangeAppliers());
  }

  private ActorFuture<ClusterConfigurationManagerImpl> startTopologyManager(
      final ClusterConfigurationInitializer clusterConfigurationInitializer,
      final ConfigurationChangeAppliers operationsAppliers) {
    final var clusterTopologyManager = createTopologyManager();

    final ActorFuture<ClusterConfigurationManagerImpl> startFuture = new TestActorFuture<>();

    clusterTopologyManager
        .start(clusterConfigurationInitializer)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                clusterTopologyManager.registerTopologyChangeAppliers(operationsAppliers);
                startFuture.complete(clusterTopologyManager);
              } else {
                startFuture.completeExceptionally(error);
              }
            });
    return startFuture;
  }

  private ClusterConfigurationManagerImpl createTopologyManager() {
    final ClusterConfigurationManagerImpl clusterTopologyManager =
        new ClusterConfigurationManagerImpl(
            new TestConcurrencyControl(),
            localMemberId,
            persistedClusterConfiguration,
            topologyMetrics,
            Duration.ofMillis(100),
            Duration.ofMillis(200));
    clusterTopologyManager.setConfigurationGossiper(gossipHandler);
    return clusterTopologyManager;
  }

  @Test
  void shouldUpdatePersistedClusterTopologyAfterInitialization() {
    // given
    startTopologyManager(successInitializer).join();

    // then
    final ClusterConfiguration topology = persistedClusterConfiguration.getConfiguration();
    assertThat(topology).isEqualTo(initialTopology);
  }

  @Test
  void shouldGossipInitialTopology() {
    // given
    startTopologyManager(successInitializer).join();

    // then
    final ClusterConfiguration gossippedTopology = gossipState.get();
    assertThat(gossippedTopology).isEqualTo(initialTopology);
  }

  @Test
  void shouldFailToStartIfTopologyInitializationThrownError() {
    // given
    final ClusterConfigurationInitializer failingInitializer =
        () -> CompletableActorFuture.completedExceptionally(new RuntimeException("Expected"));
    final var startFuture = startTopologyManager(failingInitializer);

    // when - then
    Assertions.assertThat(startFuture).failsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldFailToStartIfTopologyIsNotInitialized() {
    // given
    final ClusterConfigurationInitializer failingInitializer =
        () -> CompletableActorFuture.completed(ClusterConfiguration.uninitialized());
    final var startFuture = startTopologyManager(failingInitializer);

    // when - then
    Assertions.assertThat(startFuture).failsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldUpdateLocalTopologyOnGossipEvent() {
    // given
    final ClusterConfigurationManagerImpl clusterTopologyManager =
        startTopologyManager(successInitializer).join();

    // when
    final ClusterConfiguration topologyFromOtherMember =
        clusterTopologyManager
            .getClusterConfiguration()
            .join()
            .addMember(MemberId.from("10"), MemberState.initializeAsActive(Map.of()));

    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    final ClusterConfiguration clusterConfiguration =
        clusterTopologyManager.getClusterConfiguration().join();
    assertThatClusterTopology(clusterConfiguration).hasOnlyMembers(Set.of(1, 10));
    assertThat(gossipState.get())
        .describedAs("Gossip state contains same topology in topology manager")
        .isEqualTo(clusterConfiguration);
    assertThat(persistedClusterConfiguration.getConfiguration())
        .describedAs("Updated topology is persisted")
        .isEqualTo(clusterConfiguration);
  }

  @Test
  void shouldNotUpdateLocalTopologyOnGossipEventBeforeInitialization() {
    // given - not started cluster topology manager
    final ClusterConfigurationManagerImpl clusterTopologyManager = createTopologyManager();

    // when
    final ClusterConfiguration topologyFromOtherMember =
        clusterTopologyManager
            .getClusterConfiguration()
            .join()
            .addMember(MemberId.from("10"), MemberState.initializeAsActive(Map.of()));
    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    assertThat(persistedClusterConfiguration.getConfiguration().isUninitialized()).isTrue();
  }

  @Test
  void shouldInitiateClusterTopologyChangeOnGossip() {
    // given
    final ClusterConfigurationManagerImpl clusterTopologyManager =
        startTopologyManager(successInitializer, new TestMemberLeaver()).join();

    // when
    final ClusterConfiguration topologyFromOtherMember =
        initialTopology.startConfigurationChange(
            List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));
    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    Awaitility.await("ClusterConfiguration is updated after applying topology change operation.")
        .untilAsserted(
            () ->
                ClusterConfigurationAssert.assertThatClusterTopology(
                        clusterTopologyManager.getClusterConfiguration().join())
                    .hasPendingOperationsWithSize(0)
                    .doesNotHaveMember(1));
    assertThat(gossipState.get())
        .describedAs("Updated topology is gossiped")
        .isEqualTo(clusterTopologyManager.getClusterConfiguration().join());
  }

  @Test
  void shouldContinueClusterTopologyChangeOnRestart() {
    // given
    final ClusterConfiguration topologyWithPendingOperation =
        initialTopology.startConfigurationChange(
            List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));
    final ClusterConfigurationInitializer initializer =
        () -> CompletableActorFuture.completed(topologyWithPendingOperation);

    // when
    final ClusterConfigurationManagerImpl clusterTopologyManager =
        startTopologyManager(initializer, new TestMemberLeaver()).join();

    // then
    Awaitility.await("ClusterTopology is updated after applying topology change operation.")
        .untilAsserted(
            () ->
                ClusterConfigurationAssert.assertThatClusterTopology(
                        clusterTopologyManager.getClusterConfiguration().join())
                    .hasPendingOperationsWithSize(0)
                    .doesNotHaveMember(1));
    assertThat(gossipState.get())
        .describedAs("Updated topology is gossiped")
        .isEqualTo(clusterTopologyManager.getClusterConfiguration().join());
  }

  @Test
  void shouldRetryClusterTopologyChangeOperationOnFailure() {
    // given
    final FailingLeaveApplier failingLeaveApplier = new FailingLeaveApplier(localMemberId, 1);
    final ClusterConfigurationManagerImpl clusterTopologyManager =
        startTopologyManager(successInitializer, o -> failingLeaveApplier).join();

    // when
    final ClusterConfiguration topologyFromOtherMember =
        initialTopology.startConfigurationChange(
            List.of(new PartitionLeaveOperation(localMemberId, 1, 1)));
    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    Awaitility.await("ClusterTopology is updated after applying topology change operation.")
        .untilAsserted(
            () ->
                ClusterConfigurationAssert.assertThatClusterTopology(
                        clusterTopologyManager.getClusterConfiguration().join())
                    .hasPendingOperationsWithSize(0)
                    .doesNotHaveMember(1));
    assertThat(gossipState.get())
        .describedAs("Updated topology is gossiped")
        .isEqualTo(clusterTopologyManager.getClusterConfiguration().join());
  }

  @Test
  void shouldCallListenerWhenInconsistentLocalStateDetected() {
    // given
    final AtomicReference<ClusterConfiguration> newTopology = new AtomicReference<>();
    final AtomicReference<ClusterConfiguration> oldTopology = new AtomicReference<>();
    final CompletableFuture<Void> listenerCalled = new CompletableFuture<>();
    final InconsistentConfigurationListener listener =
        (received, old) -> {
          newTopology.set(received);
          oldTopology.set(old);
          listenerCalled.complete(null);
        };
    final ClusterConfigurationManagerImpl clusterTopologyManager =
        startTopologyManager(successInitializer).join();
    clusterTopologyManager.registerTopologyChangedListener(listener);

    // when
    final var conflictingTopology =
        initialTopology.updateMember(localMemberId, MemberState::toLeft);
    clusterTopologyManager.onGossipReceived(conflictingTopology);

    // then
    assertThat(listenerCalled).succeedsWithin(Duration.ofMillis(1000));
    assertThat(newTopology.get()).isEqualTo(conflictingTopology);
    assertThat(oldTopology.get()).isEqualTo(initialTopology);
  }

  @Test
  void shouldNotDetectInconsistencyWhenLocalMemberHasLeft() {
    final CompletableFuture<Void> listenerCalled = new CompletableFuture<>();
    final InconsistentConfigurationListener listener =
        (received, old) -> {
          listenerCalled.complete(null);
        };
    final var topologyWithMemberLeft =
        initialTopology.updateMember(localMemberId, MemberState::toLeft);
    final ClusterConfigurationManagerImpl clusterTopologyManager =
        startTopologyManager(() -> CompletableActorFuture.completed(topologyWithMemberLeft)).join();
    clusterTopologyManager.registerTopologyChangedListener(listener);

    // when
    final var topologyWithoutMember = topologyWithMemberLeft.updateMember(localMemberId, m -> null);
    final var notConflictingTopology =
        new ClusterConfiguration(
            topologyWithoutMember.version() + 1,
            topologyWithoutMember.members(),
            topologyWithoutMember.lastChange(),
            topologyWithoutMember.pendingChanges(),
            topologyWithoutMember.routingState(),
            topologyWithoutMember.clusterId());
    clusterTopologyManager.onGossipReceived(notConflictingTopology);

    // then
    assertThat(clusterTopologyManager.getClusterConfiguration().join())
        .isEqualTo(notConflictingTopology);
    assertThat(listenerCalled).describedAs("Inconsistency listener is never invoked").isNotDone();
  }

  private static final class TestMemberLeaver implements ConfigurationChangeAppliers {

    @Override
    public MemberOperationApplier getApplier(final ClusterConfigurationChangeOperation operation) {
      // ignore type of operation and always apply member leave operation
      return new LeaveOperationApplier(operation.memberId());
    }
  }

  private static class LeaveOperationApplier implements MemberOperationApplier {

    private final MemberId memberId;

    public LeaveOperationApplier(final MemberId memberId) {
      this.memberId = memberId;
    }

    @Override
    public MemberId memberId() {
      return memberId;
    }

    @Override
    public Either<Exception, UnaryOperator<MemberState>> initMemberState(
        final ClusterConfiguration currentClusterConfiguration) {
      return Either.right(MemberState::toLeaving);
    }

    @Override
    public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
      return CompletableActorFuture.completed(MemberState::toLeft);
    }
  }

  private static final class FailingLeaveApplier extends LeaveOperationApplier {
    int numFailures;

    private FailingLeaveApplier(final MemberId memberId, final int numFailures) {
      super(memberId);
      this.numFailures = numFailures;
    }

    @Override
    public ActorFuture<UnaryOperator<MemberState>> applyOperation() {
      if (numFailures > 0) {
        numFailures--;
        return CompletableActorFuture.completedExceptionally(new RuntimeException("Expected"));
      } else {
        return super.applyOperation();
      }
    }
  }
}
