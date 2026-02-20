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
package io.atomix.raft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.atomix.cluster.MemberId;
import io.atomix.raft.partition.RaftPartitionConfig;
import io.atomix.raft.roles.LeaderRole;
import io.atomix.raft.storage.log.RaftLogFlusher;
import io.atomix.raft.zeebe.ZeebeLogAppender.AppendListener;
import io.camunda.zeebe.journal.CheckedJournalException.FlushException;
import io.camunda.zeebe.journal.Journal;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaftBatchAppendTest {

  @AutoClose("shutdown")
  private ControllableRaftContexts raftContexts;

  private final Map<MemberId, CountingFlusher> flushers = new HashMap<>();
  private MemberId leaderId;
  private List<MemberId> followerIds;
  private int nextEntry = 0;

  @TempDir private Path raftDataDirectory;

  @BeforeEach
  void before() throws Exception {
    final var config = new RaftPartitionConfig();
    config.setMaxAppendsPerFollower(8);
    raftContexts = new ControllableRaftContexts(3);
    raftContexts.withPartitionConfig(config);

    for (int i = 0; i < 3; i++) {
      final var memberId = MemberId.from(String.valueOf(i));
      final var flusher = new CountingFlusher();
      flushers.put(memberId, flusher);
      raftContexts.withStorageConfigurator(
          memberId, builder -> builder.withFlusherFactory(ignored -> flusher));
    }

    raftContexts.setup(raftDataDirectory, new Random(1));
    raftContexts.awaitClusterReady();

    final var leaders = raftContexts.getMembersWithRole(RaftServer.Role.LEADER);
    assertThat(leaders).hasSize(1);
    leaderId = leaders.getFirst();

    followerIds = raftContexts.getMembersWithRole(RaftServer.Role.FOLLOWER);
    assertThat(followerIds).hasSize(2);
  }

  @Test
  void shouldFlushOnceWhenBatchingMultipleAppends() {
    // given - append 5 entries on the leader
    final long logBefore = getLogLastIndex(leaderId);
    appendEntriesOnLeader(5);
    raftContexts.getDeterministicScheduler(leaderId).runUntilIdle();
    assertThat(getLogLastIndex(leaderId)).isEqualTo(logBefore + 5);

    // when - deliver and process all messages on each follower
    for (final var followerId : followerIds) {
      raftContexts.getServerProtocol(followerId).receiveAll();
      flushers.get(followerId).reset();
      raftContexts.getDeterministicScheduler(followerId).runUntilIdle();
    }

    // then - each follower replicated all entries with a single flush
    for (final var followerId : followerIds) {
      assertLogMatchesLeader(followerId);
      assertFlushCount(followerId, 1);
    }
  }

  private void appendEntriesOnLeader(final int count) {
    final var leaderRole = (LeaderRole) raftContexts.getRaftContext(leaderId).getRaftRole();
    final var listener = mock(AppendListener.class);
    for (int i = 0; i < count; i++) {
      final ByteBuffer data = ByteBuffer.allocate(Integer.BYTES).putInt(0, nextEntry++);
      leaderRole.appendEntry(nextEntry, nextEntry, data, listener);
    }
  }

  private long getLogLastIndex(final MemberId memberId) {
    return raftContexts.getRaftContext(memberId).getLog().getLastIndex();
  }

  private void assertLogMatchesLeader(final MemberId followerId) {
    assertThat(getLogLastIndex(followerId))
        .describedAs("Follower %s should have replicated all entries", followerId)
        .isEqualTo(getLogLastIndex(leaderId));
  }

  private void assertFlushCount(final MemberId followerId, final int expected) {
    assertThat(flushers.get(followerId).flushCount())
        .describedAs(
            "Follower %s: batched appends should result in %d flush(es)", followerId, expected)
        .isEqualTo(expected);
  }

  static class CountingFlusher implements RaftLogFlusher {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    public void flush(final Journal journal) throws FlushException {
      count.incrementAndGet();
      journal.flush();
    }

    @Override
    public boolean isDirect() {
      return true;
    }

    int flushCount() {
      return count.get();
    }

    void reset() {
      count.set(0);
    }
  }
}
