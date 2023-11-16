/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.ClusterMembershipEventListener;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.Member;
import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.net.Address;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReconfigurationTest {
  private final SingleThreadContext context = new SingleThreadContext("raft-%d");
  private final TestRaftProtocolFactory protocolFactory = new TestRaftProtocolFactory(context);
  private final List<RaftServer> servers = new LinkedList<>();

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

  private static Optional<LeaderRole> getLeader(final RaftServer... servers) {
    return Arrays.stream(servers)
        .filter(RaftServer::isLeader)
        .map(RaftServer::getContext)
        .map(RaftContext::getRaftRole)
        .map(LeaderRole.class::cast)
        .findAny();
  }

  private static AppendResult appendEntry(final LeaderRole leader) {
    final var result = new AppendResult();
    leader.appendEntry(-1, -1, ByteBuffer.wrap(new byte[0]), result);
    return result;
  }

  private ClusterMembershipService createMembershipService(
      final MemberId localId, final MemberId... remoteIds) {
    final var localMember = Member.member(localId, Address.local());
    final var remoteMembers =
        Arrays.stream(remoteIds).map(id -> Member.member(id, Address.local()));
    final var members = new HashMap<MemberId, Member>();
    members.put(localId, localMember);
    remoteMembers.forEach(member -> members.put(member.id(), member));
    return new ClusterMembershipService() {
      @Override
      public Member getLocalMember() {
        return localMember;
      }

      @Override
      public Set<Member> getMembers() {
        return Set.copyOf(members.values());
      }

      @Override
      public Member getMember(final MemberId memberId) {
        return members.get(memberId);
      }

      @Override
      public void addListener(final ClusterMembershipEventListener listener) {}

      @Override
      public void removeListener(final ClusterMembershipEventListener listener) {}
    };
  }

  private RaftServer createServer(
      final Path dir, final ClusterMembershipService membershipService) {
    final var memberId = membershipService.getLocalMember().id();
    final var protocol = protocolFactory.newServerProtocol(memberId);
    final var storage =
        RaftStorage.builder()
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
            .build();
    servers.add(server);
    return server;
  }

  /**
   * Tracks the result of an append operation. Provides two futures, {@link #write()} and {@link
   * #commit()} that are completed when the entry is written and committed respectively.
   */
  private static final class AppendResult implements AppendListener {
    private final CompletableFuture<Long> write = new CompletableFuture<>();
    private final CompletableFuture<Long> commit = new CompletableFuture<>();

    /**
     * @return a future that is completed with the entry index when it is committed. If the write or
     *     commit fails, the future is completed exceptionally.
     */
    CompletableFuture<Long> commit() {
      return commit;
    }

    /**
     * @return a future that is completed with the entry index when it is written. If the write
     *     fails, the future is completed exceptionally.
     */
    CompletableFuture<Long> write() {
      return write;
    }

    @Override
    public void onWrite(final IndexedRaftLogEntry indexed) {
      write.complete(indexed.index());
    }

    @Override
    public void onWriteError(final Throwable error) {
      write.completeExceptionally(error);
      // If write fails, the entry cannot be committed either.
      commit.completeExceptionally(error);
    }

    @Override
    public void onCommit(final long index) {
      commit.complete(index);
    }

    @Override
    public void onCommitError(final long index, final Throwable error) {
      commit.completeExceptionally(error);
    }
  }

  @Nested
  final class Joining {
    @Test
    void shouldJoinExistingMembers(@TempDir final Path tmp) {
      // given - a cluster with 3 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();

      // when - a new member joins
      final var id4 = MemberId.from("4");
      final var m4 = createServer(tmp, createMembershipService(id4, id1, id2, id3));
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

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));
      final var m4 = createServer(tmp, createMembershipService(id4, id1, id2, id3));

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

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));
      final var m4 = createServer(tmp, createMembershipService(id4, id1, id2, id3));
      final var m5 = createServer(tmp, createMembershipService(id5, id1, id2, id3));

      CompletableFuture.allOf(
              m1.bootstrap(id1, id2, id3), m2.bootstrap(id1, id2, id3), m3.bootstrap(id1, id2, id3))
          .join();

      m4.join(id1, id2, id3).join();
      m5.join(id1, id2, id3).join();

      // when - no quorum possible because three out of five members are down
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

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));
      final var m4 = createServer(tmp, createMembershipService(id4, id1, id2, id3));
      final var m5 = createServer(tmp, createMembershipService(id5, id1, id2, id3));

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

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));

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

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));

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

      final var m1 = createServer(tmp, createMembershipService(id1, id2));
      final var m2 = createServer(tmp, createMembershipService(id2, id1));

      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();

      // when - m2 left
      assertThat(m2.leave()).succeedsWithin(Duration.ofSeconds(5));
      appendEntry(awaitLeader(m1)).commit().join();

      // then - m2 can request leave again
      assertThat(m2.leave()).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void shouldLeave2MemberCluster(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");

      final var m1 = createServer(tmp, createMembershipService(id1, id2));
      final var m2 = createServer(tmp, createMembershipService(id2, id1));

      CompletableFuture.allOf(m1.bootstrap(id1, id2), m2.bootstrap(id1, id2)).join();

      // when - existing member leaves
      m2.leave().join();

      // then - all members show a configuration with 1 active member
      final var expected = List.of(new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()));

      assertThat(m1.cluster().getMembers()).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void cannotLeaveWhenNewConfigurationDoesNotHaveQuorum(@TempDir final Path tmp) {
      // given - a cluster with 2 members
      final var id1 = MemberId.from("1");
      final var id2 = MemberId.from("2");
      final var id3 = MemberId.from("3");

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));

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
          .failsWithin(Duration.ofSeconds(2))
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

      final var m1 = createServer(tmp, createMembershipService(id1, id2, id3));
      final var m2 = createServer(tmp, createMembershipService(id2, id1, id3));
      final var m3 = createServer(tmp, createMembershipService(id3, id1, id2));
      final var m4 = createServer(tmp, createMembershipService(id4, id1, id2, id3));
      final var m5 = createServer(tmp, createMembershipService(id5, id1, id2, id3));

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
  }
}
