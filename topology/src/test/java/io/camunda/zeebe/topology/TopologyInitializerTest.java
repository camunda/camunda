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
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.topology.TopologyInitializer.FileInitializer;
import io.camunda.zeebe.topology.TopologyInitializer.GossipInitializer;
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
    final var fileInitializer = new FileInitializer(persistedClusterTopology);
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
    final var fileInitializer = new FileInitializer(persistedClusterTopology);

    // when
    final var initializeFuture = fileInitializer.initialize();

    // then
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  @Test
  void shouldNotInitializeFromCorruptedFile() throws IOException {
    // given
    final var fileInitializer = new FileInitializer(persistedClusterTopology);
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
    assertThat(persistedClusterTopology.getTopology())
        .describedAs("should update persisted topology after initialization")
        .isEqualTo(initialClusterTopology);
  }

  @Test
  void shouldInitializeFromGossip() throws IOException {
    // given
    final var initializer = new GossipInitializer(persistedClusterTopology, ignore -> {});

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();

    // Simulate gossip received
    persistedClusterTopology.update(initialClusterTopology);

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
            persistedClusterTopology, knownMembers, new TestConcurrencyControl(), syncRequester);

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
            persistedClusterTopology, knownMembers, new TestConcurrencyControl(), syncRequester);

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
    final Set<PartitionMetadata> partitions =
        Set.of(
            new PartitionMetadata(
                PartitionId.from("test", 1), Set.of(member), Map.of(member, 1), 1, member));
    return new StaticInitializer(() -> partitions, persistedClusterTopology);
  }

  @Nested
  class ChainedInitializerTest {
    @Test
    void shouldInitializeFromFileWhenNotEmpty() throws IOException {
      // given
      final var fileInitializer =
          new FileInitializer(persistedClusterTopology)
              .orThen(new GossipInitializer(persistedClusterTopology, ignore -> {}));
      // write initial topology to the file
      persistedClusterTopology.update(initialClusterTopology);
      // when
      final var initializeFuture = fileInitializer.initialize();

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldInitializeFromGossipWhenFileIsEmpty() throws IOException {
      // given
      final AtomicReference<ClusterTopology> gossipedTopology = new AtomicReference<>();
      final var initializer =
          new FileInitializer(persistedClusterTopology)
              .orThen(new GossipInitializer(persistedClusterTopology, gossipedTopology::set));

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();
      assertThat(gossipedTopology.get())
          .describedAs("Should gossip uninitialized topology")
          .isEqualTo(ClusterTopology.uninitialized());

      // Simulate gossip received
      persistedClusterTopology.update(initialClusterTopology);

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldInitializeFromGossipWhenFileIsCorrupted() throws IOException {
      // given
      final AtomicReference<ClusterTopology> gossipedTopology = new AtomicReference<>();
      final var initializer =
          new FileInitializer(persistedClusterTopology)
              .orThen(new GossipInitializer(persistedClusterTopology, gossipedTopology::set));
      // Corrupt file
      Files.write(
          topologyFile, "random".getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();
      assertThat(gossipedTopology.get())
          .describedAs("Should gossip uninitialized topology")
          .isEqualTo(ClusterTopology.uninitialized());

      // Simulate gossip received
      persistedClusterTopology.update(initialClusterTopology);

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
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
              persistedClusterTopology, knownMembers, new TestConcurrencyControl(), syncRequester);

      final var initializer = new FileInitializer(persistedClusterTopology).orThen(syncInitializer);

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
              persistedClusterTopology, knownMembers, new TestConcurrencyControl(), syncRequester);

      final var initializer =
          new FileInitializer(persistedClusterTopology)
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
}
