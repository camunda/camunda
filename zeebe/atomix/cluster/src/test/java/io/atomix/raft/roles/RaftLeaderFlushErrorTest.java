/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.FaultyFlusherConfigurator;
import io.atomix.raft.RaftException.AppendFailureException;
import io.atomix.raft.RaftRule;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class RaftLeaderFlushErrorTest {

  private static final Logger LOG = LoggerFactory.getLogger(RaftLeaderFlushErrorTest.class);
  private static final int MEMBERS = 3;

  @Parameter public boolean withDataLoss;

  @Rule
  @Parameter(1)
  public RaftRule raftRule; // =

  @Parameter(2)
  public AtomicBoolean isFaulty;

  @Parameter(3)
  public AtomicInteger flushFailedCount;

  @Parameters(name = "{index}: withDataLoss = {0}")
  public static Collection<Object[]> parameters() {
    return Stream.of(true, false)
        .map(
            withDataLoss -> {
              final var isFaulty = new AtomicBoolean(false);
              final var flushFailedCount = new AtomicInteger(0);
              return new Object[] {
                withDataLoss,
                RaftRule.withBootstrappedNodes(
                    MEMBERS,
                    new FaultyFlusherConfigurator(
                        (MEMBERS - 1) / 2,
                        isFaulty::get,
                        flushFailedCount::incrementAndGet,
                        true,
                        withDataLoss)),
                isFaulty,
                flushFailedCount
              };
            })
        .toList();
  }

  @Test
  public void shouldTransitionToFollowerWhenLeaderFailsToFlush() throws Exception {
    // given
    final var leader = raftRule.getLeader().get();
    // check that the leader has a faulty flusher
    assertThat(leader.name()).isEqualTo("1");

    // get last index
    raftRule.appendEntry();

    // flusher fails to flush
    isFaulty.set(true);

    LOG.info("Leader flusher set to faulty");
    // when
    final var commitListener = raftRule.appendEntryAsync();

    // then
    assertThatThrownBy(() -> commitListener.awaitCommit(Duration.ofSeconds(2)))
        .isExactlyInstanceOf(AppendFailureException.class);

    // when
    isFaulty.set(false);
    LOG.info("Leader flusher is not faulty anymore");

    assertThatThrownBy(() -> commitListener.awaitCommit(Duration.ofSeconds(2)))
        .isExactlyInstanceOf(AppendFailureException.class);

    // then
    // await new leader
    raftRule.awaitNewLeader();
    LOG.debug("appending a new entry after leader change");
    // appending a new entry succeeds
    final var finalIndex = raftRule.appendEntry();
    // all nodes have the same entry
    raftRule.awaitSameLogSizeOnAllNodes(finalIndex);
  }
}
