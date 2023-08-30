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
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.protocol.TestRaftProtocolFactory;
import io.atomix.raft.snapshot.TestSnapshotStore;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.net.Address;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MemberJoinTest {
  final SingleThreadContext context = new SingleThreadContext("raft-%d");

  final TestRaftProtocolFactory protocolFactory = new TestRaftProtocolFactory(context);

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
    m4.join().join();

    // then - all members show a configuration with 4 active members
    final var expected =
        List.of(
            new DefaultRaftMember(id1, Type.ACTIVE, Instant.now()),
            new DefaultRaftMember(id2, Type.ACTIVE, Instant.now()),
            new DefaultRaftMember(id3, Type.ACTIVE, Instant.now()),
            new DefaultRaftMember(id4, Type.ACTIVE, Instant.now()));

    assertThat(m1.cluster().getMembers()).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(m2.cluster().getMembers()).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(m3.cluster().getMembers()).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(m4.cluster().getMembers()).containsExactlyInAnyOrderElementsOf(expected);
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
    return RaftServer.builder(memberId)
        .withMembershipService(membershipService)
        .withProtocol(protocol)
        .withStorage(storage)
        .withPartitionConfig(
            new RaftPartitionConfig()
                .setElectionTimeout(Duration.ofMillis(1000))
                .setHeartbeatInterval(Duration.ofMillis(500)))
        .build();
  }
}
