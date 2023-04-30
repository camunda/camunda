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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.atomix.cluster.MemberId;
import io.atomix.raft.cluster.RaftMember.Type;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitialEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaftLogTest {

  private final InitialEntry initialEntry = new InitialEntry();
  private final ConfigurationEntry configurationEntry =
      new ConfigurationEntry(
          1234L,
          Set.of(
              new DefaultRaftMember(MemberId.from("0"), Type.ACTIVE, Instant.ofEpochSecond(1234))));
  private final ByteBuffer data =
      ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 123456);
  private final ApplicationEntry applicationEntry = new ApplicationEntry(1, 2, data);
  private RaftLog raftlog;
  private RaftLogReader reader;

  @BeforeEach
  void setup(@TempDir final File directory) {
    raftlog =
        RaftLog.builder()
            .withDirectory(directory)
            .withName("test")
            .withMetaStore(new InMemory())
            .build();
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
    assertThat(entryRead.term()).isOne();
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
    assertThat(entryRead.term()).isOne();
    assertThat(entryRead.entry()).isInstanceOf(ConfigurationEntry.class);
    final var configurationRead = (ConfigurationEntry) entryRead.entry();
    assertThat(configurationRead.members())
        .containsExactlyInAnyOrderElementsOf(configurationEntry.members());
    assertThat(configurationRead.timestamp()).isEqualTo(configurationEntry.timestamp());
  }

  @Test
  void shouldAppendApplicationEntry() {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, applicationEntry);
    // when
    final var appended = raftlog.append(entry);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var entryRead = reader.next();
    assertThat(entryRead.term()).isOne();
    assertThat(entryRead.isApplicationEntry()).isTrue();
    assertThat(entryRead.getApplicationEntry().lowestPosition()).isOne();
    assertThat(entryRead.getApplicationEntry().highestPosition()).isEqualTo(2);
    assertThat(entryRead.getApplicationEntry().data()).isEqualTo(new UnsafeBuffer(data));
    assertThat(appended).isEqualTo(entryRead);
  }

  @Test
  void shouldUpdateLastAppendedEntry() {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, applicationEntry);
    // when
    final var appended = raftlog.append(entry);

    // then
    assertThat(raftlog.getLastEntry()).isEqualTo(appended);
  }

  @Test
  void shouldAppendPersistedRaftEntry(@TempDir final File directory) {
    // given
    final RaftLogEntry entry = new RaftLogEntry(1, applicationEntry);
    final var persistedRaftRecord = raftlog.append(entry).getPersistedRaftRecord();
    final var raftlogFollower =
        RaftLog.builder()
            .withDirectory(directory)
            .withName("test-follower")
            .withMetaStore(new InMemory())
            .build();

    // when
    final var appended = raftlogFollower.append(persistedRaftRecord);

    // then
    assertThat(raftlogFollower.getLastEntry()).isEqualTo(appended);
    assertThat(appended.index()).isOne();
    assertThat(appended.entry()).isEqualTo(applicationEntry);
    assertThat(appended.getPersistedRaftRecord().asqn())
        .isEqualTo(applicationEntry.lowestPosition());

    raftlogFollower.close();
  }

  @Test
  void shouldDeleteAfter() {
    // given
    raftlog.append(new RaftLogEntry(1, applicationEntry));
    final var secondEntry = raftlog.append(new RaftLogEntry(1, applicationEntry));
    raftlog.append(new RaftLogEntry(1, applicationEntry));

    // when
    raftlog.deleteAfter(secondEntry.index());

    // then
    assertThat(raftlog.getLastIndex()).isEqualTo(secondEntry.index());
    assertThat(raftlog.getLastEntry()).isEqualTo(secondEntry);
  }

  @Test
  void shouldNotDeleteCommittedEntries() {
    // given
    raftlog.append(new RaftLogEntry(1, applicationEntry));
    final var deleteIndex = raftlog.append(new RaftLogEntry(1, applicationEntry)).index();
    final var commitIndex = raftlog.append(new RaftLogEntry(1, applicationEntry)).index();

    // when
    raftlog.setCommitIndex(commitIndex);

    // then
    assertThatThrownBy(() -> raftlog.deleteAfter(deleteIndex))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldReset() {
    // given
    raftlog.append(new RaftLogEntry(1, applicationEntry));
    raftlog.append(new RaftLogEntry(1, applicationEntry));
    raftlog.append(new RaftLogEntry(1, applicationEntry));

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

  @Test
  void shouldFlushWhenFlushExplicitlyTrue() {
    // given
    final Journal journal = mock(Journal.class);
    final var log = new RaftLog(journal, true);

    // when
    log.flush();

    // then
    verify(journal).flush();
  }

  @Test
  void shouldNotFlushWhenFlushExplicitlyFalse() {
    // given
    final Journal journal = mock(Journal.class);
    final var log = new RaftLog(journal, false);

    // when
    log.flush();

    // then
    verify(journal, timeout(1).times(0)).flush();
  }
}
