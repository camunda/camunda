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
import io.atomix.raft.storage.log.RaftLogFlusher;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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

  /**
   * Guards the fixture of the tests above: with the small segments {@link RaftRule} configures, the
   * appended entries must roll over segments, so that flushing is exercised across segment
   * boundaries. If the members were to run with the much larger default segment size, all entries
   * would fit into a single segment and that path would silently not be covered.
   */
  @Test
  public void shouldAppendAcrossMultipleSegments() throws Throwable {
    // given
    final var entryCount = 32;

    // when
    final var lastIndex = raftRule.appendEntries(entryCount);
    raftRule.awaitCommit(lastIndex);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);

    // then - nothing is compacted here, so the tail from index 1 on is the whole log
    for (final var server : raftRule.getServers()) {
      final var segments =
          server.getContext().getTailSegments(1).get(30, TimeUnit.SECONDS).segmentPaths();
      assertThat(segments)
          .as("member %s appended across multiple segments", server.name())
          .hasSizeGreaterThan(1);
    }
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
    public Optional<RaftLogFlusher.Factory> flusherFactory(final MemberId id) {
      return Optional.of(threadFactory -> new CoalescedFlusher(threadFactory.createContext()));
    }

    @Override
    public void configure(final MemberId id, final Builder builder) {
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
