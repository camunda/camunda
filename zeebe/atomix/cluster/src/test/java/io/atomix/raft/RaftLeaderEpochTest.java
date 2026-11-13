/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class RaftLeaderEpochTest {

  @Rule public RaftRule raftRule = RaftRule.withBootstrappedNodes(3);

  @Test
  public void shouldStampAllEntriesOfATermWithOneRandomEpoch() throws Exception {
    // given
    raftRule.appendEntries(2);

    // when
    raftRule.shutdownLeader();
    raftRule.awaitNewLeader();
    final long lastIndex = raftRule.appendEntries(2);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);

    // then
    final var epochsByTerm = epochsByTerm(anyLog());
    assertThat(epochsByTerm).hasSizeGreaterThanOrEqualTo(2);
    assertThat(epochsByTerm.values())
        .allSatisfy(epochs -> assertThat(epochs).singleElement().returns(4, UUID::version));
    assertThat(epochsByTerm.values().stream().flatMap(Set::stream).distinct())
        .hasSameSizeAs(epochsByTerm.keySet());
  }

  @Test
  public void shouldReplicateEntriesWithTheirEpoch() throws Exception {
    // when
    final long lastIndex = raftRule.appendEntries(3);
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);

    // then
    final Collection<List<IndexedRaftLogEntry>> logs = raftRule.getMemberLogs().values();
    assertThat(logs).hasSize(3);
    assertThat(logs.stream().map(RaftLeaderEpochTest::epochsByIndex).distinct()).hasSize(1);
  }

  private List<IndexedRaftLogEntry> anyLog() {
    return raftRule.getMemberLogs().values().iterator().next();
  }

  private static Map<Long, Set<UUID>> epochsByTerm(final List<IndexedRaftLogEntry> log) {
    return log.stream()
        .collect(
            Collectors.groupingBy(
                IndexedRaftLogEntry::term,
                Collectors.mapping(IndexedRaftLogEntry::epoch, Collectors.toSet())));
  }

  private static Map<Long, UUID> epochsByIndex(final List<IndexedRaftLogEntry> log) {
    return log.stream()
        .collect(Collectors.toMap(IndexedRaftLogEntry::index, IndexedRaftLogEntry::epoch));
  }
}
