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
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftElectionConfig;
import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.camunda.zeebe.journal.Journal;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaftFlushErrorTest {

  private static final Logger LOG = LoggerFactory.getLogger(RaftFlushErrorTest.class);
  private static final int MEMBERS = 3;
  public AtomicBoolean isFaulty = new AtomicBoolean(false);
  public AtomicInteger flushFailedCount = new AtomicInteger(0);

  @Rule
  public RaftRule raftRule =
      RaftRule.withBootstrappedNodes(
          MEMBERS,
          new FaultyFlusherConfigurator(
              (MEMBERS - 1) / 2, isFaulty::get, flushFailedCount::incrementAndGet));

  @Test
  public void shouldAppendEntryOnAllNodesWhenFollowerFailsFlush() throws Throwable {
    final var leader = raftRule.getLeader().get();
    // The number of failing nodes is less than the majority: (N - 1)/2;
    final var failingNodes = (MEMBERS - 1) / 2;
    // the leader must not be one of the failing nodes,
    // the priority election config should avoid this happening.
    assertThat(Integer.parseInt(leader.name())).isGreaterThan(failingNodes);

    final var index = raftRule.appendEntry();
    // await all nodes have processed this entry, otherwise we could set the flusher as faulty
    // before the faulty node had time to append this entry
    raftRule.awaitSameLogSizeOnAllNodes(index);

    // when
    // faulty nodes can't flush anymore
    LOG.debug("Setting flusher to faulty");
    isFaulty.set(true);

    // then
    final var commitListener = raftRule.appendEntryAsync();
    final var lastIndex = commitListener.awaitCommit(Duration.ofSeconds(5));

    Awaitility.await("Flush failed for all faulty nodes at least once")
        .until(() -> flushFailedCount.get() > failingNodes);

    // when
    // the faulty nodes can flush again successfully
    isFaulty.set(false);

    // then
    // all logs eventually converge
    raftRule.awaitSameLogSizeOnAllNodes(lastIndex);

    // all members are still registered
    assertThat(raftRule.getMemberLogs().size()).isEqualTo(MEMBERS);
    Awaitility.await("Until all members are FOLLOWER or LEADER")
        .untilAsserted(
            () -> {
              // all members are either LEADER or FOLLOWER
              final var roles = raftRule.getServers().stream().map(RaftServer::getRole).toList();
              assertThat(roles)
                  .withFailMessage(
                      String.format("Expected all members to be FOLLOWER or LEADER, got %s", roles))
                  .allMatch(r -> r == Role.FOLLOWER || r == Role.LEADER);
            });
  }

  private static RaftLogFlusher.Factory faultyFlusher(
      final Supplier<Boolean> faultyWhen, final Runnable notifyFaultyFlush) {
    return (ignored) ->
        new RaftLogFlusher() {
          @Override
          public void flush(final Journal journal) {
            if (faultyWhen.get()) {
              notifyFaultyFlush.run();
              throw new RuntimeException(new IOException("Failed sync"));
            } else {
              journal.flush();
            }
          }
        };
  }

  /**
   * Set the first faultyFlusherNumber nodes with a faulty flusher, when the supplier returns true
   */
  private record FaultyFlusherConfigurator(
      int faultyFlusherNumber, Supplier<Boolean> faultyWhen, Runnable notifyFaultyFlush)
      implements Configurator {

    @Override
    public void configure(final MemberId id, final Builder builder) {
      final var numericId = Integer.parseInt(id.id());
      // Node priority is used to avoid the faulty nodes to become leaders
      final int nodePriority;
      if (numericId <= faultyFlusherNumber) {
        LOG.trace("failing flusher for member {}", id);
        final var storage = builder.storage;
        Objects.requireNonNull(storage);
        builder.withStorage(
            RaftStorage.builder(builder.meterRegistry)
                .withDirectory(storage.directory())
                .withSnapshotStore(storage.getPersistedSnapshotStore())
                .withFlusherFactory(faultyFlusher(faultyWhen, notifyFaultyFlush))
                .build());
        nodePriority = 1;
      } else {
        LOG.trace("not failing flusher for member {} ", id);
        nodePriority = 5;
      }
      builder.withElectionConfig(RaftElectionConfig.ofPriorityElection(5, nodePriority));
    }
  }
}
