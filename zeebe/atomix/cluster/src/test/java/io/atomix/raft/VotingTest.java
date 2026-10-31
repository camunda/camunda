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
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.protocol.TestRaftServerProtocol;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class VotingTest {
  private final SingleThreadContext context = new SingleThreadContext("raft-%d");
  private final TestRaftProtocolFactory protocolFactory = new TestRaftProtocolFactory();
  private final List<RaftServer> servers = new LinkedList<>();
  private final Map<MemberId, TestRaftServerProtocol> protocols = new HashMap<>();
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @AfterEach
  void cleanup() {
    for (final var server : servers) {
      server.shutdown().join();
    }
    context.close();
  }

  @Test
  void joinerWithoutConfigurationGrantsPollAndVote(@TempDir final Path tmp) {
    // given - a joining member that has not received a configuration because no other member is
    // reachable
    final var id1 = MemberId.from("1");
    final var id2 = MemberId.from("2");
    final var joiner = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
    assertThat(joiner.join(id1)).failsWithin(Duration.ofSeconds(10));
    Awaitility.await("Joiner is passive")
        .until(() -> joiner.getContext().getRaftRole().role() == RaftServer.Role.PASSIVE);

    // when - a candidate unknown to the joiner requests a poll and a vote
    final var candidateId = MemberId.from("3");
    final var candidate = protocolFactory.newServerProtocol(candidateId);

    // then - both are granted based on term and log up-to-dateness alone
    assertPoll(candidate.poll(id2, pollRequest(candidateId, 2)), true);
    assertVote(candidate.vote(id2, voteRequest(candidateId, 2)), true);
  }

  @Test
  void joinerGrantsAtMostOneVotePerTermAcrossRestarts(@TempDir final Path tmp) {
    // given - a passive, configuration-less joiner that voted for candidate A in term 2
    final var id1 = MemberId.from("1");
    final var id2 = MemberId.from("2");
    final var joiner = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
    assertThat(joiner.join(id1)).failsWithin(Duration.ofSeconds(10));
    Awaitility.await("Joiner is passive")
        .until(() -> joiner.getContext().getRaftRole().role() == RaftServer.Role.PASSIVE);

    final var candidateA = MemberId.from("A");
    final var candidateB = MemberId.from("B");
    final var protocolA = protocolFactory.newServerProtocol(candidateA);
    final var protocolB = protocolFactory.newServerProtocol(candidateB);
    assertVote(protocolA.vote(id2, voteRequest(candidateA, 2)), true);

    // then - within the same term, only candidate A's vote request is granted again
    assertVote(protocolB.vote(id2, voteRequest(candidateB, 2)), false);
    assertVote(protocolA.vote(id2, voteRequest(candidateA, 2)), true);

    // when - the joiner restarts
    joiner.shutdown().join();
    servers.remove(joiner);
    final var restarted = createServer(tmp, StaticClusterMembershipService.of(id2, id1));
    assertThat(restarted.join(id1)).failsWithin(Duration.ofSeconds(10));
    Awaitility.await("Restarted joiner is passive")
        .until(() -> restarted.getContext().getRaftRole().role() == RaftServer.Role.PASSIVE);

    // then - the vote for term 2 is persisted: candidate B is still rejected
    assertVote(protocolB.vote(id2, voteRequest(candidateB, 2)), false);
    assertVote(protocolA.vote(id2, voteRequest(candidateA, 2)), true);
  }

  @ParameterizedTest
  @EnumSource(
      value = Type.class,
      names = {"PASSIVE", "PROMOTABLE"})
  void nonVotingMemberGrantsPollAndVote(final Type type, @TempDir final Path tmp) {
    // given - a cluster [1A, 2A, 3A] reconfigured so that member 3 is no longer ACTIVE
    final var id3 = MemberId.from("3");
    final var m3 = bootstrapWithDemotedMember3(type, tmp).get(2);

    // when - a candidate outside the configuration requests a poll and a vote in a new term
    final var candidateId = MemberId.from("99");
    final var candidate = protocolFactory.newServerProtocol(candidateId);
    final var term = m3.getContext().getTerm() + 1;

    // then - member 3 answers instead of rejecting with ILLEGAL_MEMBER_STATE
    assertPoll(candidate.poll(id3, pollRequest(candidateId, term)), true);
    assertVote(candidate.vote(id3, voteRequest(candidateId, term)), true);
  }

  @ParameterizedTest
  @EnumSource(
      value = Type.class,
      names = {"PASSIVE", "PROMOTABLE"})
  void nonVotingMemberIsNotAskedForPollsAndVotes(final Type type, @TempDir final Path tmp) {
    // given - a cluster [1A, 2A, 3A] reconfigured so that member 3 is no longer ACTIVE
    final var id1 = MemberId.from("1");
    final var id2 = MemberId.from("2");
    final var id3 = MemberId.from("3");
    final var demoted = bootstrapWithDemotedMember3(type, tmp);
    final var activeMembers = List.of(demoted.get(0), demoted.get(1));

    // record the receivers of all poll and vote requests sent by the two active members
    final Set<MemberId> pollReceivers = ConcurrentHashMap.newKeySet();
    final Set<MemberId> voteReceivers = ConcurrentHashMap.newKeySet();
    for (final var sender : List.of(id1, id2)) {
      final var protocol = protocols.get(sender);
      protocol.interceptRequest(
          PollRequest.class, (receiver, request) -> pollReceivers.add(receiver));
      protocol.interceptRequest(
          VoteRequest.class, (receiver, request) -> voteReceivers.add(receiver));
    }

    // when - the leader steps down and a new leader is elected
    final var leader = getLeaderServer(activeMembers).orElseThrow();
    final var termBefore = leader.getContext().getTerm();
    leader.stepDown().join();
    Awaitility.await("A new leader is elected")
        .atMost(Duration.ofSeconds(30))
        .until(
            () ->
                getLeaderServer(activeMembers)
                    .map(server -> server.getContext().getTerm() > termBefore)
                    .orElse(false));

    // then - member 3 was not asked for polls or votes because it is not active in the
    // configuration
    assertThat(pollReceivers).isNotEmpty().doesNotContain(id3);
    assertThat(voteReceivers).isNotEmpty().doesNotContain(id3);
  }

  /**
   * Bootstraps a cluster of three initially active members and reconfigures it so that member 3 has
   * the given non-active type.
   *
   * @return the three servers, in order of their member ids
   */
  private List<RaftServer> bootstrapWithDemotedMember3(final Type type, final Path tmp) {
    final var id1 = MemberId.from("1");
    final var id2 = MemberId.from("2");
    final var id3 = MemberId.from("3");

    final var m1 = createServer(tmp, StaticClusterMembershipService.of(id1, id2, id3));
    final var m2 = createServer(tmp, StaticClusterMembershipService.of(id2, id1, id3));
    final var m3 = createServer(tmp, StaticClusterMembershipService.of(id3, id1, id2));

    CompletableFuture.allOf(
            m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
        .join();

    // member 3 will be demoted, so make sure it is not the leader
    awaitLeaderIsIn(List.of(m1, m2, m3), m1, m2);

    // commit an entry to ensure that the leader is ready to accept new configuration
    Assertions.assertThat(appendEntry(awaitLeader(m1, m2)).commit())
        .succeedsWithin(Duration.ofSeconds(5));

    final var leader = getLeaderServer(List.of(m1, m2)).orElseThrow();
    final var leaderId = leader.cluster().getLocalMember().memberId();
    final var configuration = leader.getContext().getCluster().getConfiguration();
    final var demoted =
        List.<RaftMember>of(
            new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
            new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()),
            new DefaultRaftMember(id3, type, Instant.now()));
    Assertions.assertThat(
            protocolFactory
                .newServerProtocol(MemberId.from("test-client"))
                .reconfigure(
                    leaderId,
                    ReconfigureRequest.builder()
                        .withIndex(configuration.index())
                        .withTerm(configuration.term())
                        .withMembers(demoted)
                        .from(leaderId.id())
                        .build()))
        .succeedsWithin(Duration.ofSeconds(10))
        .satisfies(response -> assertThat(response.status()).isEqualTo(Status.OK));

    final var expectedRole =
        type == Type.PASSIVE ? RaftServer.Role.PASSIVE : RaftServer.Role.PROMOTABLE;
    Awaitility.await("Member 3 is " + type)
        .until(() -> m3.getContext().getRaftRole().role() == expectedRole);

    return List.of(m1, m2, m3);
  }

  @Test
  void followerGrantsVoteToCandidateOutsideConfiguration(@TempDir final Path tmp) {
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
    final var follower = getFollower(m1, m2, m3).orElseThrow();
    final var followerId = follower.cluster().getLocalMember().memberId();

    // when - a candidate that is not part of the configuration requests a vote in a new term
    // with an up-to-date log
    final var candidateId = MemberId.from("99");
    final var candidate = protocolFactory.newServerProtocol(candidateId);
    final var term = follower.getContext().getTerm() + 1;

    // then - the vote is granted: during a reconfiguration, consensus is reached based on the
    // candidate's configuration, which may include members that the voter does not know yet
    assertVote(candidate.vote(followerId, voteRequest(candidateId, term)), true);
  }

  private PollRequest pollRequest(final MemberId candidate, final long term) {
    // lastLogTerm is the requested term so that the candidate's log is up-to-date compared to
    // any local log, whose entries are all from earlier terms.
    return PollRequest.builder()
        .withCandidate(candidate)
        .withTerm(term)
        .withLastLogIndex(1)
        .withLastLogTerm(term)
        .build();
  }

  private VoteRequest voteRequest(final MemberId candidate, final long term) {
    return VoteRequest.builder()
        .withCandidate(candidate)
        .withTerm(term)
        .withLastLogIndex(1)
        .withLastLogTerm(term)
        .build();
  }

  private void assertPoll(final CompletableFuture<PollResponse> response, final boolean accepted) {
    assertThat(response)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            pollResponse -> {
              assertThat(pollResponse.status()).isEqualTo(Status.OK);
              assertThat(pollResponse.accepted()).isEqualTo(accepted);
            });
  }

  private void assertVote(final CompletableFuture<VoteResponse> response, final boolean voted) {
    assertThat(response)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            voteResponse -> {
              assertThat(voteResponse.status()).isEqualTo(Status.OK);
              assertThat(voteResponse.voted()).isEqualTo(voted);
            });
  }

  private static AppendResult appendEntry(final LeaderRole leader) {
    final var result = new AppendResult();
    leader.appendEntry(-1, -1, ByteBuffer.wrap(new byte[0]), result);
    return result;
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

  private static LeaderRole awaitLeader(final RaftServer... servers) {
    //noinspection OptionalGetWithoutIsPresent
    return Awaitility.await("Leader is known")
        .until(() -> getLeader(servers), Optional::isPresent)
        .get();
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

  private RaftServer createServer(
      final Path dir, final ClusterMembershipService membershipService) {
    final var memberId = membershipService.getLocalMember().id();
    final var protocol = protocolFactory.newServerProtocol(memberId);
    protocols.put(memberId, protocol);
    final var storage =
        RaftStorage.builder(meterRegistry)
            .withDirectory(dir.resolve(memberId.toString()).toFile())
            .withSnapshotStore(new TestSnapshotStore(new AtomicReference<>()))
            .withMaxSegmentSize(1024 * 10)
            .build();
    final var server =
        RaftServer.builder(memberId)
            .withMembershipService(membershipService)
            .withProtocol(protocol)
            .withStorage(storage)
            .withPartitionConfig(
                new RaftPartitionConfig()
                    .setElectionTimeout(Duration.ofMillis(500))
                    .setHeartbeatInterval(Duration.ofMillis(100)))
            .withMeterRegistry(meterRegistry)
            .build();
    servers.add(server);
    return server;
  }
}
