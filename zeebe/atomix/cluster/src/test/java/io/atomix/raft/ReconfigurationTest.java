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
import io.atomix.raft.protocol.JoinRequest;
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

  /**
   * Drops entry-carrying append requests from {@code sender} to {@code receiver} while {@code
   * enabled}. Heartbeats still pass, so the receiver keeps its leader and only the entries stay
   * unacknowledged; the drop completes asynchronously so a failing append does not refuel the next
   * one on the single raft thread.
   */
  private static void dropEntryAppendsTo(
      final RaftServer sender, final MemberId receiver, final AtomicBoolean enabled) {
    final var protocol = (TestRaftServerProtocol) sender.getContext().getProtocol();
    protocol.interceptDelivery(
        VersionedAppendRequest.class,
        (target, request) -> {
          final var result = new CompletableFuture<Void>();
          CompletableFuture.runAsync(
              () -> {
                if (enabled.get() && target.equals(receiver) && !request.entries().isEmpty()) {
                  result.completeExceptionally(new ConnectException("append dropped"));
                } else {
                  result.complete(null);
                }
              },
              CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
          return result;
        });
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
     * A partition that is scaled from one to two replicas has a single assisting member, so one
     * transport error is enough to leave the join without another member to try. The join must keep
     * retrying until its deadline passes instead of failing immediately.
     */
    @Test
    void joinShouldSucceedWhenFirstRequestFailsAtTransportLevel(@TempDir final Path tmp) {
      // given - a single-member cluster, as after the bootstrap of a scaled-up partition
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      m1.bootstrap(id1).join();
      awaitLeader(m1);

      // m1 is the only member that can assist the join: like RaftPartitionServer, we pass all
      // members of the partition and the joiner filters itself out
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));

      // when - the connection fails before the first join request is delivered, and connectivity
      // returns well within the configuration change timeout
      final var joinRequests = new AtomicInteger();
      final var joinerProtocol = (TestRaftServerProtocol) m2.getContext().getProtocol();
      joinerProtocol.interceptRequest(
          JoinRequest.class,
          (final JoinRequest request) -> {
            if (joinRequests.incrementAndGet() == 1) {
              return CompletableFuture.failedFuture(new ConnectException());
            }
            return CompletableFuture.completedFuture(null);
          });

      // then - the join still succeeds because it is retried while its deadline has budget left
      assertThat(m2.join(id1, id2))
          .as("m2 can join although its first join request failed at the transport level")
          .succeedsWithin(Duration.ofSeconds(30));
      assertThat(joinRequests).as("the join request was retried").hasValueGreaterThan(1);
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

    /**
     * Reproduces <a href="https://github.com/camunda/camunda/issues/60086">#60086</a>: a leader
     * that recovers an uncommitted joint configuration must commit it before it appends the final
     * configuration, as the normal reconfiguration path does. Appending the final configuration
     * right away lets it, and the joint entry below it, commit under the new members' quorum alone.
     * For a two-member cluster scaling down to one, that quorum is the leader by itself, so the
     * leaving member's removal would commit without the leaving member having acknowledged
     * anything.
     */
    @Test
    void recoveredJointConfigurationCommitsBeforeTheFinalOne(@TempDir final Path tmp) {
      // given - a two-member cluster in which m2 leaves, but appends to m2 are dropped so the joint
      // configuration {1,2} -> {1} cannot commit. m2 still answers polls and votes, so m1 can be
      // elected under the joint configuration.
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();
      awaitLeaderIsIn(servers, m1);

      final var dropAppendsToM2 = new AtomicBoolean(true);
      dropEntryAppendsTo(m1, id2, dropAppendsToM2);
      m2.leave();
      Awaitility.await("m1 appended the joint configuration")
          .untilAsserted(
              () ->
                  assertThat(m1.getContext().getCluster().getConfiguration())
                      .returns(true, Configuration::requiresJointConsensus));

      // when - m1 restarts, recovers the joint configuration from its log and is elected again
      m1.shutdown().join();
      final var restartedM1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      dropEntryAppendsTo(restartedM1, id2, dropAppendsToM2);
      final var m1Started = restartedM1.bootstrap(id1, id2);
      awaitLeader(restartedM1);

      // then - the final configuration is not appended while the joint one cannot commit
      Awaitility.await("m1 stays in joint consensus while m2 cannot acknowledge the joint entry")
          .during(Duration.ofSeconds(3))
          .untilAsserted(
              () ->
                  assertThat(restartedM1.getContext().getCluster().getConfiguration())
                      .returns(true, Configuration::requiresJointConsensus));

      // and the leave completes once m2 acknowledges the joint configuration
      dropAppendsToM2.set(false);
      Awaitility.await("m1 left joint consensus with m2 removed")
          .untilAsserted(
              () -> {
                final var configuration = restartedM1.getContext().getCluster().getConfiguration();
                assertThat(configuration.requiresJointConsensus()).isFalse();
                assertThat(configuration.newMembers())
                    .extracting(RaftMember::memberId)
                    .containsExactly(id1);
                assertThat(restartedM1.getContext().getCommitIndex())
                    .isGreaterThanOrEqualTo(configuration.index());
              });
      assertThat(m1Started).succeedsWithin(Duration.ofSeconds(10));
    }

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

    /**
     * A member that never acknowledged an append has a match index of 0, so the promotion catch-up
     * gate rejects promoting it before anything is appended: the leader's view of its replication
     * lag cannot be trusted yet. The append-time protection - an appended promotion must not commit
     * without the promoted member's acknowledgement - is pinned separately by {@link
     * TwoPhaseReconfiguration#shouldNotCommitPromotionWhilePromotedMemberUnreachable}, where the
     * member catches up before it becomes unreachable.
     */
    @Test
    void shouldRejectPromotingUnreachableMember(@TempDir final Path tmp) {
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

      // when - promoting the unreachable member to ACTIVE, which would make it a voting member
      // of the new configuration
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

      // then - the promotion is rejected outright because the member never acknowledged anything,
      // nothing is appended, and the leader keeps leading and committing
      assertThat(response)
          .succeedsWithin(Duration.ofSeconds(10))
          .satisfies(
              reconfigureResponse -> {
                assertThat(reconfigureResponse.status()).isEqualTo(Status.ERROR);
                assertThat(reconfigureResponse.error().type())
                    .isEqualTo(RaftError.Type.CONFIGURATION_ERROR);
                assertThat(reconfigureResponse.error().message()).contains("not caught up");
              });
      assertThat(m1.getContext().getCommitIndex()).isEqualTo(commitIndexBeforePromotion);
      assertThat(m1.isLeader()).isTrue();
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(5));
    }
  }

  @Nested
  final class TwoPhaseReconfiguration {
    /**
     * Pins the PROMOTABLE catch-up contract: the leader ships its uncommitted tail to a PROMOTABLE
     * member through an uncommitted reader (see {@link
     * io.atomix.raft.cluster.impl.RaftMemberContext}), and the member appends and acknowledges that
     * tail even though the entries are not committed, so it can reach the leader's last index and
     * become eligible for promotion.
     */
    @Test
    void shouldReplicateUncommittedEntriesToPromotableMember(@TempDir final Path tmp) {
      // given - a cluster [1A, 2A] with a PROMOTABLE member 3, and generous timeouts so that
      // holding back the ACTIVE follower's acks doesn't make the leader step down within this
      // test's observation window (see removedLeaderStepsDownAtCommitNotAtAppend for why)
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

      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();
      assertThat(appendEntry(awaitLeader(m1, m2)).commit()).succeedsWithin(Duration.ofSeconds(5));

      final var m3 =
          createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2), testTimeouts);
      assertThat(m3.join(Type.PROMOTABLE, List.of(id1, id2, id3)))
          .succeedsWithin(Duration.ofSeconds(30));

      final var leader = getLeaderServer(List.of(m1, m2)).orElseThrow();
      final var follower = Stream.of(m1, m2).filter(s -> s != leader).findAny().orElseThrow();
      final var followerId = MemberId.from(follower.name());

      // when - nothing new can commit because entry-carrying appends to the only ACTIVE follower
      // fail (the ACTIVE commit quorum is 2 out of 2, the PROMOTABLE member is in no quorum), and
      // the leader appends several new entries
      interceptEntryAppends(leader, followerId, new AtomicBoolean(true));

      final var commitIndexBefore = leader.getContext().getCommitIndex();
      final var leaderRole = getLeader(leader).orElseThrow();
      for (int i = 0; i < 5; i++) {
        appendEntry(leaderRole).write().join();
      }
      final var lastIndex = lastIndex(leader);
      assertThat(lastIndex).isGreaterThan(commitIndexBefore);

      // then - the PROMOTABLE member catches up to the leader's last index even though the
      // leader's commit index stays behind it
      Awaitility.await("the promotable member catches up to the leader's last uncommitted index")
          .untilAsserted(() -> assertThat(lastIndex(m3)).isEqualTo(lastIndex));
      assertThat(leader.getContext().getCommitIndex())
          .describedAs("the appended entries must not have committed")
          .isEqualTo(commitIndexBefore);
    }

    @Test
    void shouldJoinAsPromotableAndBecomeActiveAfterPromotion(@TempDir final Path tmp) {
      // given - a single-member cluster
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      m1.bootstrap(id1).join();
      // commit an entry to ensure that the leader is ready to accept new configurations
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(5));

      // when - m2 joins as PROMOTABLE
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      assertThat(m2.join(Type.PROMOTABLE, List.of(id1, id2)))
          .succeedsWithin(Duration.ofSeconds(30));

      // then - the leader's configuration has m2 as a PROMOTABLE, non-voting member
      assertThat(memberTypes(m1)).containsEntry(id2, Type.PROMOTABLE);

      // when - m2 caught up to the leader and is promoted to ACTIVE
      awaitCaughtUp(m1, m2);
      awaitSameConfiguration(m1, m2);
      assertThat(m2.cluster().getLocalMember().promote(Type.ACTIVE))
          .succeedsWithin(Duration.ofSeconds(30));

      // then - the leader's committed configuration has m2 as ACTIVE and the cluster still
      // commits entries
      Awaitility.await("the promotion is committed")
          .untilAsserted(
              () -> {
                final var configuration = m1.getContext().getCluster().getConfiguration();
                assertThat(configuration.requiresJointConsensus()).isFalse();
                assertThat(memberTypes(m1)).containsEntry(id2, Type.ACTIVE);
                assertThat(m1.getContext().getCommitIndex())
                    .isGreaterThanOrEqualTo(configuration.index());
              });
      assertThat(appendEntry(awaitLeader(m1, m2)).commit()).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void shouldRejectPromotionUntilCaughtUp(@TempDir final Path tmp) {
      // given - a single-member cluster with a caught-up PROMOTABLE member 2, a promotion lag
      // threshold that any unreplicated entry exceeds, and generous timeouts so that blocking
      // replication to m2 doesn't make the leader step down within this test's observation window
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final Consumer<RaftPartitionConfig> testConfig =
          config -> {
            config.setMaxQuorumResponseTimeout(Duration.ofSeconds(60));
            config.setElectionTimeout(Duration.ofSeconds(3));
            config.setPromotionLagThreshold(1);
          };

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2), testConfig);
      m1.bootstrap(id1).join();
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(5));

      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1), testConfig);
      assertThat(m2.join(Type.PROMOTABLE, List.of(id1, id2)))
          .succeedsWithin(Duration.ofSeconds(30));
      awaitCaughtUp(m1, m2);
      awaitSameConfiguration(m1, m2);

      // when - m2 stops receiving entries while the leader commits a few more, so m2's
      // replication lag exceeds the promotion lag threshold (the ACTIVE quorum is the leader
      // alone, so commits don't need m2)
      final var blockAppends = new AtomicBoolean(true);
      interceptEntryAppends(m1, id2, blockAppends);
      final var leaderRole = getLeader(m1).orElseThrow();
      for (int i = 0; i < 3; i++) {
        assertThat(appendEntry(leaderRole).commit()).succeedsWithin(Duration.ofSeconds(5));
      }

      // then - the promotion is rejected because m2 is not caught up
      assertThat(m2.cluster().getLocalMember().promote(Type.ACTIVE))
          .failsWithin(Duration.ofSeconds(10))
          .withThrowableOfType(ExecutionException.class)
          .withMessageContaining("not caught up");
      assertThat(memberTypes(m1)).containsEntry(id2, Type.PROMOTABLE);

      // when - replication to m2 resumes and it catches up
      blockAppends.set(false);
      awaitCaughtUp(m1, m2);

      // then - retrying the promotion succeeds. The first retries may still be rejected while
      // the acknowledgement that clears m2's replication lag on the leader is in flight.
      Awaitility.await("the promotion succeeds after catching up")
          .untilAsserted(
              () ->
                  assertThat(m2.cluster().getLocalMember().promote(Type.ACTIVE))
                      .succeedsWithin(Duration.ofSeconds(10)));
      assertThat(memberTypes(m1)).containsEntry(id2, Type.ACTIVE);
    }

    /**
     * Pins the quorum arithmetic protecting promotion durability: as soon as the joint
     * configuration for a promotion is appended, the promoted member counts towards the new side's
     * ACTIVE commit quorum, so the promotion cannot commit while the promoted member is
     * unreachable.
     */
    @Test
    void shouldNotCommitPromotionWhilePromotedMemberUnreachable(@TempDir final Path tmp) {
      // given - a single-member cluster with a caught-up PROMOTABLE member 2, and generous
      // timeouts so that blocking replication to m2 doesn't make the leader step down within
      // this test's observation window
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final Consumer<RaftPartitionConfig> testTimeouts =
          config -> {
            config.setMaxQuorumResponseTimeout(Duration.ofSeconds(60));
            config.setElectionTimeout(Duration.ofSeconds(3));
          };

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2), testTimeouts);
      m1.bootstrap(id1).join();
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(5));

      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1), testTimeouts);
      assertThat(m2.join(Type.PROMOTABLE, List.of(id1, id2)))
          .succeedsWithin(Duration.ofSeconds(30));
      awaitCaughtUp(m1, m2);
      awaitSameConfiguration(m1, m2);

      // when - m2 stops receiving entries (its own requests still reach the leader, so the
      // promotion request itself is forwarded and accepted) and m2 requests its promotion
      final var blockAppends = new AtomicBoolean(true);
      interceptEntryAppends(m1, id2, blockAppends);
      final var promoteFuture = m2.cluster().getLocalMember().promote(Type.ACTIVE);

      // then - the joint configuration is appended but does not commit: its new side [1A, 2A]
      // needs m2's acknowledgement, which never arrives
      Awaitility.await("the joint configuration is appended")
          .untilAsserted(
              () ->
                  assertThat(
                          m1.getContext().getCluster().getConfiguration().requiresJointConsensus())
                      .isTrue());
      final var jointIndex = m1.getContext().getCluster().getConfiguration().index();
      Awaitility.await("the promotion does not commit while the promoted member is unreachable")
          .during(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                assertThat(promoteFuture).isNotDone();
                assertThat(m1.getContext().getCommitIndex()).isLessThan(jointIndex);
              });

      // when - replication to m2 resumes
      blockAppends.set(false);

      // then - the promotion completes
      assertThat(promoteFuture).succeedsWithin(Duration.ofSeconds(30));
      assertThat(memberTypes(m1)).containsEntry(id2, Type.ACTIVE);
    }

    /**
     * The termination counterpart of {@link
     * #shouldNotCommitPromotionWhilePromotedMemberUnreachable}: that test pins that an appended
     * promotion does not commit without the promoted member, and deliberately keeps the step-down
     * out of its observation window. This one pins that a promotion whose joint configuration can
     * never commit does not leave the leader wedged either - it must not fall back to committing
     * under the old configuration alone, which would apply the promotion without the promoted
     * member ever having stored it, so instead it fails to reach a quorum and steps down. It uses
     * the class's default timeouts for exactly that reason.
     */
    @Test
    void shouldStepDownWhenPromotionCanNeverCommit(@TempDir final Path tmp) {
      // given - a single-member cluster [1A] with a caught-up PROMOTABLE member 2
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      m1.bootstrap(id1).join();
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(5));

      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      assertThat(m2.join(Type.PROMOTABLE, List.of(id1, id2)))
          .succeedsWithin(Duration.ofSeconds(30));
      awaitCaughtUp(m1, m2);
      awaitSameConfiguration(m1, m2);

      // when - m2 becomes unreachable in both directions, so it can neither acknowledge the joint
      // configuration nor answer polls and votes: a member that still answered votes could
      // re-elect the leader immediately after it stepped down. Blocking only entry-carrying
      // appends, as the sibling test does, would leave exactly that hole. The promotion is then
      // requested through a client rather than by m2 itself: an isolated m2 could not send it, and
      // DefaultRaftMember#promote retries on NO_LEADER, so its future would never complete and
      // there would be nothing deterministic to assert on. The leader's match index for m2 is
      // untouched by the failing appends, so the request still passes the catch-up gate.
      final var commitIndexBeforePromotion = m1.getContext().getCommitIndex();
      final var configuration = m1.getContext().getCluster().getConfiguration();
      final var client = protocolFactory.newServerProtocol(MemberId.from("test-client"));
      protocolFactory.partition(id2);

      final var promoted =
          List.<RaftMember>of(
              new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
              new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()));
      final var response =
          client.reconfigure(
              id1,
              ReconfigureRequest.builder()
                  .withIndex(configuration.index())
                  .withTerm(m1.getContext().getCluster().getConfigurationTerm())
                  .withMembers(promoted)
                  .from(id1.id())
                  .build());

      // then - the joint configuration is appended
      Awaitility.await("the joint configuration is appended")
          .untilAsserted(
              () ->
                  assertThat(
                          m1.getContext().getCluster().getConfiguration().requiresJointConsensus())
                      .isTrue());
      final var jointIndex = m1.getContext().getCluster().getConfiguration().index();
      assertThat(jointIndex).isGreaterThan(commitIndexBeforePromotion);

      // and - it never commits and the leader steps down instead of committing it under the old
      // configuration [1A] alone. It also stays down: the joint vote quorum needs a majority of
      // the new side [1A, 2A] too, which m2 is part of.
      awaitNoLeader(m1);
      Awaitility.await("the promotion never commits and the leader stays down")
          .during(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                assertThat(m1.getContext().getCommitIndex())
                    .isEqualTo(commitIndexBeforePromotion)
                    .isLessThan(jointIndex);
                assertThat(m1.isLeader()).isFalse();
              });

      // and - the promotion does not succeed. It terminates either with the error the stepping-down
      // leader completes the pending reconfiguration with, or, if that response loses the race
      // against the test protocol's request timeout, exceptionally. Both mean a failed promotion,
      // and asserting only one of the two would be racy.
      Awaitility.await("the promotion request completes")
          .untilAsserted(() -> assertThat(response).isDone());
      assertThat(response.isCompletedExceptionally() || response.join().status() == Status.ERROR)
          .describedAs("the promotion must not succeed")
          .isTrue();
      assertThat(m1.getContext().getCluster().getConfiguration().requiresJointConsensus())
          .describedAs("the promotion never took effect: the cluster is left in joint consensus")
          .isTrue();
    }

    /**
     * A crash-recovery retry of join(PROMOTABLE) can arrive after the member was already promoted
     * to ACTIVE. Joining must never demote: the retry is acknowledged without a configuration
     * change.
     */
    @Test
    void shouldNotDemoteActiveMemberWhenJoiningAsPromotable(@TempDir final Path tmp) {
      // given - a two-member cluster where m2 joined as PROMOTABLE and was promoted to ACTIVE
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2));
      m1.bootstrap(id1).join();
      assertThat(appendEntry(awaitLeader(m1)).commit()).succeedsWithin(Duration.ofSeconds(5));

      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
      assertThat(m2.join(Type.PROMOTABLE, List.of(id1, id2)))
          .succeedsWithin(Duration.ofSeconds(30));
      awaitCaughtUp(m1, m2);
      awaitSameConfiguration(m1, m2);
      assertThat(m2.cluster().getLocalMember().promote(Type.ACTIVE))
          .succeedsWithin(Duration.ofSeconds(30));
      assertThat(memberTypes(m1)).containsEntry(id2, Type.ACTIVE);

      // when - a crash-recovery retry re-issues the join as PROMOTABLE
      final var retriedJoin = m2.cluster().join(Type.PROMOTABLE, List.of(id1, id2));

      // then - the join is acknowledged without demoting m2
      assertThat(retriedJoin).succeedsWithin(Duration.ofSeconds(30));
      assertThat(memberTypes(m1)).containsEntry(id2, Type.ACTIVE);
      assertThat(appendEntry(awaitLeader(m1, m2)).commit()).succeedsWithin(Duration.ofSeconds(5));
    }

    /**
     * The two-phase leave: a member that demoted itself to PASSIVE before leaving is in no quorum
     * anymore, so its removal commits without any participation from the leaving member - both
     * joint sides' ACTIVE quorums consist of the remaining members only.
     */
    @Test
    void shouldRemoveDemotedMemberWithoutItsParticipation(@TempDir final Path tmp) {
      // given - a cluster with 3 ACTIVE members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
      final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
      final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();
      // commit an entry to ensure that the leader is ready to accept new configurations
      assertThat(appendEntry(awaitLeader(m1, m2, m3)).commit())
          .succeedsWithin(Duration.ofSeconds(5));
      final var leader = getLeaderServer(List.of(m1, m2, m3)).orElseThrow();
      final var demoting = Stream.of(m1, m2, m3).filter(s -> s != leader).findAny().orElseThrow();
      final var demotingId = MemberId.from(demoting.name());

      // when - a follower demotes itself to PASSIVE, then all messages to it are blocked (its
      // own requests still reach the leader), and it leaves
      assertThat(demoting.cluster().getLocalMember().demote(Type.PASSIVE))
          .succeedsWithin(Duration.ofSeconds(10));
      assertThat(memberTypes(leader)).containsEntry(demotingId, Type.PASSIVE);
      protocolFactory.blockMessagesTo(demotingId);
      final var leaveFuture = demoting.leave();

      // then - the leader commits the configuration without the demoted member even though it
      // never acknowledged anything after the demotion
      final var remaining = Stream.of(m1, m2, m3).filter(s -> s != demoting).toList();
      final var expected =
          remaining.stream().map(server -> server.cluster().getLocalMember()).toList();
      Awaitility.await("the leader commits the configuration without the demoted member")
          .untilAsserted(
              () -> {
                final var configuration = leader.getContext().getCluster().getConfiguration();
                assertThat(configuration.requiresJointConsensus()).isFalse();
                assertThat(configuration.allMembers())
                    .containsExactlyInAnyOrderElementsOf(expected);
                assertThat(leader.getContext().getCommitIndex())
                    .isGreaterThanOrEqualTo(configuration.index());
              });

      // and - the leave completes once connectivity is restored
      protocolFactory.heal(demotingId);
      assertThat(leaveFuture).succeedsWithin(Duration.ofSeconds(30));
    }

    /**
     * Blocks entry-carrying appends from {@code sender} to {@code receiver} while {@code blocked}
     * is true. Empty appends (heartbeats) keep flowing so the receiver is not falsely suspected
     * unreachable. Every append exchange is delayed by a few milliseconds to avoid busy-looping the
     * raft actor thread with the in-memory test protocol (see
     * removedLeaderStepsDownAtCommitNotAtAppend).
     */
    private static void interceptEntryAppends(
        final RaftServer sender, final MemberId receiver, final AtomicBoolean blocked) {
      final var protocol = (TestRaftServerProtocol) sender.getContext().getProtocol();
      protocol.interceptDelivery(
          VersionedAppendRequest.class,
          (to, request) -> {
            final var result = new CompletableFuture<Void>();
            CompletableFuture.runAsync(
                () -> {
                  if (to.equals(receiver) && blocked.get() && !request.entries().isEmpty()) {
                    result.completeExceptionally(new ConnectException());
                  } else {
                    result.complete(null);
                  }
                },
                CompletableFuture.delayedExecutor(20, TimeUnit.MILLISECONDS));
            return result;
          });
    }

    private static Map<MemberId, Type> memberTypes(final RaftServer server) {
      return server.cluster().getMembers().stream()
          .collect(Collectors.toMap(RaftMember::memberId, RaftMember::getType));
    }

    private static void awaitCaughtUp(final RaftServer leader, final RaftServer member) {
      Awaitility.await(
              "%s caught up to the last index of %s".formatted(member.name(), leader.name()))
          .untilAsserted(() -> assertThat(lastIndex(member)).isEqualTo(lastIndex(leader)));
    }

    /**
     * Reads the server's last log index on its own raft thread. The journal is thread-confined:
     * reading it from the test thread races with the appending raft thread over the current segment
     * and its writer, both plain fields, so a poll could observe a retired writer indefinitely or
     * trip over a half-published segment switch.
     */
    private static long lastIndex(final RaftServer server) {
      final var context = server.getContext();
      final var lastIndex = new CompletableFuture<Long>();
      context.getThreadContext().execute(() -> lastIndex.complete(context.getLog().getLastIndex()));
      return lastIndex.orTimeout(5, TimeUnit.SECONDS).join();
    }

    /**
     * Waits until both servers hold the same configuration. Configuration changes are requested
     * with the requester's local view of index and term; a request from a member that has not
     * received the latest configuration yet is rejected as stale.
     */
    private static void awaitSameConfiguration(final RaftServer a, final RaftServer b) {
      Awaitility.await("%s and %s hold the same configuration".formatted(a.name(), b.name()))
          .untilAsserted(
              () ->
                  assertThat(a.getContext().getCluster().getConfiguration().index())
                      .isEqualTo(b.getContext().getCluster().getConfiguration().index()));
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

    /**
     * The force-configure counterpart of {@code
     * recoveredJointConfigurationCommitsBeforeTheFinalOne}: the leader elected under a forced
     * configuration appends the first regular configuration only once its no-op entry has
     * committed, like every other configuration change.
     */
    @Test
    void leaderLeavesForcedConfigurationOnlyAfterItsNoOpEntryCommits() {
      // given - the cluster is forced down to {1,2} with 3 and 4 gone, while entry-carrying
      // appends between 1 and 2 are dropped. Either may become leader; heartbeats and votes pass.
      m3.shutdown().join();
      m4.shutdown().join();
      final var dropEntryAppends = new AtomicBoolean(true);
      dropEntryAppendsTo(m1, id2, dropEntryAppends);
      dropEntryAppendsTo(m2, id1, dropEntryAppends);
      m2.forceConfigure(newMembers()).join();

      // when - a leader is elected under the forced configuration
      final var leader = awaitLeader(m1, m2);
      final var leaderServer = getLeaderServer(List.of(m1, m2)).orElseThrow();

      // then - it stays in the forced configuration while the other member cannot acknowledge the
      // no-op entry ...
      Awaitility.await("the leader keeps the forced configuration while its no-op cannot commit")
          .during(Duration.ofSeconds(2))
          .untilAsserted(
              () ->
                  assertThat(leaderServer.getContext().getCluster().getConfiguration())
                      .returns(true, Configuration::force));

      // ... and leaves it once the no-op entry commits
      dropEntryAppends.set(false);
      Awaitility.await("the leader appends a regular configuration once the no-op committed")
          .untilAsserted(
              () -> {
                final var configuration = leaderServer.getContext().getCluster().getConfiguration();
                assertThat(configuration.force()).isFalse();
                assertThat(configuration.allMembers())
                    .extracting(RaftMember::memberId)
                    .containsExactlyInAnyOrder(id1, id2);
              });
      assertThat(appendEntry(leader).commit()).succeedsWithin(Duration.ofSeconds(10));
    }

    private Map<MemberId, Type> newMembers() {
      return Map.of(id1, Type.ACTIVE, id2, Type.ACTIVE);
    }
  }
}
