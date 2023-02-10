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
package io.atomix.raft.storage.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.log.RaftLogFlusher.DirectFlusher;
import io.atomix.raft.storage.log.RaftLogFlusher.NoopFlusher;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.journal.Journal;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaftLogTest {
  private static final long DEFAULT_APPLICATION_ENTRY_LENGTH = 2L;

  private final InitialEntry initialEntry = new InitialEntry();
  private final ConfigurationEntry configurationEntry =
      new ConfigurationEntry(
          1234L,
          Set.of(
              new DefaultRaftMember(MemberId.from("0"), Type.ACTIVE, Instant.ofEpochSecond(1234))));
  private final ByteBuffer data =
      ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 123456);
  private final ApplicationEntry firstApplicationEntry = createApplicationEntry(1);
  private RaftLog raftlog;
  private RaftLogReader reader;

  @BeforeEach
  void setup(@TempDir final File directory) {
    raftlog = RaftLog.builder().withDirectory(directory).withName("test").build();
    reader = raftlog.openUncommittedReader();
  }

  @AfterEach
  void tearDown() {
    reader.close();
    raftlog.close();
  }

  @Test
  void shouldAppendInitialEntry() {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, initialEntry);
    // when
    final var appended = raftlog.append(entry);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var entryRead = reader.next();
    assertThat(entryRead.term()).isEqualTo(1);
    assertThat(entryRead.entry()).isInstanceOf(InitialEntry.class);
    assertThat(appended).isEqualTo(entryRead);
  }

  @Test
  void shouldAppendConfigurationEntry() {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, configurationEntry);
    // when
    raftlog.append(entry);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var entryRead = reader.next();
    assertThat(entryRead.term()).isEqualTo(1);
    assertThat(entryRead.entry()).isInstanceOf(ConfigurationEntry.class);
    final var configurationRead = (ConfigurationEntry) entryRead.entry();
    assertThat(configurationRead.members())
        .containsExactlyInAnyOrderElementsOf(configurationEntry.members());
    assertThat(configurationRead.timestamp()).isEqualTo(configurationEntry.timestamp());
  }

  @Test
  void shouldAppendApplicationEntry() {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, firstApplicationEntry);
    // when
    final var appended = raftlog.append(entry);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var entryRead = reader.next();
    assertThat(entryRead.term()).isEqualTo(1);
    assertThat(entryRead.isApplicationEntry()).isTrue();
    assertThat(entryRead.getApplicationEntry().lowestPosition()).isEqualTo(1);
    assertThat(entryRead.getApplicationEntry().highestPosition()).isEqualTo(2);
    assertThat(((SerializedApplicationEntry) entryRead.getApplicationEntry()).data())
        .isEqualTo(new UnsafeBuffer(data));
    assertThat(appended).isEqualTo(entryRead);
  }

  @Test
  void shouldUpdateLastAppendedEntry() {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, firstApplicationEntry);
    // when
    final var appended = raftlog.append(entry);

    // then
    assertThat(raftlog.getLastEntry()).isEqualTo(appended);
  }

  @Test
  void shouldAppendPersistedRaftEntry(@TempDir final File directory) {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, firstApplicationEntry);
    final var persistedRaftRecord = raftlog.append(entry).getPersistedRaftRecord();
    final var raftlogFollower =
        RaftLog.builder().withDirectory(directory).withName("test-follower").build();

    // when
    final var appended = raftlogFollower.append(persistedRaftRecord);

    // then
    assertThat(raftlogFollower.getLastEntry()).isEqualTo(appended);
    assertThat(appended.index()).isEqualTo(1);
    assertThat(appended.entry()).isEqualTo(firstApplicationEntry);
    assertThat(appended.getPersistedRaftRecord().asqn())
        .isEqualTo(firstApplicationEntry.lowestPosition());

    raftlogFollower.close();
  }

  @Test
  void shouldDeleteAfter() {
    // given
    raftlog.append(new RaftLogEntry(1, firstApplicationEntry));
    final var secondEntry =
        raftlog.append(new RaftLogEntry(1, createApplicationEntryAfter(firstApplicationEntry)));
    raftlog.append(
        new RaftLogEntry(1, createApplicationEntryAfter(secondEntry.getApplicationEntry())));

    // when
    raftlog.deleteAfter(secondEntry.index());

    // then
    assertThat(raftlog.getLastIndex()).isEqualTo(secondEntry.index());
    assertThat(raftlog.getLastEntry()).isEqualTo(secondEntry);
  }

  @Test
  void shouldNotDeleteCommittedEntries() {
    // given
    raftlog.append(new RaftLogEntry(1, firstApplicationEntry));
    final ApplicationEntry secondApplicationEntry =
        createApplicationEntryAfter(firstApplicationEntry);
    final var deleteIndex = raftlog.append(new RaftLogEntry(1, secondApplicationEntry)).index();
    final var commitIndex =
        raftlog
            .append(new RaftLogEntry(1, createApplicationEntryAfter(secondApplicationEntry)))
            .index();

    // when
    raftlog.setCommitIndex(commitIndex);

    // then
    assertThatThrownBy(() -> raftlog.deleteAfter(deleteIndex))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldReset() {
    // given
    raftlog.append(new RaftLogEntry(1, firstApplicationEntry));
    final IndexedRaftLogEntry secondEntry =
        raftlog.append(new RaftLogEntry(1, createApplicationEntryAfter(firstApplicationEntry)));
    raftlog.append(
        new RaftLogEntry(1, createApplicationEntryAfter(secondEntry.getApplicationEntry())));

    // when
    raftlog.reset(10);

    // then
    assertThat(raftlog.getLastIndex()).isEqualTo(9);
    assertThat(raftlog.getLastEntry()).isNull();
    assertThat(raftlog.getFirstIndex()).isEqualTo(10);
  }

  @Test
  void shouldSetCommitIndex() {
    // when
    raftlog.setCommitIndex(10);

    // then
    assertThat(raftlog.getCommitIndex()).isEqualTo(10);
  }

  private ApplicationEntry createApplicationEntryAfter(final ApplicationEntry applicationEntry) {
    return createApplicationEntry(applicationEntry.highestPosition() + 1);
  }

  private ApplicationEntry createApplicationEntry(final long lowestPosition) {
    // -1 on highest position as the lowestPosition is inclusive
    return new SerializedApplicationEntry(
        lowestPosition, lowestPosition + DEFAULT_APPLICATION_ENTRY_LENGTH - 1, data);
  }

  @Nested
  final class FlushTest {
    @Test
    void shouldUseFlusher() {
      // given
      final var journal = mock(Journal.class);
      final var flusher = mock(RaftLogFlusher.class);
      final var log = new RaftLog(journal, flusher);

      // when
      log.flush();

      // then
      verify(flusher, times(1)).flush(journal);
    }

    @Test
    void shouldForceFlush() {
      final var journal = mock(Journal.class);
      final var flusher = mock(RaftLogFlusher.class);
      final var log = new RaftLog(journal, flusher);

      // when
      log.forceFlush();

      // then
      verify(journal, times(1)).flush();
    }

    @Test
    void shouldFlushDirectly() {
      // given
      final var journal = mock(Journal.class);
      final var log = new RaftLog(journal, new DirectFlusher());

      // when
      log.flush();

      // then
      verify(journal, times(1)).flush();
    }

    @Test
    void shouldDisableFlush() {
      // given
      final var journal = mock(Journal.class);
      final var flusher = spy(new NoopFlusher());
      final var log = new RaftLog(journal, flusher);

      // when
      log.flush();

      // then
      verify(flusher, times(1)).flush(journal);
      verify(journal, never()).flush();
    }
  }
}
