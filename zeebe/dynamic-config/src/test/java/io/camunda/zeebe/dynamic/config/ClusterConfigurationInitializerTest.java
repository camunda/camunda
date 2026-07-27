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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.FileInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.GossipInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.InitializerError.PersistedConfigurationIsBroken;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.StaticInitializer;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationInitializer.SyncInitializer;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

@Timeout(120)
final class ClusterConfigurationInitializerTest {
  @TempDir Path rootDir;
  private final ClusterConfiguration initialClusterConfiguration =
      ClusterConfiguration.init()
          .addMember(
              MemberId.from("10"),
              MemberState.initializeAsActive(
                  Map.of(1, PartitionState.active(1, DynamicPartitionConfig.init()))));

  private PersistedClusterConfiguration persistedClusterConfiguration;
  private Path topologyFile;

  @BeforeEach
  void init() {
    topologyFile = rootDir.resolve("topology.temp");
    persistedClusterConfiguration =
        PersistedClusterConfiguration.ofFile(topologyFile, new ProtoBufSerializer());
  }

  @Test
  void shouldInitializeFromExistingFile() throws IOException {
    // given
    final var fileInitializer = new FileInitializer(topologyFile, new ProtoBufSerializer());
    // write initial topology to the file
    persistedClusterConfiguration.update(initialClusterConfiguration);
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
    final TestClusterConfigurationNotifier topologyNotifier =
        new TestClusterConfigurationNotifier();
    final var initializer =
        new GossipInitializer(
            topologyNotifier,
            persistedClusterConfiguration,
            ignore -> {},
            new TestConcurrencyControl());

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();

    // Simulate gossip received
    topologyNotifier.updateTopology(initialClusterConfiguration);

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
  }

  @Test
  void shouldInitializeFromSync() throws IOException {
    // given
    final var knownMembers = List.of(MemberId.from("1"));
    final ActorFuture<ClusterConfiguration> syncResponseFuture = new TestActorFuture<>();
    final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester =
        id -> syncResponseFuture;
    final var initializer =
        new SyncInitializer(
            Duration.ofSeconds(5),
            new TestClusterConfigurationNotifier(),
            () -> knownMembers,
            new TestConcurrencyControl(true),
            syncRequester,
            ClusterConfigurationGossiperConfig.DEFAULT_BOOTSTRAP_TIMEOUT);

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();
    syncResponseFuture.complete(initialClusterConfiguration);

    // Simulate gossip received
    persistedClusterConfiguration.update(initialClusterConfiguration);

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
    assertThat(persistedClusterConfiguration.getConfiguration())
        .describedAs("should update persisted topology after initialization")
        .isEqualTo(initialClusterConfiguration);
  }

  @Test
  void shouldCompleteFutureButNotInitializeWhenSyncReturnsUninitialized() throws IOException {
    // given
    final var knownMembers = List.of(MemberId.from("1"));
    final ActorFuture<ClusterConfiguration> syncResponseFuture = new TestActorFuture<>();
    final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester =
        id -> syncResponseFuture;
    final var concurrencyControl = new TestConcurrencyControl(true);
    final var initializer =
        new SyncInitializer(
            Duration.ofSeconds(5),
            new TestClusterConfigurationNotifier(),
            () -> knownMembers,
            concurrencyControl,
            syncRequester,
            ClusterConfigurationGossiperConfig.DEFAULT_BOOTSTRAP_TIMEOUT);

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();
    syncResponseFuture.complete(ClusterConfiguration.uninitialized());
    concurrencyControl.runAll();

    // Simulate gossip received
    persistedClusterConfiguration.update(initialClusterConfiguration);

    // then
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  @Test
  void shouldPreferInitializedTopologyWhenAnotherMemberIsUninitialized() {
    // given
    final var uninitializedMember = MemberId.from("1");
    final var initializedMember = MemberId.from("2");
    final var uninitializedResponse = new TestActorFuture<ClusterConfiguration>();
    final var initializedResponse = new TestActorFuture<ClusterConfiguration>();
    final var syncResponses =
        Map.of(uninitializedMember, uninitializedResponse, initializedMember, initializedResponse);
    final var initializer =
        new SyncInitializer(
            Duration.ofSeconds(5),
            new TestClusterConfigurationNotifier(),
            () -> List.of(uninitializedMember, initializedMember),
            new TestConcurrencyControl(true),
            syncResponses::get,
            ClusterConfigurationGossiperConfig.DEFAULT_BOOTSTRAP_TIMEOUT);

    // when
    final var initializeFuture = initializer.initialize();
    uninitializedResponse.complete(ClusterConfiguration.uninitialized());

    // then
    assertThat(initializeFuture.isDone()).isFalse();

    // when
    initializedResponse.complete(initialClusterConfiguration);

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
  }

  @Test
  void shouldNotFallBackToUninitializedAfterBootstrapTimeoutWhenNoMemberIsReachable() {
    // given - a member that never responds to sync requests
    final var unresponsiveMember = MemberId.from("1");
    final var concurrencyControl = new TestConcurrencyControl(true);
    final var bootstrapTimeout = Duration.ofSeconds(1);
    final var initializer =
        new SyncInitializer(
            Duration.ofSeconds(1),
            new TestClusterConfigurationNotifier(),
            () -> List.of(unresponsiveMember),
            concurrencyControl,
            ignored -> new TestActorFuture<>(),
            bootstrapTimeout);

    // when
    final var initializeFuture = initializer.initialize();

    // then - stays pending while no member has replied
    assertThat(initializeFuture.isDone()).isFalse();

    // when - the bootstrap timeout elapses
    concurrencyControl.runAll();
    final var testTimeout = bootstrapTimeout.toMillis() * 5;
    // there's no easy way to verify that a future does not terminates, so we fail the future
    // with an exception that we check later.
    concurrencyControl.schedule(
        testTimeout, () -> initializeFuture.completeExceptionally(new Exception("expected")));
    concurrencyControl.runAll();

    // then - falls back to uninitialized so the coordinator can use static initialization
    assertThat(initializeFuture)
        .failsWithin(Duration.ofMillis(testTimeout + 1000))
        .withThrowableThat()
        .withMessageContaining("expected");
  }

  @Test
  void shouldFallBackToUninitializedAfterBootstrapTimeoutAfterOneUninitializedReturns() {
    // given - a member that never responds to sync requests
    final var unresponsiveMember = MemberId.from("1");
    final var concurrencyControl = new TestConcurrencyControl(true);
    final var uninitializedResponse = new TestActorFuture<ClusterConfiguration>();
    final var syncResponses = Map.of(unresponsiveMember, uninitializedResponse);
    final var initializer =
        new SyncInitializer(
            Duration.ofSeconds(5),
            new TestClusterConfigurationNotifier(),
            () -> List.of(unresponsiveMember),
            concurrencyControl,
            m -> syncResponses.getOrDefault(m, new TestActorFuture<>()),
            Duration.ofSeconds(1));

    // when
    final var initializeFuture = initializer.initialize();

    // then - stays pending while no member has replied
    assertThat(initializeFuture.isDone()).isFalse();

    // when - the bootstrap timeout elapses
    uninitializedResponse.complete(ClusterConfiguration.uninitialized());
    concurrencyControl.runAll();

    // then - falls back to uninitialized so the coordinator can use static initialization
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  @Test
  void shouldInitializeWhenMemberBecomesInitializedOnRetry() {
    // given
    final var unreachableMember = MemberId.from("1");
    final var recoveringMember = MemberId.from("2");
    final var recoveringMemberCalls = new AtomicInteger();
    final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester =
        id -> {
          if (id.equals(unreachableMember)) {
            return TestActorFuture.failedFuture(new RuntimeException("unreachable"));
          }
          // uninitialized on the first query, then a valid configuration once re-queried
          return recoveringMemberCalls.getAndIncrement() == 0
              ? CompletableActorFuture.completed(ClusterConfiguration.uninitialized())
              : CompletableActorFuture.completed(initialClusterConfiguration);
        };
    final var concurrencyControl = new TestConcurrencyControl(true);
    final var initializer =
        new SyncInitializer(
            Duration.ofSeconds(5),
            new TestClusterConfigurationNotifier(),
            () -> List.of(unreachableMember, recoveringMember),
            concurrencyControl,
            syncRequester,
            ClusterConfigurationGossiperConfig.DEFAULT_BOOTSTRAP_TIMEOUT);

    // when - first round: one member fails, the other is uninitialized -> stays pending
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();

    // when - retry re-queries the members and the recovering member now returns a configuration
    concurrencyControl.runAll();

    // then
    assertThatClusterTopology(initializeFuture.join()).isInitialized();
  }

  @Test
  void shouldCompleteImmediatelyWithNoKnownMembersToSync() {
    // given - a single-node cluster: the coordinator has no other members to sync with
    final var initializer =
        new SyncInitializer(
            Duration.ofSeconds(5),
            new TestClusterConfigurationNotifier(),
            List::of,
            new TestConcurrencyControl(),
            id -> {
              throw new AssertionError("should not query any member when the cluster has none");
            },
            ClusterConfigurationGossiperConfig.DEFAULT_BOOTSTRAP_TIMEOUT);

    // when
    final var initializeFuture = initializer.initialize();

    // then - completes immediately as uninitialized so the coordinator falls back to
    // StaticInitializer, without waiting on anyone (including itself)
    assertThat(initializeFuture.isDone()).isTrue();
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  @Test
  void shouldFallBackToUninitializedAfterBootstrapTimeoutWhenMemberOnlyEverReturnsNull() {
    // given - a member that always answers with null, e.g. a gateway member which is part of
    // cluster membership but never gossips an explicit uninitialized configuration
    final var nullRespondingMember = MemberId.from("gateway-0");
    final var concurrencyControl = new TestConcurrencyControl(true);
    final var bootstrapTimeout = Duration.ofSeconds(1);
    final var initializer =
        new SyncInitializer(
            Duration.ofMillis(50),
            new TestClusterConfigurationNotifier(),
            () -> List.of(nullRespondingMember),
            concurrencyControl,
            ignored -> CompletableActorFuture.completed(null),
            bootstrapTimeout);

    // when
    final var initializeFuture = initializer.initialize();

    // then - stays pending while only null responses are received
    assertThat(initializeFuture.isDone()).isFalse();

    // when - retries run and the bootstrap timeout elapses
    concurrencyControl.runAll();

    // then - falls back to uninitialized instead of polling forever, since a member that only
    // ever returns null never contributes to the "all members confirmed uninitialized" check
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  @Test
  void shouldConvergeQuicklyWhenAllMembersEventuallyConfirmUninitialized() {
    // given - a 3-node cluster (this member + 2 peers); one peer hasn't yet reached the gossip
    // stage on the first poll (returns null), the other already confirms uninitialized
    final var slowMember = MemberId.from("1");
    final var fastMember = MemberId.from("2");
    final var slowMemberCalls = new AtomicInteger();
    final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester =
        id -> {
          if (id.equals(slowMember)) {
            return slowMemberCalls.getAndIncrement() == 0
                ? CompletableActorFuture.completed(null)
                : CompletableActorFuture.completed(ClusterConfiguration.uninitialized());
          }
          return CompletableActorFuture.completed(ClusterConfiguration.uninitialized());
        };
    final var concurrencyControl = new TestConcurrencyControl(true);
    final var bootstrapTimeout = Duration.ofSeconds(30);
    final var initializer =
        new SyncInitializer(
            Duration.ofMillis(50),
            new TestClusterConfigurationNotifier(),
            () -> List.of(slowMember, fastMember),
            concurrencyControl,
            syncRequester,
            bootstrapTimeout);

    // when
    final var initializeFuture = initializer.initialize();
    assertThat(initializeFuture.isDone()).isFalse();

    // then - converges within a single retry round, well before the 30s bootstrap timeout
    concurrencyControl.runAll();
    assertThatClusterTopology(initializeFuture.join()).isUninitialized();
  }

  private ClusterConfigurationInitializer getStaticInitializer() {
    final var member = MemberId.from("10");
    return new StaticInitializer(getStaticConfiguration(member, Set.of(member)));
  }

  private StaticConfiguration getStaticConfiguration(
      final MemberId member, final Set<MemberId> members) {
    final var partitionId = new PartitionId("test", 1);
    final Set<PartitionMetadata> partitions =
        Set.of(
            new PartitionMetadata(
                new PartitionId("test", 1),
                members,
                members.stream().collect(Collectors.toMap(Function.identity(), ignored -> 1)),
                1,
                member));
    return new StaticConfiguration(
        new ControllablePartitionDistributor().withPartitions(partitions),
        members,
        member,
        List.of(partitionId),
        1,
        DynamicPartitionConfig.init(),
        null);
  }

  private static final class TestClusterConfigurationNotifier
      implements ClusterConfigurationUpdateNotifier {

    private ClusterConfigurationUpdateListener listener;

    @Override
    public void addUpdateListener(final ClusterConfigurationUpdateListener listener) {
      this.listener = listener;
    }

    @Override
    public void removeUpdateListener(final ClusterConfigurationUpdateListener listener) {
      this.listener = null;
    }

    void updateTopology(final ClusterConfiguration topology) {
      listener.onClusterConfigurationUpdated(topology);
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
                      new TestClusterConfigurationNotifier(),
                      persistedClusterConfiguration,
                      ignore -> {},
                      new TestConcurrencyControl()));
      // write initial topology to the file
      persistedClusterConfiguration.update(initialClusterConfiguration);
      // when
      final var initializeFuture = fileInitializer.initialize();

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldInitializeFromGossipWhenFileIsEmpty() {
      // given
      final AtomicReference<ClusterConfiguration> gossipedTopology = new AtomicReference<>();
      final TestClusterConfigurationNotifier topologyUpdateNotifier =
          new TestClusterConfigurationNotifier();
      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer())
              .orThen(
                  new GossipInitializer(
                      topologyUpdateNotifier,
                      persistedClusterConfiguration,
                      gossipedTopology::set,
                      new TestConcurrencyControl()));

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();
      assertThat(gossipedTopology.get())
          .describedAs("Should gossip uninitialized topology")
          .isEqualTo(ClusterConfiguration.uninitialized());

      // Simulate gossip received
      topologyUpdateNotifier.updateTopology(initialClusterConfiguration);

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldFailToInitializeWhenFileIsCorrupted() throws IOException {
      // given
      final AtomicReference<ClusterConfiguration> gossipedTopology = new AtomicReference<>();
      final TestClusterConfigurationNotifier topologyUpdateNotifier =
          new TestClusterConfigurationNotifier();
      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer())
              .orThen(
                  new GossipInitializer(
                      topologyUpdateNotifier,
                      persistedClusterConfiguration,
                      gossipedTopology::set,
                      new TestConcurrencyControl()));
      // Corrupt file
      Files.write(
          topologyFile, "random".getBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);

      // when
      final var initializeFuture = initializer.initialize();
      Awaitility.await().until(initializeFuture::isDone);
      assertThat(initializeFuture.getException())
          .isInstanceOf(PersistedConfigurationIsBroken.class);
    }

    @Test
    void shouldInitializeFromSyncWhenFileIsEmpty() {
      // given
      final var knownMembers = List.of(MemberId.from("1"));
      final ActorFuture<ClusterConfiguration> syncResponseFuture = new TestActorFuture<>();
      final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester =
          id -> syncResponseFuture;
      final var syncInitializer =
          new SyncInitializer(
              Duration.ofSeconds(5),
              new TestClusterConfigurationNotifier(),
              () -> knownMembers,
              new TestConcurrencyControl(true),
              syncRequester,
              ClusterConfigurationGossiperConfig.DEFAULT_BOOTSTRAP_TIMEOUT);

      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer()).orThen(syncInitializer);

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();

      // Simulate gossip received
      syncResponseFuture.complete(initialClusterConfiguration);

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }

    @Test
    void shouldInitializeFromStaticWhenFileAndSyncFails() {
      // given
      final var knownMembers = List.of(MemberId.from("1"));
      final ActorFuture<ClusterConfiguration> syncResponseFuture = new TestActorFuture<>();
      final Function<MemberId, ActorFuture<ClusterConfiguration>> syncRequester =
          id -> syncResponseFuture;
      final var concurrencyControl = new TestConcurrencyControl(true);
      final var syncInitializer =
          new SyncInitializer(
              Duration.ofSeconds(5),
              new TestClusterConfigurationNotifier(),
              () -> knownMembers,
              concurrencyControl,
              syncRequester,
              ClusterConfigurationGossiperConfig.DEFAULT_BOOTSTRAP_TIMEOUT);

      final var initializer =
          new FileInitializer(topologyFile, new ProtoBufSerializer())
              .orThen(syncInitializer)
              .orThen(getStaticInitializer());

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isFalse();

      // Simulate gossip received
      syncResponseFuture.complete(ClusterConfiguration.uninitialized());
      concurrencyControl.runAll();

      // then
      assertThatClusterTopology(initializeFuture.join()).isInitialized();
    }
  }

  @Nested
  class ChainedModifierTest {
    @Test
    void shouldRunPartitionDistributorInitializerIfCoordinator() {
      // given
      final var staticConfiguration =
          getStaticConfiguration(
              MemberId.from("1"), Set.of(MemberId.from("0"), MemberId.from("1")));
      final var updatedConfiguration =
          // member 0 was removed, member 1 last remaining in the cluster
          getStaticConfiguration(MemberId.from("1"), Set.of(MemberId.from("1")));
      final var initializer =
          new StaticInitializer(updatedConfiguration)
              .andThen(new PartitionDistributorInitializer(staticConfiguration));

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isTrue();

      // then
      // PartitionDistributionInitializer is run (even though it's not member 0)
      assertThat(initializeFuture.join().partitionDistributorConfig()).isPresent();
    }

    @Test
    void shouldNotRunPartitionDistributorInitializerIfNotCoordinator() {
      // given
      final var staticConfiguration =
          getStaticConfiguration(
              MemberId.from("1"), Set.of(MemberId.from("0"), MemberId.from("1")));
      final var initializer =
          new StaticInitializer(staticConfiguration)
              .andThen(new PartitionDistributorInitializer(staticConfiguration));

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isTrue();

      // then
      // PartitionDistributorInitializer is skipped because member 1 is not the coordinator
      assertThat(initializeFuture.join().partitionDistributorConfig()).isEmpty();
    }

    @Test
    void shouldNotRunModifierIfNotCoordinator() {
      // given
      final var staticConfiguration =
          getStaticConfiguration(
              MemberId.from("1"), Set.of(MemberId.from("0"), MemberId.from("1")));
      final var initializer =
          new StaticInitializer(staticConfiguration)
              .andThen(new ClusterIdInitializer("cluster-id-123", MemberId.from("1")));

      // when
      final var initializeFuture = initializer.initialize();
      assertThat(initializeFuture.isDone()).isTrue();

      // then
      // ClusterIdInitializer is skipped because member 1 is not the coordinator
      assertThat(initializeFuture.join().clusterId()).isEmpty();
    }
  }

  @Nested
  final class RecoveryTest {
    @Test
    void shouldRecoverFromExpectedException() throws IOException {
      // given
      final ClusterConfigurationInitializer recovery =
          () -> CompletableActorFuture.completed(initialClusterConfiguration);
      final ClusterConfigurationInitializer failingInitializer =
          () ->
              CompletableActorFuture.completedExceptionally(
                  new PersistedConfigurationIsBroken(topologyFile, null));
      final var recoveringInitializer =
          failingInitializer.recover(PersistedConfigurationIsBroken.class, recovery);

      // when
      Files.write(
          topologyFile, "broken".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

      // then
      assertThat(recoveringInitializer.initialize().join()).isEqualTo(initialClusterConfiguration);
    }

    @Test
    void shouldIgnoreRecoveryOnSuccess() throws IOException {
      // given
      final var recovery = Mockito.mock(ClusterConfigurationInitializer.class);
      final var fileInitializer = new FileInitializer(topologyFile, new ProtoBufSerializer());
      final var recoveringInitializer =
          fileInitializer.recover(PersistedConfigurationIsBroken.class, recovery);

      // when
      persistedClusterConfiguration.update(initialClusterConfiguration);

      // then
      assertThat(recoveringInitializer.initialize().join()).isEqualTo(initialClusterConfiguration);
      verify(recovery, never()).initialize();
    }

    @Test
    void shouldUseChainedInitializerAfterSkippingRecovery() {
      // given
      final ClusterConfigurationInitializer unsuccessfulInitializer =
          () -> CompletableActorFuture.completed(ClusterConfiguration.uninitialized());

      final ClusterConfigurationInitializer successfulInitializer =
          () -> CompletableActorFuture.completed(initialClusterConfiguration);

      final ClusterConfigurationInitializer recoveryInitializer =
          () ->
              CompletableActorFuture.completedExceptionally(
                  new RuntimeException("shouldn't happen"));

      // when
      final var chainedInitializer =
          unsuccessfulInitializer
              .recover(PersistedConfigurationIsBroken.class, recoveryInitializer)
              .orThen(successfulInitializer);

      // then
      assertThat(chainedInitializer.initialize().join()).isEqualTo(initialClusterConfiguration);
    }

    @Test
    void shouldUseChainedInitializerAfterUnsuccessfulRecovery() {
      // given
      final ClusterConfigurationInitializer failingInitializer =
          () ->
              CompletableActorFuture.completedExceptionally(
                  new PersistedConfigurationIsBroken(topologyFile, null));

      final ClusterConfigurationInitializer unsuccessfulRecovery =
          () -> CompletableActorFuture.completed(ClusterConfiguration.uninitialized());

      final ClusterConfigurationInitializer finalInitializer =
          () -> CompletableActorFuture.completed(initialClusterConfiguration);

      // when
      final var chainedInitializer =
          failingInitializer
              .recover(PersistedConfigurationIsBroken.class, unsuccessfulRecovery)
              .orThen(finalInitializer);

      // then
      assertThat(chainedInitializer.initialize().join()).isEqualTo(initialClusterConfiguration);
    }
  }
}
