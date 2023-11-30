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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.topology.TopologyInitializer.FileInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.GossipInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.InitializerError.PersistedTopologyIsBroken;
import io.camunda.zeebe.topology.TopologyInitializer.StaticInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.SyncInitializer;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import io.camunda.zeebe.topology.state.PartitionState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TopologyInitializerTest {
  @TempDir Path rootDir;
  private final ClusterTopology initialClusterTopology =
      ClusterTopology.init()
          .addMember(
              MemberId.from("10"),
              MemberState.initializeAsActive(Map.of(1, PartitionState.active(1))));

  private PersistedClusterTopology persistedClusterTopology;
  private Path topologyFile;

  @BeforeEach
  void init() {
    topologyFile = rootDir.resolve("topology.temp");
    persistedClusterTopology = new PersistedClusterTopology(topologyFile, new ProtoBufSerializer());
  }

  @Test
  void shouldInitializeFromExistingFile() throws IOException {
    // given
    final var fileInitializer = new FileInitializer(topologyFile, new ProtoBufSerializer());
    // write initial topology to the file
    persistedClusterTopology.update(initialClusterTopology);
    // when
    final var initializeFuture = fileInitializer.initialize();

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
  }

  @Test
  void shouldNotInitializeFromEmptyFile() {
    // given
    final var fileInitializer = new FileInitializer(topologyFile, new ProtoBufSerializer());

    // when
    final var initializeFuture = fileInitializer.initialize();

    // then
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  @Test
  void shouldNotInitializeFromCorruptedFile() throws IOException {
    // given
    final var fileInitializer = new FileInitializer(topologyFile, new ProtoBufSerializer());
    // Corrupt file
    Files.write(
        topologyFile, "random".getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
    // when
    final var initializeFuture = fileInitializer.initialize();

    // then
    assertThat(initializeFuture)
        .failsWithin(Duration.ofMillis(100))
        .withThrowableOfType(ExecutionException.class);
  }

  @Test
  void shouldInitializeFromStaticConfig() {
    // given
    final var initializer = getStaticInitializer();

    // when
    final var initializeFuture = initializer.initialize();

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
  }

  @Test
  void shouldInitializeFromGossip() {
    // given
    final TestTopologyNotifier topologyNotifier = new TestTopologyNotifier();
    final var initializer =
        new GossipInitializer(
            topologyNotifier, persistedClusterTopology, ignore -> {}, new TestConcurrencyControl());

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();

    // Simulate gossip received
    topologyNotifier.updateTopology(initialClusterTopology);

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
  }

  @Test
  void shouldInitializeFromSync() throws IOException {
    // given
    final var knownMembers = List.of(MemberId.from("1"));
    final ActorFuture<ClusterTopology> syncResponseFuture = new TestActorFuture<>();
    final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester = id -> syncResponseFuture;
    final var initializer =
        new SyncInitializer(
            new TestTopologyNotifier(), knownMembers, new TestConcurrencyControl(), syncRequester);

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();
    syncResponseFuture.complete(initialClusterTopology);

    // Simulate gossip received
    persistedClusterTopology.update(initialClusterTopology);

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
    assertThat(persistedClusterTopology.getTopology())
        .describedAs("should update persisted topology after initialization")
        .isEqualTo(initialClusterTopology);
  }

  @Test
  void shouldCompleteFutureButNotInitializeWhenSyncReturnsUninitialized() throws IOException {
    // given
    final var knownMembers = List.of(MemberId.from("1"));
    final ActorFuture<ClusterTopology> syncResponseFuture = new TestActorFuture<>();
    final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester = id -> syncResponseFuture;
    final var initializer =
        new SyncInitializer(
            new TestTopologyNotifier(), knownMembers, new TestConcurrencyControl(), syncRequester);

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();
    syncResponseFuture.complete(ClusterTopology.uninitialized());

    // Simulate gossip received
    persistedClusterTopology.update(initialClusterTopology);

    // then
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  private TopologyInitializer getStaticInitializer() {
    final MemberId member = MemberId.from("10");
    final var partitionId = PartitionId.from("test", 1);
    final Set<PartitionMetadata> partitions =
        Set.of(
            new PartitionMetadata(
                PartitionId.from("test", 1), Set.of(member), Map.of(member, 1), 1, member));
    return new StaticInitializer(
        new StaticConfiguration(
            new ControllablePartitionDistributor().withPartitions(partitions),
            Set.of(member),
            member,
            List.of(partitionId),
            1));
  }

  private static final class TestTopologyNotifier implements TopologyUpdateNotifier {

    private TopologyUpdateListener listener;

    @Override
    public void addUpdateListener(final TopologyUpdateListener listener) {
      this.listener = listener;
    }

    @Override
    public void removeUpdateListener(final TopologyUpdateListener listener) {
      this.listener = null;
    }

    void updateTopology(final ClusterTopology topology) {
      listener.onTopologyUpdated(topology);
    }
  }

  @Nested
  class ChainedInitializerTest {
    @Test
    void shouldInitializeFromFileWhenNotEmpty() throws IOException {
      // given
      final var fileInitializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer())
              .orThen(
                  new GossipInitializer(
                      new TestTopologyNotifier(),
                      persistedClusterTopology,
                      ignore -> {},
                      new TestConcurrencyControl()));
      // write initial topology to the file
      persistedClusterTopology.update(initialClusterTopology);
      // when
      final var initializeFuture = fileInitializer.initialize();

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldInitializeFromGossipWhenFileIsEmpty() {
      // given
      final AtomicReference<ClusterTopology> gossipedTopology = new AtomicReference<>();
      final TestTopologyNotifier topologyUpdateNotifier = new TestTopologyNotifier();
      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer())
              .orThen(
                  new GossipInitializer(
                      topologyUpdateNotifier,
                      persistedClusterTopology,
                      gossipedTopology::set,
                      new TestConcurrencyControl()));

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();
      assertThat(gossipedTopology.get())
          .describedAs("Should gossip uninitialized topology")
          .isEqualTo(ClusterTopology.uninitialized());

      // Simulate gossip received
      topologyUpdateNotifier.updateTopology(initialClusterTopology);

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldFailToInitializeWhenFileIsCorrupted() throws IOException {
      // given
      final AtomicReference<ClusterTopology> gossipedTopology = new AtomicReference<>();
      final TestTopologyNotifier topologyUpdateNotifier = new TestTopologyNotifier();
      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer())
              .orThen(
                  new GossipInitializer(
                      topologyUpdateNotifier,
                      persistedClusterTopology,
                      gossipedTopology::set,
                      new TestConcurrencyControl()));
      // Corrupt file
      Files.write(
          topologyFile, "random".getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);

      // when
      final var initializeFuture = initializer.initialize();
      Awaitility.await().until(initializeFuture::isDone);
      assertThat(initializeFuture.getException()).isInstanceOf(PersistedTopologyIsBroken.class);
    }

    @Test
    void shouldInitializeFromSyncWhenFileIsEmpty() {
      // given
      final var knownMembers = List.of(MemberId.from("1"));
      final ActorFuture<ClusterTopology> syncResponseFuture = new TestActorFuture<>();
      final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester =
          id -> syncResponseFuture;
      final var syncInitializer =
          new SyncInitializer(
              new TestTopologyNotifier(),
              knownMembers,
              new TestConcurrencyControl(),
              syncRequester);

      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer()).orThen(syncInitializer);

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();

      // Simulate gossip received
      syncResponseFuture.complete(initialClusterTopology);

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldInitializeFromStaticWhenFileAndSyncFails() {
      // given
      final var knownMembers = List.of(MemberId.from("1"));
      final ActorFuture<ClusterTopology> syncResponseFuture = new TestActorFuture<>();
      final Function<MemberId, ActorFuture<ClusterTopology>> syncRequester =
          id -> syncResponseFuture;
      final var syncInitializer =
          new SyncInitializer(
              new TestTopologyNotifier(),
              knownMembers,
              new TestConcurrencyControl(),
              syncRequester);

      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer())
              .orThen(syncInitializer)
              .orThen(getStaticInitializer());

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();

      // Simulate gossip received
      syncResponseFuture.complete(ClusterTopology.uninitialized());

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }
  }

  @Nested
  final class RecoveryTest {
    @Test
    void shouldRecoverFromExpectedException() throws IOException {
      // given
      final TopologyInitializer recovery =
          () -> CompletableActorFuture.completed(initialClusterTopology);
      final TopologyInitializer failingInitializer =
          () ->
              CompletableActorFuture.completedExceptionally(
                  new PersistedTopologyIsBroken(topologyFile, null));
      final var recoveringInitializer =
          failingInitializer.recover(PersistedTopologyIsBroken.class, recovery);

      // when
      Files.write(
          topologyFile, "broken".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

      // then
      assertThat(recoveringInitializer.initialize().join()).isEqualTo(initialClusterTopology);
    }

    @Test
    void shouldIgnoreRecoveryOnSuccess() throws IOException {
      // given
      final var recovery = mock(TopologyInitializer.class);
      final var fileInitializer = new FileInitializer(topologyFile, new ProtoBufSerializer());
      final var recoveringInitializer =
          fileInitializer.recover(PersistedTopologyIsBroken.class, recovery);

      // when
      persistedClusterTopology.update(initialClusterTopology);

      // then
      assertThat(recoveringInitializer.initialize().join()).isEqualTo(initialClusterTopology);
      verify(recovery, never()).initialize();
    }

    @Test
    void shouldUseChainedInitializerAfterSkippingRecovery() {
      // given
      final TopologyInitializer unsuccessfulInitializer =
          () -> CompletableActorFuture.completed(ClusterTopology.uninitialized());

      final TopologyInitializer successfulInitializer =
          () -> CompletableActorFuture.completed(initialClusterTopology);

      final TopologyInitializer recoveryInitializer =
          () ->
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("shouldn't happen"));

      // when
      final var chainedInitializer =
          unsuccessfulInitializer
              .recover(PersistedTopologyIsBroken.class, recoveryInitializer)
              .orThen(successfulInitializer);

      // then
      assertThat(chainedInitializer.initialize().join()).isEqualTo(initialClusterTopology);
    }

    @Test
    void shouldUseChainedInitializerAfterUnsuccessfulRecovery() {
      // given
      final TopologyInitializer failingInitializer =
          () ->
              CompletableActorFuture.completedExceptionally(
                  new PersistedTopologyIsBroken(topologyFile, null));

      final TopologyInitializer unsuccessfulRecovery =
          () -> CompletableActorFuture.completed(ClusterTopology.uninitialized());

      final TopologyInitializer finalInitializer =
          () -> CompletableActorFuture.completed(initialClusterTopology);

      // when
      final var chainedInitializer =
          failingInitializer
              .recover(PersistedTopologyIsBroken.class, unsuccessfulRecovery)
              .orThen(finalInitializer);

      // then
      assertThat(chainedInitializer.initialize().join()).isEqualTo(initialClusterTopology);
    }
  }
}
