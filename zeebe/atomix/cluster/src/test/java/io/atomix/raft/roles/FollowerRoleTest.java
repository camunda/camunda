/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.raft.ElectionTimer;
import io.atomix.raft.ElectionTimerFactory;
import io.atomix.raft.RaftError.Type;
import io.atomix.raft.RaftServer;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.TimeoutNowRequest;
import io.atomix.raft.protocol.TimeoutNowResponse;
import io.atomix.utils.concurrent.ThreadContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class FollowerRoleTest {

  private static final MemberId LEADER = MemberId.from("1");
  private static final long TERM = 2;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private RaftContext raft;
  private FollowerRole role;
  private ArgumentCaptor<Runnable> raftThreadTasks;

  @BeforeEach
  void setUp() {
    raft = mock(RaftContext.class);
    when(raft.getName()).thenReturn("partition-1");
    when(raft.getMeterRegistry()).thenReturn(meterRegistry);
    when(raft.getTerm()).thenReturn(TERM);
    when(raft.getLeader()).thenReturn(member(LEADER));

    final var threadContext = mock(ThreadContext.class);
    raftThreadTasks = ArgumentCaptor.forClass(Runnable.class);
    doAnswer(invocation -> null).when(threadContext).execute(raftThreadTasks.capture());
    when(raft.getThreadContext()).thenReturn(threadContext);

    final ElectionTimerFactory electionTimerFactory =
        (triggerElection, logger) -> mock(ElectionTimer.class);
    role = new FollowerRole(raft, electionTimerFactory);
  }

  @Test
  void shouldRejectRequestInvalidatedBeforeTheTransition() {
    // given
    final var response = role.onTimeoutNow(timeoutNow());

    // when
    when(raft.getLeader()).thenReturn(member(MemberId.from("2")));
    runRaftThreadTask();

    // then
    assertThat(response)
        .succeedsWithin(Duration.ofSeconds(5))
        .satisfies(
            rejection -> {
              assertThat(rejection.status()).isEqualTo(Status.ERROR);
              assertThat(rejection.error().type()).isEqualTo(Type.ILLEGAL_MEMBER_STATE);
            });
    verify(raft, never()).transition(any(RaftServer.Role.class));
  }

  @Test
  void shouldAcknowledgeBeforeTheTransitionCompletes() throws InterruptedException {
    // given
    final var transitionStarted = new CountDownLatch(1);
    final var releaseTransition = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              transitionStarted.countDown();
              releaseTransition.await(30, TimeUnit.SECONDS);
              return null;
            })
        .when(raft)
        .transition(Role.CANDIDATE);

    // when
    final var response = role.onTimeoutNow(timeoutNow());
    final var transition = new Thread(this::runRaftThreadTask, "stalled-transition");
    transition.start();

    // then
    try {
      assertThat(transitionStarted.await(30, TimeUnit.SECONDS)).isTrue();
      assertThat(response)
          .succeedsWithin(Duration.ofSeconds(5))
          .extracting(TimeoutNowResponse::status)
          .isEqualTo(Status.OK);
    } finally {
      releaseTransition.countDown();
      transition.join();
    }
  }

  private void runRaftThreadTask() {
    raftThreadTasks.getValue().run();
  }

  private TimeoutNowRequest timeoutNow() {
    return TimeoutNowRequest.builder().withTerm(TERM).withLeader(LEADER).build();
  }

  private static DefaultRaftMember member(final MemberId id) {
    return new DefaultRaftMember(id, RaftMember.Type.ACTIVE, Instant.now());
  }
}
