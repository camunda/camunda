/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.CancelledBootstrapException;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReconfigurationTest {
  private final SingleThreadContext context = new SingleThreadContext("raft-%d");
  private final TestRaftProtocolFactory protocolFactory = new TestRaftProtocolFactory();
  private final List<RaftServer> servers = new LinkedList<>();
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @AfterEach
  void cleanup() {
    for (final var server : servers) {
      server.shutdown().join();
    }
    context.close();
  }

  private static LeaderRole awaitLeader(final RaftServer... servers) {
    //noinspection OptionalGetWithoutIsPresent
    return Awaitility.await("Leader is known")
        .until(() -> getLeader(servers), Optional::isPresent)
        .get();
  }

  private static void awaitNoLeader(final RaftServer... servers) {
    Awaitility.await("There is no leader").until(() -> getLeader(servers), Optional::isEmpty);
  }

  private static void awaitLeaderIsIn(
      final Collection<RaftServer> allServers, final RaftServer... servers) {
    final var serversSet = Arrays.stream(servers).map(RaftServer::name).collect(Collectors.toSet());

    final var deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
    while (getLeaderServer(allServers).map(l -> !serversSet.contains(l.name())).orElse(true)
        && System.currentTimeMillis() < deadline) {
      getLeaderServer(allServers).ifPresent(s -> s.stepDown().join());
      awaitLeader(allServers.toArray(RaftServer[]::new));
    }
  }

  private static Optional<RaftServer> getLeaderServer(final Collection<RaftServer> servers) {
    return servers.stream().filter(RaftServer::isLeader).findAny();
  }

  private static Optional<LeaderRole> getLeader(final RaftServer... servers) {
    return getLeaderServer(Arrays.stream(servers).toList())
        .map(RaftServer::getContext)
        .map(RaftContext::getRaftRole)
        .map(LeaderRole.class::cast);
  }

  private static Optional<RaftServer> getFollower(final RaftServer... servers) {
    return Arrays.stream(servers).filter(RaftServer::isFollower).findAny();
  }

  private static AppendResult appendEntry(final LeaderRole leader) {
    final var result = new AppendResult();
    leader.appendEntry(-1, -1, ByteBuffer.wrap(new byte[0]), result);
    return result;
  }

  private RaftServer createServer(
      final Path dir, final ClusterMembershipService membershipService) {
    return createServer(dir, membershipService, config -> {});
  }

  private RaftServer createServer(
      final Path dir,
      final ClusterMembershipService membershipService,
      final Consumer<RaftPartitionConfig> configCustomizer) {
    final var memberId = membershipService.getLocalMember().id();
    final var protocol = protocolFactory.newServerProtocol(memberId);
    final var storage =
        RaftStorage.builder(meterRegistry)
            .withDirectory(dir.resolve(memberId.toString()).toFile())
            .withSnapshotStore(new TestSnapshotStore(new AtomicReference<>()))
            .withMaxSegmentSize(1024 * 10)
            .build();
    final var partitionConfig =
        new RaftPartitionConfig()
            .setElectionTimeout(Duration.ofMillis(500))
            .setHeartbeatInterval(Duration.ofMillis(100));
    configCustomizer.accept(partitionConfig);
    final var server =
        RaftServer.builder(memberId)
            .withMembershipService(membershipService)
            .withProtocol(protocol)
            .withStorage(storage)
            .withPartitionConfig(partitionConfig)
            .withMeterRegistry(meterRegistry)
            .build();
    servers.add(server);
    return server;
  }

  @Nested
  final class Joining {
    @Test
    void rejoinShouldBeSuccessful(@TempDir final Path tmp) {
      // given - a cluster with 3 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id2, id1));

      // when - m3 joined once
      CompletableFuture.allOf(m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3)).join();
      m3.join(id1, id2).join();

      // then - m3 can join again
      m3.shutdown().join();
      m3.join(id1, id2).join();
    }

    @Test
    void rejoinShouldBeSuccessfulWithSingleReplica(@TempDir final Path tmp) throws IOException {
      // given - a cluster with 3 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));

      // when - m2 joined once
      CompletableFuture.allOf(m1.bootstrap(id1)).join();
      m2.join(id1).join();
      m2.shutdown().join();

      Awaitility.await("1 is not leader").untilAsserted(() -> assertThat(m1.isLeader()).isFalse());

      // then - m2 can join again after restarting
      final var restartedM2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      restartedM2.join(id1).join();
    }

    /**
     * Reproduces <a href="https://github.com/camunda/camunda/issues/56808">#56808</a>: joining a
     * single-member cluster deadlocks permanently when the configuration appended by the leader
     * cannot be replicated to the joiner before the join attempt fails.
     *
     * <p>The leader appends the joint configuration and operates under it immediately
     * (configurations take effect on append), but can never commit it without the joiner's ack.
     * When the joiner gives up and shuts down, as {@code PartitionManagerImpl} does after a failed
     * join, the leader steps down and winning an election in the joint configuration requires the
     * joiner's vote. When PASSIVE members rejected polls and votes, this deadlocked permanently:
     * the retried joiner refused the vote needed to elect the leader that its own join needed. With
     * membership-blind voting, the retried joiner grants the vote while its join attempt keeps
     * retrying, the re-elected leader resumes the in-flight reconfiguration, and the join succeeds.
     */
    @Test
    void joinShouldSucceedWhenRetriedAfterFailedFirstAttempt(@TempDir final Path tmp) {
      // given - a single-member cluster, as after the bootstrap of a scaled-up partition
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      m1.bootstrap(id1).join();
      awaitLeader(m1);

      // when - m2 tries to join while replication from m1 to m2 transiently fails. The join
      // request still reaches the leader m1, which appends the joint configuration, but m2 never
      // receives it and the join attempt fails, either by timing out or, if m1 steps down first,
      // with "Leader stepping down". Like RaftPartitionServer, we pass all members of the
      // partition, including the joining member itself.
      protocolFactory.blockMessagesTo(id2);
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      assertThat(m2.join(id1, id2)).failsWithin(Duration.ofSeconds(30));

      // m2 gives up, like PartitionManagerImpl removes the partition after a failed join
      m2.shutdown().join();

      // m1 operates under the appended joint configuration but cannot commit it and steps down
      awaitNoLeader(m1);

      // then - once connectivity is restored, retrying the join (as the cluster topology
      // coordinator does indefinitely) succeeds: the joiner keeps retrying the join on NO_LEADER
      // and, while the join is in flight, grants the vote that re-elects m1
      protocolFactory.heal(id2);
      final var retriedM2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      assertThat(retriedM2.join(id1, id2))
          .as("m2 can join when retrying after a failed first attempt")
          .succeedsWithin(Duration.ofSeconds(30));
      awaitLeader(m1, retriedM2);
    }

    /**
     * Reproduces the restart aspect of <a
     * href="https://github.com/camunda/camunda/issues/57389">#57389</a>: configurations take effect
     * as soon as they are appended, so a leader that appended a joint configuration must still
     * operate under it after a restart, even though the configuration is not yet committed and thus
     * not persisted in the meta store. Forgetting it would allow electing a leader without the
     * joining member's vote and losing a configuration that the joining member may already have
     * received.
     */
    @Test
    void jointConfigurationSurvivesLeaderRestart(@TempDir final Path tmp) {
      // given - a single-member cluster that appended a joint configuration it cannot commit
      // because replication to the joining member fails
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      m1.bootstrap(id1).join();
      awaitLeader(m1);

      protocolFactory.blockMessagesTo(id2);
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      assertThat(m2.join(id1, id2)).failsWithin(Duration.ofSeconds(30));
      m2.shutdown().join();
      assertThat(m1.getContext().getCluster().getConfiguration().requiresJointConsensus())
          .as("m1 operates under the appended joint configuration")
          .isTrue();

      // when - m1 restarts before the joint configuration is committed. The bootstrap future can
      // only complete once the server is ready, which requires a leader, so don't wait on it yet.
      m1.shutdown().join();
      final var restartedM1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m1Started = restartedM1.bootstrap(id1);

      // then - the joint configuration is recovered from the log and quorum still requires m2,
      // so m1 cannot elect itself alone
      Awaitility.await("the joint configuration is recovered from the log after restart")
          .untilAsserted(
              () ->
                  assertThat(restartedM1.getContext().getCluster().getConfiguration())
                      .isNotNull()
                      .returns(true, Configuration::requiresJointConsensus));
      Awaitility.await("m1 cannot become leader alone under the joint configuration")
          .during(Duration.ofSeconds(2))
          .until(() -> getLeader(restartedM1).isEmpty());

      // and the join succeeds when retried once connectivity is restored
      protocolFactory.heal(id2);
      final var retriedM2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      assertThat(retriedM2.join(id1, id2))
          .as("m2 can join when retrying after m1 restarted")
          .succeedsWithin(Duration.ofSeconds(30));
      awaitLeader(restartedM1, retriedM2);
      assertThat(m1Started).succeedsWithin(Duration.ofSeconds(10));
    }

    @Test
    void canJoinAgainAfterDataloss(@TempDir final Path tmp) throws IOException {
      // given - a cluster with 3 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id2, id1));

      // when - m3 joined once and then joins again after dataloss
      CompletableFuture.allOf(m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3)).join();
      m3.join(id1, id2).join();
      m3.shutdown().join();
      servers.remove(m3);

      FileUtil.deleteFolder(tmp.resolve(id3.toString()));
      Files.createDirectory(tmp.resolve(id3.toString()));
      final var recreatedM3 = createServer(tmp, StaticClusterMembershipService.of(id3, id2, id1));
      recreatedM3.join(id1, id2).join();

      // then - leader can append on m3
      final var leader = awaitLeader(m1, m2);
      final var index = appendEntry(leader).write().join();
      Awaitility.await("All members have committed the entry")
          .untilAsserted(
              () ->
                  assertThat(List.of(m1, m2, recreatedM3))
                      .allSatisfy(
                          server ->
                              assertThat(server.getContext().getCommitIndex()).isEqualTo(index)));
    }

    @Test
    void shouldJoinExistingMembers(@TempDir final Path tmp) {
      // given - a cluster with 3 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();

      // when - a new member joins
      final var id4 = MemberId.from("4");
      final var m4 = createServer(tmp, StaticClusterMembershipService.of(id4, id1, id2, id3));
      m4.join(id1, id2, id3).join();

      // then - all members show a configuration with 4 active members
      final var expected =
          List.of(
              new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id3, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id4, Type.ACTIVE, Instant.now()));

      Awaitility.await("All members have configuration with 4 active members")
          .untilAsserted(
              () ->
                  assertThat(List.of(m1, m2, m3, m4))
                      .allSatisfy(
                          member ->
                              assertThat(member.cluster().getMembers())
                                  .containsExactlyInAnyOrderElementsOf(expected)));
    }

    @Test
    void shouldCommitOnAllMembers(@TempDir final Path tmp) {
      // given - a cluster with 3 members and one new member joining
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");
      final var id4 = MemberId.from("4");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));
      final var m4 = createServer(tmp, StaticClusterMembershipService.of(id4, id1, id2, id3));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();
      m4.join(id1, id2, id3).join();

      // when - appending a new entry
      final var leader = awaitLeader(m1, m2, m3, m4);
      final var index = appendEntry(leader).write().join();

      // then - all members received the entry
      Awaitility.await("All members have committed the entry")
          .untilAsserted(
              () ->
                  assertThat(List.of(m1, m2, m3, m4))
                      .allSatisfy(
                          server ->
                              assertThat(server.getContext().getCommitIndex()).isEqualTo(index)));
    }

    @Test
    void shouldRequireAdjustedQuorum(@TempDir final Path tmp) {
      // given - a cluster with 3 members and two new members joining
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");
      final var id4 = MemberId.from("4");
      final var id5 = MemberId.from("5");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));
      final var m4 = createServer(tmp, StaticClusterMembershipService.of(id4, id1, id2, id3));
      final var m5 = createServer(tmp, StaticClusterMembershipService.of(id5, id1, id2, id3));
      final var allServers = List.of(m1, m2, m3, m4, m5);

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();

      m4.join(id1, id2, id3).join();
      m5.join(id1, id2, id3).join();

      // in case the leader was server 2 or 3, then there is no election.
      // in that case we force the current leader to step down so a new election must take place.
      // when - no quorum possible because three out of five members are down
      awaitLeaderIsIn(allServers, m1, m4, m5);
      m1.shutdown().join();
      m4.shutdown().join();
      m5.shutdown().join();

      // then - cluster will not find a leader because two members are not enough for a quorum
      Awaitility.await("No leader is elected")
          .during(Duration.ofSeconds(5))
          .until(() -> getLeader(m1, m2, m3, m4, m5), Optional::isEmpty);
    }

    @Test
    void shouldFormNewQuorum(@TempDir final Path tmp) {
      // given - a cluster with 3 members and two new members joining
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");
      final var id4 = MemberId.from("4");
      final var id5 = MemberId.from("5");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));
      final var m4 = createServer(tmp, StaticClusterMembershipService.of(id4, id1, id2, id3));
      final var m5 = createServer(tmp, StaticClusterMembershipService.of(id5, id1, id2, id3));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();

      m4.join(id1, id2, id3).join();
      m5.join(id1, id2, id3).join();

      // when - original members fail so that quorum depends on new members
      m1.shutdown().join();
      m2.shutdown().join();

      // then - cluster still has a leader and can commit entries
      final var leader = awaitLeader(m1, m2, m3, m4, m5);
      assertThat(appendEntry(leader).commit()).succeedsWithin(Duration.ofSeconds(1));
    }
  }

  @Nested
  final class Leaving {
    @Test
    void followerCanLeaveCluster(@TempDir final Path tmp) {
      // given - a cluster with 3 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();
      awaitLeader(m1, m2, m3);

      // when - existing member leaves
      final var follower = Stream.of(m1, m2, m3).filter(s -> !s.isLeader()).findAny().orElseThrow();
      final var others = Stream.of(m1, m2, m3).filter(s -> s != follower).toList();
      follower.leave().join();

      // then - all members show a configuration with 2 active members
      final var expected =
          others.stream().map(server -> server.cluster().getLocalMember()).toList();
      Awaitility.await("All members have configuration with 2 active members")
          .untilAsserted(
              () ->
                  assertThat(others)
                      .allSatisfy(
                          member ->
                              assertThat(member.cluster().getMembers())
                                  .containsExactlyInAnyOrderElementsOf(expected)));
    }

    @Test
    void leaderCanLeaveCluster(@TempDir final Path tmp) {
      // given - a cluster with 3 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();
      awaitLeader(m1, m2, m3);

      // when - existing member leaves
      final var leader = Stream.of(m1, m2, m3).filter(RaftServer::isLeader).findAny().orElseThrow();
      final var others = Stream.of(m1, m2, m3).filter(s -> s != leader).toList();
      leader.leave().join();

      // then - all members show a configuration with 2 active members
      final var expected =
          others.stream().map(server -> server.cluster().getLocalMember()).toList();
      assertThat(others)
          .allSatisfy(
              member ->
                  assertThat(member.cluster().getMembers())
                      .containsExactlyInAnyOrderElementsOf(expected));
    }

    @Test
    void leaveIsIdempotent(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));

      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();

      // when - m2 left
      assertThat(m2.leave()).succeedsWithin(Duration.ofSeconds(5));
      appendEntry(awaitLeader(m1)).commit().join();

      // then - m2 can request leave again
      assertThat(m2.leave()).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void canLeaveAgainAfterRestart(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));

      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();

      // when - m2 left
      assertThat(m2.leave()).succeedsWithin(Duration.ofSeconds(5));
      appendEntry(awaitLeader(m1)).commit().join();

      m2.shutdown().join();
      final var m2Restarted = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      final var startFuture = m2Restarted.bootstrap(id1, id2);

      // then - m2 can request leave again
      assertThat(m2Restarted.leave()).succeedsWithin(Duration.ofSeconds(5));
      // bootstrap completes
      assertThat(startFuture)
          .failsWithin(Duration.ofMillis(200))
          .withThrowableOfType(ExecutionException.class)
          .withCauseInstanceOf(CancelledBootstrapException.class);
    }

    @Test
    void shouldLeave2MemberCluster(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));

      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();

      // when - existing member leaves
      m2.leave().join();

      // then - all members show a configuration with 1 active member
      final var expected = List.of(new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()));

      assertThat(m1.cluster().getMembers()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void lastMemberCanLeaveCluster(@TempDir final Path tmp) {
      // given - a cluster with a single member
      final var id1 = MemberId.from("1");
      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1));
      m1.bootstrap(id1).join();

      // commit an entry to ensure that the leader is ready to accept new configuration
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(1));

      // when - the last member leaves, scaling the partition down to zero members
      assertThat(m1.leave()).succeedsWithin(Duration.ofSeconds(5));

      // then - the committed configuration is empty
      assertThat(m1.cluster().getMembers()).isEmpty();
    }

    /**
     * The live scenario of <a href="https://github.com/camunda/camunda/issues/55856">#55856</a>:
     * scale down from two members to zero. After the leader left, the remaining follower holds the
     * single-member configuration, typically only as an uncommitted log entry. It must elect itself
     * based on that configuration, commit it, and then be able to leave as well.
     */
    @Test
    void lastMemberCanLeave(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();
      awaitLeader(m1, m2);
      final var leader = getLeaderServer(List.of(m1, m2)).orElseThrow();
      final var follower = getFollower(m1, m2).orElseThrow();

      // when - the leader leaves first
      leader.leave().join();

      // then - the remaining follower can leave as well. The first attempts may fail with
      // NO_LEADER until the follower elected itself, but must not break the member - the caller
      // retries, like the cluster configuration coordinator does.
      Awaitility.await("the last member can leave")
          .untilAsserted(() -> assertThat(follower.leave()).succeedsWithin(Duration.ofSeconds(2)));
      assertThat(follower.cluster().getMembers()).isEmpty();
    }

    /**
     * Reproduces <a href="https://github.com/camunda/camunda/issues/55856">#55856</a>: when the
     * second-to-last member leaves, the remaining follower acks the new single-member configuration
     * entry but typically never learns that it committed, because the leaving leader steps down as
     * soon as the commit completes the leave. The follower then holds the configuration only as an
     * uncommitted log entry. If it restarts before electing itself, it must recover that
     * configuration from the log instead of reverting to the stored two-member configuration and
     * waiting forever on a quorum that no longer exists.
     */
    @Test
    void lastMemberCanLeaveAfterRestart(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();
      awaitLeader(m1, m2);
      final var leader = getLeaderServer(List.of(m1, m2)).orElseThrow();
      final var follower = getFollower(m1, m2).orElseThrow();
      final var followerId = MemberId.from(follower.name());

      // when - the leader leaves and the follower restarts before it can elect itself and commit
      // the new configuration. Block any straggler messages so the follower cannot learn that the
      // new configuration is already committed.
      leader.leave().join();
      protocolFactory.blockMessagesTo(followerId);
      follower.shutdown().join();
      final var restarted = createServer(tmp, StaticClusterMembershipService.of(followerId));
      final var started = restarted.bootstrap(id1, id2);

      // then - the restarted follower recovers the single-member configuration from its log
      // instead of the stored two-member configuration
      Awaitility.await("the restarted follower recovers the configuration from the log")
          .untilAsserted(
              () ->
                  assertThat(restarted.cluster().getMembers())
                      .containsExactly(
                          new DefaultRaftMember(followerId, Type.ACTIVE, Instant.now())));

      // and it elects itself, becomes ready and can leave, scaling the partition down to zero
      protocolFactory.heal(followerId);
      awaitLeader(restarted);
      assertThat(started).succeedsWithin(Duration.ofSeconds(10));
      assertThat(restarted.leave()).succeedsWithin(Duration.ofSeconds(5));
      assertThat(restarted.cluster().getMembers()).isEmpty();
    }

    @Test
    void cannotLeaveWhenNewConfigurationDoesNotHaveQuorum(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();

      // when -  existing member try to leave, when there is no majority for the new configuration
      m3.shutdown().join();
      // To reduce chances of flakiness ensure that there is a leader before sending leave
      awaitLeader(m1, m2);

      // then
      assertThat(m2.leave())
          .describedAs(
              "Should fail to leave because quorum not available for the new configuration")
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(ExecutionException.class);
    }

    @Test
    void shouldReduceQuorumSize(@TempDir final Path tmp) {
      // given - a cluster with 5 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");
      final var id4 = MemberId.from("4");
      final var id5 = MemberId.from("5");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));
      final var m4 = createServer(tmp, StaticClusterMembershipService.of(id4, id1, id2, id3));
      final var m5 = createServer(tmp, StaticClusterMembershipService.of(id5, id1, id2, id3));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3, id4, id5),
              m2.bootstrap(id1, id2, id3, id4, id5),
              m3.bootstrap(id1, id2, id3, id4, id5),
              m4.bootstrap(id1, id2, id3, id4, id5),
              m5.bootstrap(id1, id2, id3, id4, id5))
          .join();

      // when -- two members leave and one shuts down without leaving

      // commit an entry to ensure that the leader is ready to accept new configuration
      assertThat(appendEntry(awaitLeader(m1, m2, m3, m4, m5)).commit())
          .succeedsWithin(Duration.ofSeconds(1));
      m4.leave().join();
      m4.shutdown().join();

      // commit an entry to ensure that the leader is ready to accept new configuration
      assertThat(appendEntry(awaitLeader(m1, m2, m3, m5)).commit())
          .succeedsWithin(Duration.ofSeconds(1));
      m5.leave().join();
      m5.shutdown().join();

      // shut down m3 without leaving
      m3.shutdown().join();

      // then -- remaining three can elect a leader and commit entries
      final var leader = awaitLeader(m1, m2);
      assertThat(appendEntry(leader).commit()).succeedsWithin(Duration.ofSeconds(1));
    }

    /**
     * The discriminating test for <a href="https://github.com/camunda/camunda/issues/57390">
     * #57390</a>: a leaving member must keep receiving appends/heartbeats for as long as its
     * removal is appended but not yet committed, so its election timer stays quiet and it stays
     * reachable while the outcome is still undecided. Red on unfixed code, where {@code
     * RaftClusterContext#updateConfiguration} prunes the leaving member's context - and thus its
     * place in the leader's replication targets - as soon as the removal is appended.
     */
    @Test
    void leavingMemberKeepsReceivingAppendsUntilConfigurationCommits(@TempDir final Path tmp) {
      // given - a cluster with 3 members and generous timeouts, so holding one follower's ack of
      // the final configuration back for an extended window doesn't itself disrupt the leader or
      // the leaving member (see removedLeaderStepsDownAtCommitNotAtAppend for why)
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");
      final Consumer<RaftPartitionConfig> testTimeouts =
          config -> {
            config.setMaxQuorumResponseTimeout(Duration.ofSeconds(60));
            config.setElectionTimeout(Duration.ofSeconds(3));
          };

      final var m1 =
          createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3), testTimeouts);
      final var m2 =
          createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3), testTimeouts);
      final var m3 =
          createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2), testTimeouts);

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();
      awaitLeader(m1, m2, m3);
      final var leader = getLeaderServer(List.of(m1, m2, m3)).orElseThrow();
      final var followers = Stream.of(m1, m2, m3).filter(s -> s != leader).toList();
      final var leaving = followers.get(0);
      final var leavingId = MemberId.from(leaving.name());
      final var quorumFollower = followers.get(1);
      final var quorumFollowerId = MemberId.from(quorumFollower.name());

      // when - a follower (not the leader) leaves. The final, non-joint configuration is
      // identifiable by an empty oldMembers; once the leader starts disseminating it to the other
      // follower (the one whose ack the new configuration's quorum needs), drop entry-carrying
      // appends to it, so the removal can never commit. Every append exchange is delayed by a few
      // milliseconds, for the same reason as in removedLeaderStepsDownAtCommitNotAtAppend: the
      // in-memory test protocol otherwise completes exchanges fast enough that a member which
      // keeps acknowledging immediately refuels another full round of appends to every member,
      // busy-looping the single raft actor thread - which would also corrupt this test's own
      // append counter for the leaving member.
      final var holdBackCommit = new AtomicBoolean(false);
      final var appendsToLeavingMember = new AtomicInteger();
      final var leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
      leaderProtocol.interceptRequest(
          ConfigureRequest.class,
          (receiver, request) -> {
            // oldMembers().isEmpty() alone is not enough to identify C-new: the *initial*
            // configuration is also non-joint and could resurface here as a retried/straggler
            // ConfigureRequest after this interceptor is registered (e.g. after a dropped
            // configure response, or a term bump). Requiring that the leaving member is
            // already excluded from the new members uniquely identifies the final,
            // post-joint-consensus configuration.
            if (receiver.equals(quorumFollowerId)
                && request.oldMembers().isEmpty()
                && request.newMembers().stream()
                    .noneMatch(member -> member.memberId().equals(leavingId))) {
              holdBackCommit.set(true);
            }
          });
      leaderProtocol.interceptDelivery(
          VersionedAppendRequest.class,
          (receiver, request) -> {
            if (receiver.equals(leavingId)) {
              appendsToLeavingMember.incrementAndGet();
            }
            final var result = new CompletableFuture<Void>();
            CompletableFuture.runAsync(
                () -> {
                  if (receiver.equals(quorumFollowerId)
                      && holdBackCommit.get()
                      && !request.entries().isEmpty()) {
                    result.completeExceptionally(new ConnectException());
                  } else {
                    result.complete(null);
                  }
                },
                CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
            return result;
          });

      final var leaveFuture = leaving.leave();

      // then - while the final configuration is appended but held back from committing, the
      // leaving member keeps receiving appends across several heartbeat intervals, and neither
      // the leave nor the leader's own leadership resolves
      Awaitility.await("the final configuration is appended but not yet committed")
          .until(holdBackCommit::get);
      final var appendsAtHoldBack = appendsToLeavingMember.get();
      Awaitility.await("the leaving member keeps receiving appends")
          .atMost(Duration.ofSeconds(5))
          .until(() -> appendsToLeavingMember.get() >= appendsAtHoldBack + 5);
      assertThat(leader.isLeader()).isTrue();
      assertThat(leaveFuture).isNotDone();

      // when - the configuration is allowed to commit
      holdBackCommit.set(false);

      // then - the leave completes, the leaving member's context is torn down everywhere, and it
      // stops receiving appends
      assertThat(leaveFuture).succeedsWithin(Duration.ofSeconds(10));
      final var remaining = List.of(leader, quorumFollower);
      final var expected =
          remaining.stream().map(server -> server.cluster().getLocalMember()).toList();
      Awaitility.await("the leaving member's context is removed everywhere")
          .untilAsserted(
              () ->
                  assertThat(remaining)
                      .allSatisfy(
                          member ->
                              assertThat(member.cluster().getMembers())
                                  .containsExactlyInAnyOrderElementsOf(expected)));
      assertThat(leader.getContext().getCluster().getMemberContext(leavingId)).isNull();
    }

    /**
     * Pins existing behavior against regression: a removed leader steps down only once the removal
     * commits, not as soon as it is appended. Role transitions for demotions/removals are driven by
     * the commit-index hook in {@code RaftContext#setCommitIndex}, not by the append-time
     * membership bookkeeping in {@code RaftClusterContext#updateConfiguration} - so a leaving
     * leader keeps leading and replicating for as long as the removal is uncommitted.
     */
    @Test
    void removedLeaderStepsDownAtCommitNotAtAppend(@TempDir final Path tmp) {
      // given - a cluster with 3 members and a long quorum response timeout, so the leader does
      // not suspect a network partition and step down early while this test deliberately holds
      // back one member's acknowledgement of the final configuration
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");
      // A held-back follower is not the only one affected while the removal is uncommitted: any
      // *other* follower that catches up to the removing configuration entry stops recognizing
      // the (about-to-be-removed) leader via RaftClusterContext#getMember, because that lookup is
      // backed by remoteMemberContexts, which is pruned at append time on unfixed code. Its
      // election timer then never gets reset by further heartbeats from that leader, and it calls
      // its own election after one electionTimeout. Widen the election timeout so that neither
      // follower's timer fires within this test's observation window.
      final Consumer<RaftPartitionConfig> testTimeouts =
          config -> {
            config.setMaxQuorumResponseTimeout(Duration.ofSeconds(60));
            config.setElectionTimeout(Duration.ofSeconds(3));
          };

      final var m1 =
          createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3), testTimeouts);
      final var m2 =
          createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3), testTimeouts);
      final var m3 =
          createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2), testTimeouts);

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();
      awaitLeader(m1, m2, m3);
      final var leader = getLeaderServer(List.of(m1, m2, m3)).orElseThrow();
      final var leaderId = MemberId.from(leader.name());
      final var followers = Stream.of(m1, m2, m3).filter(s -> s != leader).toList();
      final var heldBackFollower = followers.get(0);
      final var heldBackFollowerId = MemberId.from(heldBackFollower.name());

      // when - the leader leaves. The final, non-joint configuration is identifiable by an empty
      // oldMembers (the joint configuration that precedes it always carries the current members
      // as oldMembers); once the leader starts disseminating it to one follower, drop entry-
      // carrying appends to that follower, so its match index can never reach the new
      // configuration's index and the removal can never commit. Empty heartbeats keep flowing so
      // the follower is not falsely suspected unreachable and does not call an election itself.
      // Every append exchange (to either follower) is delayed by a few milliseconds: the in-memory
      // test protocol otherwise completes them fast enough that a follower which keeps
      // acknowledging immediately refuels another full round of appends to every member (see
      // LeaderAppender#recordHeartbeat -> sendHeartbeats), busy-looping the single raft actor
      // thread. A few milliseconds is negligible against this test's assertions, which all operate
      // on a scale of several heartbeat intervals or more.
      final var holdBackCommit = new AtomicBoolean(false);
      final var leaderProtocol = (TestRaftServerProtocol) leader.getContext().getProtocol();
      leaderProtocol.interceptRequest(
          ConfigureRequest.class,
          (receiver, request) -> {
            // oldMembers().isEmpty() alone is not enough to identify C-new: the *initial*
            // configuration is also non-joint and could resurface here as a retried/straggler
            // ConfigureRequest after this interceptor is registered (e.g. after a dropped
            // configure response, or a term bump). Requiring that the leaving leader is
            // already excluded from the new members uniquely identifies the final,
            // post-joint-consensus configuration.
            if (receiver.equals(heldBackFollowerId)
                && request.oldMembers().isEmpty()
                && request.newMembers().stream()
                    .noneMatch(member -> member.memberId().equals(leaderId))) {
              holdBackCommit.set(true);
            }
          });
      leaderProtocol.interceptDelivery(
          VersionedAppendRequest.class,
          (receiver, request) -> {
            final var result = new CompletableFuture<Void>();
            CompletableFuture.runAsync(
                () -> {
                  if (receiver.equals(heldBackFollowerId)
                      && holdBackCommit.get()
                      && !request.entries().isEmpty()) {
                    result.completeExceptionally(new ConnectException());
                  } else {
                    result.complete(null);
                  }
                },
                CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
            return result;
          });

      final var leaveFuture = leader.leave();

      // then - while the final configuration is appended but held back from committing, the
      // removed leader keeps leading and heartbeating instead of stepping down immediately
      Awaitility.await("the final configuration is appended but not yet committed")
          .until(holdBackCommit::get);
      Awaitility.await("the leader keeps leading while the removal is uncommitted")
          .during(Duration.ofMillis(800))
          .until(leader::isLeader);
      assertThat(leaveFuture).isNotDone();

      // when - the configuration is allowed to commit
      holdBackCommit.set(false);

      // then - only now does the removed leader step down, the leave completes, and the
      // remaining members elect a new leader among themselves
      assertThat(leaveFuture).succeedsWithin(Duration.ofSeconds(10));
      Awaitility.await("the removed leader has stepped down to inactive")
          .untilAsserted(
              () ->
                  assertThat(leader.cluster().getLocalMember().getType())
                      .isEqualTo(RaftMember.Type.INACTIVE));
      awaitLeader(followers.toArray(RaftServer[]::new));
      final var expected =
          followers.stream().map(server -> server.cluster().getLocalMember()).toList();
      assertThat(followers)
          .allSatisfy(
              member ->
                  assertThat(member.cluster().getMembers())
                      .containsExactlyInAnyOrderElementsOf(expected));
    }
  }

  @Nested
  final class Reconfiguring {
    @Test
    void shouldRejectConfigurationWithoutActiveMembers(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));

      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();

      // commit an entry to ensure that the leader is ready to accept new configuration
      assertThat(appendEntry(awaitLeader(m1, m2)).commit()).succeedsWithin(Duration.ofSeconds(1));

      final var leader = getLeaderServer(List.of(m1, m2)).orElseThrow();
      final var leaderId = leader.cluster().getLocalMember().memberId();
      final var configuration = leader.getContext().getCluster().getConfiguration();

      // when - requesting a configuration where no member is ACTIVE
      final var allPassive =
          List.<RaftMember>of(
              new DefaultRaftMember(id1, Type.PASSIVE, Instant.now()),
              new DefaultRaftMember(id2, Type.PASSIVE, Instant.now()));
      final var response =
          protocolFactory
              .newServerProtocol(MemberId.from("test-client"))
              .reconfigure(
                  leaderId,
                  ReconfigureRequest.builder()
                      .withIndex(configuration.index())
                      .withTerm(configuration.term())
                      .withMembers(allPassive)
                      .from(leaderId.id())
                      .build());

      // then - the reconfiguration is rejected and the cluster remains functional
      assertThat(response)
          .succeedsWithin(Duration.ofSeconds(5))
          .satisfies(
              reconfigureResponse -> {
                assertThat(reconfigureResponse.status()).isEqualTo(Status.ERROR);
                assertThat(reconfigureResponse.error().type())
                    .isEqualTo(RaftError.Type.CONFIGURATION_ERROR);
              });
      assertThat(appendEntry(awaitLeader(m1, m2)).commit()).succeedsWithin(Duration.ofSeconds(1));
    }

    @Test
    void canElectLeaderWithPassiveMemberWhenOneActiveMemberIsDown(@TempDir final Path tmp) {
      // given - a cluster [1A, 2A, 3A] extended with an unreachable PASSIVE member 4
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");
      final var id4 = MemberId.from("4");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();

      // commit an entry to ensure that the leader is ready to accept new configuration
      assertThat(appendEntry(awaitLeader(m1, m2, m3)).commit())
          .succeedsWithin(Duration.ofSeconds(1));

      final var leader = getLeaderServer(List.of(m1, m2, m3)).orElseThrow();
      final var leaderId = leader.cluster().getLocalMember().memberId();
      final var configuration = leader.getContext().getCluster().getConfiguration();
      final var withPassiveMember =
          List.<RaftMember>of(
              new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id3, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id4, Type.PASSIVE, Instant.now()));
      final var response =
          protocolFactory
              .newServerProtocol(MemberId.from("test-client"))
              .reconfigure(
                  leaderId,
                  ReconfigureRequest.builder()
                      .withIndex(configuration.index())
                      .withTerm(configuration.term())
                      .withMembers(withPassiveMember)
                      .from(leaderId.id())
                      .build());
      assertThat(response)
          .succeedsWithin(Duration.ofSeconds(10))
          .satisfies(
              reconfigureResponse -> assertThat(reconfigureResponse.status()).isEqualTo(Status.OK));

      // when - one ACTIVE follower is down and the leader steps down
      final var follower = Stream.of(m1, m2, m3).filter(s -> !s.isLeader()).findAny().orElseThrow();
      final var remaining = Stream.of(m1, m2, m3).filter(s -> s != follower).toList();
      follower.shutdown().join();
      getLeaderServer(remaining).orElseThrow().stepDown().join();

      // then - the two remaining ACTIVE members can elect a leader and commit entries: the
      // PASSIVE member must not count towards the vote or commit quorum
      final var newLeader = awaitLeader(remaining.toArray(RaftServer[]::new));
      assertThat(appendEntry(newLeader).commit()).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void doesNotCommitConfigurationWhileVotingMemberIsUnreachable(@TempDir final Path tmp) {
      // given - a single member cluster [1A] with an unreachable PASSIVE member 2
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      m1.bootstrap(id1).join();

      // commit an entry to ensure that the leader is ready to accept new configuration
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(1));

      final var client = protocolFactory.newServerProtocol(MemberId.from("test-client"));
      final var configuration = m1.getContext().getCluster().getConfiguration();
      final var withPassiveMember =
          List.<RaftMember>of(
              new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id2, Type.PASSIVE, Instant.now()));
      assertThat(
              client.reconfigure(
                  id1,
                  ReconfigureRequest.builder()
                      .withIndex(configuration.index())
                      .withTerm(configuration.term())
                      .withMembers(withPassiveMember)
                      .from(id1.id())
                      .build()))
          .describedAs("Adding a PASSIVE member commits without its ack")
          .succeedsWithin(Duration.ofSeconds(10))
          .satisfies(
              reconfigureResponse -> assertThat(reconfigureResponse.status()).isEqualTo(Status.OK));

      // when - promoting the unreachable member to ACTIVE, making it a voting member of the new
      // configuration
      final var commitIndexBeforePromotion = m1.getContext().getCommitIndex();
      final var committedConfiguration = m1.getContext().getCluster().getConfiguration();
      final var promoted =
          List.<RaftMember>of(
              new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()));
      final var response =
          client.reconfigure(
              id1,
              ReconfigureRequest.builder()
                  .withIndex(committedConfiguration.index())
                  .withTerm(committedConfiguration.term())
                  .withMembers(promoted)
                  .from(id1.id())
                  .build());

      // then - the configuration does not commit without the new voting member's ack: the leader
      // eventually steps down instead of committing governed solely by the old configuration
      Awaitility.await("Leader steps down because the new configuration cannot commit")
          .atMost(Duration.ofSeconds(30))
          .until(() -> !m1.isLeader());
      assertThat(m1.getContext().getCommitIndex()).isEqualTo(commitIndexBeforePromotion);
      Awaitility.await("Reconfigure request completes")
          .atMost(Duration.ofSeconds(30))
          .until(response::isDone);
      assertThat(response.isCompletedExceptionally() || response.join().status() == Status.ERROR)
          .describedAs("Reconfigure request must not succeed")
          .isTrue();
    }
  }

  @Nested
  class ForceConfigureTest {
    final MemberId id1 = MemberId.from("1");
    final MemberId id2 = MemberId.from("2");
    final MemberId id3 = MemberId.from("3");
    final MemberId id4 = MemberId.from("4");
    @TempDir private Path tmp;
    private RaftServer m1;
    private RaftServer m2;
    private RaftServer m3;
    private RaftServer m4;

    @BeforeEach
    void startServers() {
      m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3, id4));
      m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3, id4));
      m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2, id4));
      m4 = createServer(tmp, StaticClusterMembershipService.of(id4, id1, id2, id3));
      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3, id4),
              m2.bootstrap(id1, id2, id3, id4),
              m3.bootstrap(id1, id2, id3, id4),
              m4.bootstrap(id1, id2, id3, id4))
          .join();
      awaitLeader(m1, m2, m3, m4);
    }

    @Test
    void shouldForceConfigureWhenMembersToRemoveAreActive() {
      // when
      m2.forceConfigure(newMembers()).join();

      // then
      // leader must be one of m1 or m2
      awaitLeader(m1, m2);
      assertThat(List.of(m1, m2))
          .allSatisfy(
              m ->
                  assertThat(m.cluster().getMembers())
                      .describedAs("Force configuration should have only two members")
                      .containsExactlyInAnyOrderElementsOf(
                          Set.of(
                              new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
                              new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()))));
    }

    @Test
    void shouldForceConfigureWhenRemovedMembersAreUnreachable() {
      // when
      m3.shutdown().join();
      m4.shutdown().join();
      m2.forceConfigure(newMembers()).join();

      // then
      // leader must be one of m1 or m2
      awaitLeader(m1, m2);
      assertThat(List.of(m1, m2))
          .allSatisfy(
              m ->
                  assertThat(m.cluster().getMembers())
                      .describedAs("Force configuration should have only two members")
                      .containsExactlyInAnyOrderElementsOf(
                          Set.of(
                              new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
                              new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()))));
    }

    @Test
    void shouldForceConfigureIfOnlyOneRemainingMember() {
      // when
      m2.shutdown().join();
      m3.shutdown().join();
      m4.shutdown().join();
      m1.forceConfigure(Map.of(id1, Type.ACTIVE)).join();

      // then
      awaitLeader(m1);

      assertThat(m1.cluster().getMembers())
          .describedAs("Force configuration should have only one members")
          .containsExactlyInAnyOrderElementsOf(
              Set.of(new DefaultRaftMember(id1, Type.ACTIVE, Instant.now())));
    }

    @Test
    void shouldFailForceConfigurationIfOneMemberUnreachable() {
      // when
      m2.shutdown().join();

      // then
      assertThat(m1.forceConfigure(newMembers()))
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(ExecutionException.class)
          .withMessageContaining(
              "Failed to force configure because not all members acknowledged the request.");
    }

    @Test
    void shouldForceConfigureWhenRetriedAfterFailure() {
      // given
      m2.shutdown().join();
      final CompletableFuture<RaftServer> firstAttempt = m1.forceConfigure(newMembers());
      assertThat(firstAttempt)
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(ExecutionException.class)
          .withMessageContaining(
              "Failed to force configure because not all members acknowledged the request.");

      // when

      // restart m2
      final var m2Restarted =
          createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3, id4));
      m2Restarted.bootstrap(id1, id2, id3, id4).join();

      // then
      final CompletableFuture<RaftServer> secondAttempt = m1.forceConfigure(newMembers());
      assertThat(secondAttempt).succeedsWithin(Duration.ofSeconds(10));
    }

    @Test
    void canCommitNewEventsAfterForceConfigure() {
      // when
      m2.forceConfigure(newMembers()).join();
      m3.shutdown().join();
      m4.shutdown().join();
      final var leader = awaitLeader(m1, m2);
      final var commitFuture = appendEntry(leader).commit();

      // then
      assertThat(commitFuture).succeedsWithin(Duration.ofMillis(1000));
    }

    @Test
    void shouldReconfigureViaAnOutDatedFollower() {
      // given
      m2.shutdown().join();
      final var leader = awaitLeader(m1, m3, m4);
      appendEntry(leader).commit().join();
      m3.shutdown().join();
      m4.shutdown().join();

      // when
      awaitNoLeader(m1);
      // no leader when m2 restarts. So its state is outdated
      final var m2Restarted =
          createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3, id4));
      m2Restarted.bootstrap(id1, id2, id3, id4);
      m2Restarted.forceConfigure(newMembers()).join();

      // then
      awaitLeader(m1, m2Restarted);
    }

    @Test
    void forceReconfigureIsIdempotentWhenRetriedViaAFollower() {
      // given
      m2.forceConfigure(newMembers()).join();
      m3.shutdown().join();
      m4.shutdown().join();
      awaitLeader(m1, m2);
      Awaitility.await("Both members have come out of force configuration")
          .untilAsserted(
              () -> {
                assertThat(m2.getContext().getCluster().getConfiguration().force()).isFalse();
                assertThat(m1.getContext().getCluster().getConfiguration().force()).isFalse();
              });

      // when
      final var follower = getFollower(m1, m2).orElseThrow();
      follower.forceConfigure(newMembers()).join();

      // then
      Awaitility.await("Both members have come out of force configuration")
          .untilAsserted(
              () -> {
                assertThat(m2.getContext().getCluster().getConfiguration().force())
                    .describedAs("Member 2 has come out of force configuration")
                    .isFalse();
                assertThat(m1.getContext().getCluster().getConfiguration().force())
                    .describedAs("Member 1 has come out of force configuration")
                    .isFalse();
              });
    }

    @Test
    void forceReconfigureIsIdempotentWhenRetriedViaLeader() {
      // given
      m2.forceConfigure(newMembers()).join();
      m3.shutdown().join();
      m4.shutdown().join();
      awaitLeader(m1, m2);
      Awaitility.await("Both members have come out of force configuration")
          .untilAsserted(
              () -> {
                assertThat(m2.getContext().getCluster().getConfiguration().force()).isFalse();
                assertThat(m1.getContext().getCluster().getConfiguration().force()).isFalse();
              });

      // when
      final var leader = Stream.of(m1, m2).filter(RaftServer::isLeader).findAny().orElseThrow();
      leader.forceConfigure(newMembers()).join();

      // then
      Awaitility.await("Both members have come out of force configuration")
          .untilAsserted(
              () -> {
                assertThat(m2.getContext().getCluster().getConfiguration().force())
                    .describedAs("Member 2 has come out of force configuration")
                    .isFalse();
                assertThat(m1.getContext().getCluster().getConfiguration().force())
                    .describedAs("Member 1 has come out of force configuration")
                    .isFalse();
              });
    }

    private Map<MemberId, Type> newMembers() {
      return Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE);
    }
  }
}
