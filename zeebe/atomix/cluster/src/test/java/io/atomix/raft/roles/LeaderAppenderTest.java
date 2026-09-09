/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.cluster.impl.RaftMemberContext;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.VersionedAppendRequest;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.utils.concurrent.ThreadContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests how the leader maintains its view of a follower's replication progress. The appender is
 * driven through a mocked {@link RaftContext}: every append it sends is answered by completing the
 * future the mocked protocol handed out for it, and responses are handled inline, which makes their
 * handling order deterministic.
 */
final class LeaderAppenderTest {

  private static final long TERM = 1L;
  private static final long LAST_LOG_INDEX = 100L;
  private static final long LOWER_INDEX = 10L;
  private static final long HIGHER_INDEX = 20L;

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  /** The leader's view of the follower's match index, the state under test. */
  private final AtomicLong matchIndex = new AtomicLong();

  private final CompletableFuture<AppendResponse> firstAppend = new CompletableFuture<>();
  private final CompletableFuture<AppendResponse> secondAppend = new CompletableFuture<>();

  private LeaderAppender appender;

  @BeforeEach
  void setup() {
    final var context = mock(RaftContext.class, RETURNS_DEEP_STUBS);
    final var threadContext = inlineThreadContext();
    final var log = mock(RaftLog.class);
    final var follower = followerContext();

    when(context.getName()).thenReturn("leader");
    when(context.getMeterRegistry()).thenReturn(meterRegistry);
    when(context.getThreadContext()).thenReturn(threadContext);
    when(context.getElectionTimeout()).thenReturn(Duration.ofMillis(100));
    when(context.getMaxQuorumResponseTimeout()).thenReturn(Duration.ofSeconds(1));
    when(context.getTerm()).thenReturn(TERM);
    when(context.getCommitIndex()).thenReturn(0L);
    when(context.getCurrentSnapshot()).thenReturn(null);
    when(context.getLeader())
        .thenReturn(new DefaultRaftMember(MemberId.from("0"), Type.ACTIVE, Instant.now()));

    when(log.getLastIndex()).thenReturn(LAST_LOG_INDEX);
    when(context.getLog()).thenReturn(log);

    when(context.getCluster().getReplicationTargets()).thenReturn(Set.of(follower));
    // no quorum progress, so that handling a response neither commits nor triggers new heartbeats
    when(context.getCluster().<Long>getQuorumFor(any())).thenReturn(Optional.of(0L));
    when(context.getProtocol().append(any(MemberId.class), any(VersionedAppendRequest.class)))
        .thenReturn(firstAppend, secondAppend);

    // the appender is only reachable through a leader role, which is not started here because the
    // appender is driven directly
    appender = new LeaderAppender(new LeaderRole(context));
  }

  @AfterEach
  void tearDown() {
    appender.close();
    meterRegistry.close();
  }

  @Test
  void shouldNotRegressMatchIndexOnReorderedSuccessfulResponses() {
    // given - two appends are in flight to the same follower
    appender.appendEntries(LOWER_INDEX);
    appender.appendEntries(HIGHER_INDEX);

    // when - the follower acknowledges the higher index first and the lower one only after, e.g.
    // because it acknowledged already durable records ahead of records it was still persisting
    firstAppend.complete(response(true, HIGHER_INDEX));
    secondAppend.complete(response(true, LOWER_INDEX));

    // then
    assertThat(matchIndex)
        .describedAs("the leader keeps the highest acknowledged index")
        .hasValue(HIGHER_INDEX);
  }

  @Test
  void shouldAdvanceMatchIndexOnSuccessfulResponses() {
    // given
    appender.appendEntries(LOWER_INDEX);
    appender.appendEntries(HIGHER_INDEX);

    // when
    firstAppend.complete(response(true, LOWER_INDEX));
    secondAppend.complete(response(true, HIGHER_INDEX));

    // then
    assertThat(matchIndex)
        .describedAs("the leader follows the follower's progress")
        .hasValue(HIGHER_INDEX);
  }

  @Test
  void shouldLowerMatchIndexOnFailedResponse() {
    // given
    appender.appendEntries(LOWER_INDEX);
    appender.appendEntries(HIGHER_INDEX);

    // when - a failed response reports that the follower does not have the higher index after all
    firstAppend.complete(response(true, HIGHER_INDEX));
    secondAppend.complete(response(false, LOWER_INDEX));

    // then
    assertThat(matchIndex)
        .describedAs("a failed response is the follower reporting that it lost records")
        .hasValue(LOWER_INDEX);
  }

  private AppendResponse response(final boolean succeeded, final long lastLogIndex) {
    return AppendResponse.builder()
        .withStatus(Status.OK)
        .withTerm(TERM)
        .withSucceeded(succeeded)
        .withLastLogIndex(lastLogIndex)
        .withLastSnapshotIndex(0)
        .withConfigurationIndex(0)
        .build();
  }

  /**
   * A thread context which runs responses inline, on the thread completing them, so that a response
   * is fully handled once its future is completed.
   */
  private ThreadContext inlineThreadContext() {
    final var threadContext = mock(ThreadContext.class);
    doAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(threadContext)
        .execute(any(Runnable.class));
    return threadContext;
  }

  /**
   * A follower which is always ready for a heartbeat and has no entries left to replicate, so that
   * every {@code appendEntries} sends exactly one empty append to it. Its match index is stateful,
   * as that is what the tests assert on.
   */
  private RaftMemberContext followerContext() {
    final var follower = mock(RaftMemberContext.class);
    when(follower.getMember())
        .thenReturn(new DefaultRaftMember(MemberId.from("1"), Type.ACTIVE, Instant.now()));
    when(follower.isOpen()).thenReturn(true);
    when(follower.hasReplicationContext()).thenReturn(true);
    when(follower.hasNextEntry()).thenReturn(false);
    // an outdated configuration term keeps the appender on the configure-or-heartbeat path, where
    // it sends an empty append without having to read from the log
    when(follower.getConfigTerm()).thenReturn(TERM - 1);
    when(follower.canConfigure()).thenReturn(false);
    when(follower.canHeartbeat()).thenReturn(true);
    when(follower.getMatchIndex()).thenAnswer(invocation -> matchIndex.get());
    doAnswer(
            invocation -> {
              matchIndex.set(invocation.getArgument(0, Long.class));
              return null;
            })
        .when(follower)
        .setMatchIndex(anyLong());
    return follower;
  }
}
