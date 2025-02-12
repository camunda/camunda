/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import static io.camunda.zeebe.topology.ClusterTopologyAssert.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.topology.ClusterTopologyManager.InconsistentTopologyListener;
import io.camunda.zeebe.topology.changes.NoopTopologyChangeAppliers;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers;
import io.camunda.zeebe.topology.changes.TopologyChangeAppliers.MemberOperationApplier;
import io.camunda.zeebe.topology.metrics.TopologyManagerMetrics;
import io.camunda.zeebe.topology.serializer.ClusterTopologySerializer;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.TopologyChangeOperation;
import io.camunda.zeebe.topology.state.TopologyChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ClusterTopologyManagerTest {

  @TempDir Path tempDir;
  AtomicReference<ClusterTopology> gossipState = new AtomicReference<>();
  private final ClusterTopologySerializer serializer = new ProtoBufSerializer();
  private final Consumer<ClusterTopology> gossipHandler = gossipState::set;

  private final ClusterTopology initialTopology =
      ClusterTopology.init()
          .addMember(MemberId.from("1"), MemberState.initializeAsActive(Map.of()));
  private final TopologyInitializer successInitializer =
      () -> CompletableActorFuture.completed(initialTopology);
  private final MemberId localMemberId = MemberId.from("1");

  private PersistedClusterTopology persistedClusterTopology;
  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final TopologyManagerMetrics topologyMetrics = new TopologyManagerMetrics(meterRegistry);

  @BeforeEach
  void init() {
    persistedClusterTopology =
        PersistedClusterTopology.ofFile(tempDir.resolve("topology.temp"), serializer);
  }

  private ActorFuture<ClusterTopologyManagerImpl> startTopologyManager(
      final TopologyInitializer topologyInitializer) {
    return startTopologyManager(topologyInitializer, new NoopTopologyChangeAppliers());
  }

  private ActorFuture<ClusterTopologyManagerImpl> startTopologyManager(
      final TopologyInitializer topologyInitializer,
      final TopologyChangeAppliers operationsAppliers) {
    final var clusterTopologyManager = createTopologyManager();

    final ActorFuture<ClusterTopologyManagerImpl> startFuture = new TestActorFuture<>();

    clusterTopologyManager
        .start(topologyInitializer)
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

  private ClusterTopologyManagerImpl createTopologyManager() {
    final ClusterTopologyManagerImpl clusterTopologyManager =
        new ClusterTopologyManagerImpl(
            new TestConcurrencyControl(),
            localMemberId,
            persistedClusterTopology,
            topologyMetrics,
            Duration.ofMillis(100),
            Duration.ofMillis(200));
    clusterTopologyManager.setTopologyGossiper(gossipHandler);
    return clusterTopologyManager;
  }

  @Test
  void shouldUpdatePersistedClusterTopologyAfterInitialization() {
    // given
    startTopologyManager(successInitializer).join();

    // then
    final ClusterTopology topology = persistedClusterTopology.getTopology();
    assertThat(topology).isEqualTo(initialTopology);
  }

  @Test
  void shouldGossipInitialTopology() {
    // given
    startTopologyManager(successInitializer).join();

    // then
    final ClusterTopology gossippedTopology = gossipState.get();
    assertThat(gossippedTopology).isEqualTo(initialTopology);
  }

  @Test
  void shouldFailToStartIfTopologyInitializationThrownError() {
    // given
    final TopologyInitializer failingInitializer =
        () -> CompletableActorFuture.completedExceptionally(new RuntimeException("Expected"));
    final var startFuture = startTopologyManager(failingInitializer);

    // when - then
    assertThat(startFuture).failsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldFailToStartIfTopologyIsNotInitialized() {
    // given
    final TopologyInitializer failingInitializer =
        () -> CompletableActorFuture.completed(ClusterTopology.uninitialized());
    final var startFuture = startTopologyManager(failingInitializer);

    // when - then
    assertThat(startFuture).failsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldUpdateLocalTopologyOnGossipEvent() {
    // given
    final ClusterTopologyManagerImpl clusterTopologyManager =
        startTopologyManager(successInitializer).join();

    // when
    final ClusterTopology topologyFromOtherMember =
        clusterTopologyManager
            .getClusterTopology()
            .join()
            .addMember(MemberId.from("10"), MemberState.initializeAsActive(Map.of()));

    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    final ClusterTopology clusterTopology = clusterTopologyManager.getClusterTopology().join();
    assertThatClusterTopology(clusterTopology).hasOnlyMembers(Set.of(1, 10));
    assertThat(gossipState.get())
        .describedAs("Gossip state contains same topology in topology manager")
        .isEqualTo(clusterTopology);
    assertThat(persistedClusterTopology.getTopology())
        .describedAs("Updated topology is persisted")
        .isEqualTo(clusterTopology);
  }

  @Test
  void shouldNotUpdateLocalTopologyOnGossipEventBeforeInitialization() {
    // given - not started cluster topology manager
    final ClusterTopologyManagerImpl clusterTopologyManager = createTopologyManager();

    // when
    final ClusterTopology topologyFromOtherMember =
        clusterTopologyManager
            .getClusterTopology()
            .join()
            .addMember(MemberId.from("10"), MemberState.initializeAsActive(Map.of()));
    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    assertThat(persistedClusterTopology.getTopology().isUninitialized()).isTrue();
  }

  @Test
  void shouldInitiateClusterTopologyChangeOnGossip() {
    // given
    final ClusterTopologyManagerImpl clusterTopologyManager =
        startTopologyManager(successInitializer, new TestMemberLeaver()).join();

    // when
    final ClusterTopology topologyFromOtherMember =
        initialTopology.startTopologyChange(List.of(new PartitionLeaveOperation(localMemberId, 1)));
    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    Awaitility.await("ClusterTopology is updated after applying topology change operation.")
        .untilAsserted(
            () ->
                ClusterTopologyAssert.assertThatClusterTopology(
                        clusterTopologyManager.getClusterTopology().join())
                    .hasPendingOperationsWithSize(0)
                    .doesNotHaveMember(1));
    assertThat(gossipState.get())
        .describedAs("Updated topology is gossiped")
        .isEqualTo(clusterTopologyManager.getClusterTopology().join());
  }

  @Test
  void shouldContinueClusterTopologyChangeOnRestart() {
    // given
    final ClusterTopology topologyWithPendingOperation =
        initialTopology.startTopologyChange(List.of(new PartitionLeaveOperation(localMemberId, 1)));
    final TopologyInitializer initializer =
        () -> CompletableActorFuture.completed(topologyWithPendingOperation);

    // when
    final ClusterTopologyManagerImpl clusterTopologyManager =
        startTopologyManager(initializer, new TestMemberLeaver()).join();

    // then
    Awaitility.await("ClusterTopology is updated after applying topology change operation.")
        .untilAsserted(
            () ->
                ClusterTopologyAssert.assertThatClusterTopology(
                        clusterTopologyManager.getClusterTopology().join())
                    .hasPendingOperationsWithSize(0)
                    .doesNotHaveMember(1));
    assertThat(gossipState.get())
        .describedAs("Updated topology is gossiped")
        .isEqualTo(clusterTopologyManager.getClusterTopology().join());
  }

  @Test
  void shouldRetryClusterTopologyChangeOperationOnFailure() {
    // given
    final FailingLeaveApplier failingLeaveApplier = new FailingLeaveApplier(localMemberId, 1);
    final ClusterTopologyManagerImpl clusterTopologyManager =
        startTopologyManager(successInitializer, o -> failingLeaveApplier).join();

    // when
    final ClusterTopology topologyFromOtherMember =
        initialTopology.startTopologyChange(List.of(new PartitionLeaveOperation(localMemberId, 1)));
    clusterTopologyManager.onGossipReceived(topologyFromOtherMember);

    // then
    Awaitility.await("ClusterTopology is updated after applying topology change operation.")
        .untilAsserted(
            () ->
                ClusterTopologyAssert.assertThatClusterTopology(
                        clusterTopologyManager.getClusterTopology().join())
                    .hasPendingOperationsWithSize(0)
                    .doesNotHaveMember(1));
    assertThat(gossipState.get())
        .describedAs("Updated topology is gossiped")
        .isEqualTo(clusterTopologyManager.getClusterTopology().join());
  }

  @Test
  void shouldCallListenerWhenInconsistentLocalStateDetected() {
    // given
    final AtomicReference<ClusterTopology> newTopology = new AtomicReference<>();
    final AtomicReference<ClusterTopology> oldTopology = new AtomicReference<>();
    final CompletableFuture<Void> listenerCalled = new CompletableFuture<>();
    final InconsistentTopologyListener listener =
        (received, old) -> {
          newTopology.set(received);
          oldTopology.set(old);
          listenerCalled.complete(null);
        };
    final ClusterTopologyManagerImpl clusterTopologyManager =
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
    final InconsistentTopologyListener listener =
        (received, old) -> {
          listenerCalled.complete(null);
        };
    final var topologyWithMemberLeft =
        initialTopology.updateMember(localMemberId, MemberState::toLeft);
    final ClusterTopologyManagerImpl clusterTopologyManager =
        startTopologyManager(() -> CompletableActorFuture.completed(topologyWithMemberLeft)).join();
    clusterTopologyManager.registerTopologyChangedListener(listener);

    // when
    final var topologyWithoutMember = topologyWithMemberLeft.updateMember(localMemberId, m -> null);
    final var notConflictingTopology =
        new ClusterTopology(
            topologyWithoutMember.version() + 1,
            topologyWithoutMember.members(),
            topologyWithoutMember.lastChange(),
            topologyWithoutMember.pendingChanges());
    clusterTopologyManager.onGossipReceived(notConflictingTopology);

    // then
    assertThat(clusterTopologyManager.getClusterTopology().join())
        .isEqualTo(notConflictingTopology);
    assertThat(listenerCalled).describedAs("Inconsistency listener is never invoked").isNotDone();
  }

  private static final class TestMemberLeaver implements TopologyChangeAppliers {

    @Override
    public MemberOperationApplier getApplier(final TopologyChangeOperation operation) {
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
        final ClusterTopology currentClusterTopology) {
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
