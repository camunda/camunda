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
import io.camunda.zeebe.topology.serializer.ClusterTopologySerializer;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.state.MemberState;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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
  private PersistedClusterTopology persistedClusterTopology;
  private final TopologyInitializer successInitializer =
      () -> {
        try {
          persistedClusterTopology.update(initialTopology);
        } catch (IOException e) {
          return CompletableActorFuture.completedExceptionally(e);
        }
        return CompletableActorFuture.completed(true);
      };

  @BeforeEach
  void init() {
    persistedClusterTopology =
        new PersistedClusterTopology(tempDir.resolve("topology.temp"), serializer);
  }

  private ActorFuture<ClusterTopologyManager> startTopologyManager(
      final TopologyInitializer topologyInitializer) {
    final var clusterTopologyManager = createTopologyManager();

    final ActorFuture<ClusterTopologyManager> startFuture = new TestActorFuture<>();

    clusterTopologyManager
        .start(topologyInitializer)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                startFuture.complete(clusterTopologyManager);
              } else {
                startFuture.completeExceptionally(error);
              }
            });
    return startFuture;
  }

  private ClusterTopologyManager createTopologyManager() {
    final ClusterTopologyManager clusterTopologyManager =
        new ClusterTopologyManager(new TestConcurrencyControl(), persistedClusterTopology);
    clusterTopologyManager.setTopologyGossiper(gossipHandler);
    return clusterTopologyManager;
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
    final TopologyInitializer failingInitializer = () -> CompletableActorFuture.completed(false);
    final var startFuture = startTopologyManager(failingInitializer);

    // when - then
    assertThat(startFuture).failsWithin(Duration.ofMillis(100));
  }

  @Test
  void shouldUpdateLocalTopologyOnGossipEvent() {
    // given
    final ClusterTopologyManager clusterTopologyManager =
        startTopologyManager(successInitializer).join();

    // when
    final ClusterTopology topologyFromOtherMember =
        clusterTopologyManager
            .getClusterTopology()
            .join()
            .addMember(MemberId.from("10"), MemberState.initializeAsActive(Map.of()));
    final var gossipedTopology =
        clusterTopologyManager.onGossipReceived(topologyFromOtherMember).join();

    // then
    final ClusterTopology clusterTopology = clusterTopologyManager.getClusterTopology().join();
    assertThatClusterTopology(clusterTopology).hasOnlyMembers(Set.of(1, 10));
    assertThat(gossipedTopology)
        .describedAs("Gossip state contains same topology in topology manager")
        .isEqualTo(clusterTopology);
    assertThat(persistedClusterTopology.getTopology())
        .describedAs("Updated topology is persisted")
        .isEqualTo(clusterTopology);
  }
}
