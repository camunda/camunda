/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.raft.FaultyFlusherConfigurator;
import io.atomix.raft.RaftRule;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaftFollowerFlushErrorTest {

  private static final Logger LOG = LoggerFactory.getLogger(RaftFollowerFlushErrorTest.class);
  private static final int MEMBERS = 3;
  public AtomicBoolean isFaulty = new AtomicBoolean(false);
  public AtomicInteger flushFailedCount = new AtomicInteger(0);

  @Rule
  public RaftRule raftRule =
      RaftRule.withBootstrappedNodes(
          MEMBERS,
          new FaultyFlusherConfigurator(
              (MEMBERS - 1) / 2, isFaulty::get, flushFailedCount::incrementAndGet, false, false));

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
}
