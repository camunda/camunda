/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.impl.RaftContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A partition created by scaling up starts from a bootstrap snapshot at index 1, whose state is not
 * in the log, while its log still starts at index 1. A member which joins it with an empty log must
 * receive the snapshot, as replaying the log from index 1 does not rebuild that state.
 */
final class SnapshotReplicationToEmptyMemberTest {
  private static final MemberId LEADER = MemberId.from("0");
  // joins the partition with an empty log, like a member of a partition created by scaling up
  private static final MemberId JOINING_MEMBER = MemberId.from("1");

  @TempDir private Path directory;
  private ControllableRaftContexts raft;

  @BeforeEach
  void setup() throws Exception {
    raft = new ControllableRaftContexts(2);
    raft.setup(directory, new Random(1), List.of(0));
  }

  @AfterEach
  void shutdown() throws IOException {
    raft.shutdown();
  }

  @Test
  void shouldSendBootstrapSnapshotToJoiningMember() {
    // given - the leader has a bootstrap snapshot, and its log still starts at index 1
    raft.tickElectionTimeout(LEADER);
    raft.runUntilDone(LEADER);
    assertThat(context(LEADER).getRole()).isEqualTo(Role.LEADER);
    raft.takeBootstrapSnapshot(LEADER);
    raft.runUntilDone(LEADER);
    assertThat(context(LEADER).getCurrentSnapshot().getId()).startsWith("1-1-0-0-");
    assertThat(context(LEADER).getLog().getFirstIndex()).isOne();

    // when
    final var join = raft.join(JOINING_MEMBER, List.of(LEADER));
    for (int i = 0; i < 50 && !join.isDone(); i++) {
      raft.tickHeartbeatTimeout(LEADER);
      deliverAll(JOINING_MEMBER);
      deliverAll(LEADER);
    }

    // then - the member received the snapshot, instead of the log from index 1
    assertThat(join).isCompleted();
    assertThat(context(JOINING_MEMBER).getCurrentSnapshotIndex()).isOne();
    assertThat(raft.getServerProtocol(JOINING_MEMBER).getReceivedInstallRequests()).isPositive();
    assertThat(context(JOINING_MEMBER).getLog().getFirstIndex()).isEqualTo(2);
  }

  private void deliverAll(final MemberId member) {
    raft.getServerProtocol(member).receiveAll();
    raft.runUntilDone(member);
  }

  private RaftContext context(final MemberId member) {
    return raft.getRaftContext(member);
  }
}
