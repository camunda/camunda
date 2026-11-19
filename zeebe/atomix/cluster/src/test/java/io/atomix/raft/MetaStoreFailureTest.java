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
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The meta store syncs the term, the vote and the configuration on every write. After a failed
 * sync, the kernel marks the pages clean and reports the error only once, and reading the file
 * returns what is in the page cache, not what is on disk. A member which fails to write to its meta
 * store must therefore fail instead of acting on what it wrote, e.g. granting a vote that may not
 * be on disk.
 */
final class MetaStoreFailureTest {
  private static final MemberId LEADER = MemberId.from("2");
  private static final MemberId FOLLOWER = MemberId.from("1");
  private static final MemberId OTHER_FOLLOWER = MemberId.from("0");

  @TempDir private Path directory;
  private ControllableRaftContexts raft;

  @BeforeEach
  void setup() throws Exception {
    raft = new ControllableRaftContexts(3);
    raft.enableMetaStoreWriteFailures();
    raft.setup(directory, new Random(1));
  }

  @AfterEach
  void shutdown() throws IOException {
    raft.shutdown();
  }

  @Test
  void shouldFailWhenTermCannotBeStored() {
    // given
    raft.failNextMetaStoreWrite(FOLLOWER);

    // when - the candidate's vote request makes the follower store the new term
    electLeader();

    // then - the follower fails instead of voting with a term that may not be on disk
    assertFailed(FOLLOWER);
    assertThat(context(FOLLOWER).getLastVotedFor()).isNull();
    assertThat(context(LEADER).getRole()).isEqualTo(Role.LEADER);
  }

  @Test
  void shouldFailWhenMetaStoreWriteFailsDuringReconfiguration() {
    // given
    electLeader();
    raft.failNextMetaStoreWrite(FOLLOWER);

    // when - replicating and committing the configuration without the leaving member makes the
    // follower write its meta store: the last flushed index, then the configuration
    raft.leave(OTHER_FOLLOWER);
    replicate();

    // then
    assertFailed(FOLLOWER);
  }

  private void assertFailed(final MemberId member) {
    final var context = context(member);
    assertThat(context.getRole()).isEqualTo(Role.INACTIVE);
    final var health = context.getHealthReport();
    assertThat(health.isDead()).isTrue();
    assertThat(health.getIssue()).isNotNull();
    assertThat(health.getIssue().throwable())
        .hasMessageContaining("Failed to persist data")
        .hasRootCauseMessage("Injected sync failure");
  }

  private void electLeader() {
    raft.tickElectionTimeout(LEADER);
    for (int i = 0; i < 100 && context(LEADER).getRole() != Role.LEADER; i++) {
      raft.tickHeartbeatTimeout();
      raft.processAllMessage();
      raft.runUntilDone();
    }
    assertThat(context(LEADER).getRole()).isEqualTo(Role.LEADER);
    replicate();
  }

  private void replicate() {
    for (int i = 0; i < 10; i++) {
      raft.runUntilDone();
      raft.processAllMessage();
      raft.runUntilDone();
      raft.tickHeartbeatTimeout(LEADER);
    }
  }

  private RaftContext context(final MemberId member) {
    return raft.getRaftContext(member);
  }
}
