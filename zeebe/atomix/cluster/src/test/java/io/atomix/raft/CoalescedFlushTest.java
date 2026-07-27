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
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.storage.log.CoalescedFlusher;
import java.time.Duration;
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
    final var entryCount = 32;

    // when
    final var lastIndex = raftRule.appendEntries(entryCount);

    // then
    raftRule.awaitCommit(lastIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    assertAllMemberLogsEqual();
  }

  @Test
  public void shouldCommitEntriesAfterLeaderRestart() throws Throwable {
    // given
    raftRule.appendEntries(32);

    // when
    raftRule.restartLeader();
    final var lastIndex = raftRule.appendEntries(32);

    // then - all previously committed and new entries are present on all nodes
    raftRule.awaitCommit(lastIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);
    assertThat(raftRule.getMemberLogs().values())
        .allSatisfy(entries -> assertThat(entries.getLast().index()).isEqualTo(lastIndex));
    assertAllMemberLogsEqual();
  }

  private void assertAllMemberLogsEqual() {
    final var memberLogs = raftRule.getMemberLogs();
    final var firstMemberEntries = memberLogs.values().stream().findFirst().orElseThrow();
    assertThat(memberLogs.values())
        .allSatisfy(entries -> assertThat(entries).containsExactlyElementsOf(firstMemberEntries));
  }

  /** Configures all members to flush their logs with the {@link CoalescedFlusher}. */
  private static final class CoalescedFlushConfigurator implements Configurator {

    @Override
    public void configure(final MemberId id, final Builder builder) {
      Configurator.replaceFlusherFactory(
          builder, threadFactory -> new CoalescedFlusher(threadFactory.createContext()));

      // with coalesced flushing, acknowledgements - including those of heartbeats - wait for
      // real fsyncs, which can stall for seconds on loaded CI runners; use generous timeouts
      // so the test exercises replication rather than leader step-downs under load
      final var partitionConfig =
          new RaftPartitionConfig()
              .setElectionTimeout(Duration.ofSeconds(5))
              .setHeartbeatInterval(Duration.ofMillis(250));
      partitionConfig.setMaxQuorumResponseTimeout(Duration.ofSeconds(15));
      builder.withPartitionConfig(partitionConfig);
    }
  }
}
