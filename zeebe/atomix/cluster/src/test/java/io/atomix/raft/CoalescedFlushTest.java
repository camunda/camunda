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
import io.atomix.raft.RaftRule.Configurator;
import io.atomix.raft.RaftServer.Builder;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.CoalescedFlusher;
import java.util.Objects;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the Raft protocol against the {@link CoalescedFlusher}, which acknowledges appends and
 * advances commits asynchronously, once a covering flush completed.
 */
public class CoalescedFlushTest {

  @Rule
  public RaftRule raftRule = RaftRule.withBootstrappedNodes(3, new CoalescedFlushConfigurator());

  @Test
  public void shouldCommitEntriesOnAllNodes() throws Throwable {
    // given
    final var entryCount = 128;

    // when
    final var lastIndex = raftRule.appendEntries(entryCount);

    // then
    raftRule.awaitCommit(lastIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    final var memberLogs = raftRule.getMemberLogs();
    final var firstMemberEntries = memberLogs.values().stream().findFirst().orElseThrow();
    assertThat(memberLogs.values())
        .allSatisfy(entries -> assertThat(entries).containsExactlyElementsOf(firstMemberEntries));
  }

  @Test
  public void shouldCommitEntriesAfterLeaderRestart() throws Throwable {
    // given
    raftRule.appendEntries(64);

    // when
    raftRule.restartLeader();
    final var lastIndex = raftRule.appendEntries(64);

    // then - all previously committed and new entries are present on all nodes
    raftRule.awaitCommit(lastIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    final var memberLogs = raftRule.getMemberLogs();
    final var firstMemberEntries = memberLogs.values().stream().findFirst().orElseThrow();
    assertThat(firstMemberEntries.getLast().index()).isEqualTo(lastIndex);
    assertThat(memberLogs.values())
        .allSatisfy(entries -> assertThat(entries).containsExactlyElementsOf(firstMemberEntries));
  }

  /** Configures all members to flush their logs with the {@link CoalescedFlusher}. */
  private static final class CoalescedFlushConfigurator implements Configurator {

    @Override
    public void configure(final MemberId id, final Builder builder) {
      final var storage = Objects.requireNonNull(builder.storage);
      builder.withStorage(
          RaftStorage.builder(builder.meterRegistry)
              .withDirectory(storage.directory())
              .withSnapshotStore(storage.getPersistedSnapshotStore())
              .withFlusherFactory(
                  threadFactory -> new CoalescedFlusher(threadFactory.createContext()))
              .build());
    }
  }
}
