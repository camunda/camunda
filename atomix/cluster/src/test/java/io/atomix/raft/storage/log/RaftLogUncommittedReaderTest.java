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

import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RaftLogUncommittedReaderTest {

  private RaftLog raftlog;
  private RaftLogReader uncommittedReader;

  private final ByteBuffer data =
      ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(0, 123456);

  @BeforeEach
  void setup(@TempDir final File directory) {
    raftlog =
        RaftLog.builder()
            .withDirectory(directory)
            .withName("test")
            .withMetaStore(new InMemory())
            .build();
    uncommittedReader = raftlog.openUncommittedReader();
    data.order(ByteOrder.LITTLE_ENDIAN).putInt(123456);
  }

  @AfterEach
  void tearDown() {
    uncommittedReader.close();
    raftlog.close();
  }

  @Test
  void shouldReadUncommittedEntries() {
    // when
    appendEntries(2);

    // then
    assertThat(uncommittedReader.hasNext()).isTrue();
    assertThat(uncommittedReader.next().index()).isOne();

    assertThat(uncommittedReader.hasNext()).isTrue();
    assertThat(uncommittedReader.next().index()).isEqualTo(2);

    assertThat(uncommittedReader.hasNext()).isFalse();
  }

  @Test
  void shouldSeekToIndex() {
    // given
    appendEntries(10);

    // when
    final var nextIndex = uncommittedReader.seek(5);

    // then
    assertThat(nextIndex).isEqualTo(5);
    assertThat(uncommittedReader.hasNext()).isTrue();
    assertThat(uncommittedReader.next().index()).isEqualTo(5);
  }

  @Test
  void shouldSeekToIndexAfterTruncate() {
    // given
    appendEntries(6);
    uncommittedReader.seek(5);

    // when
    raftlog.deleteAfter(3);
    final var nextIndex = uncommittedReader.seek(5);

    // then
    assertThat(nextIndex).isEqualTo(4);
    assertThat(uncommittedReader.hasNext()).isFalse();
  }

  @Test
  void shouldSeekToLast() {
    // given
    appendEntries(5);

    // when
    final var nextIndex = uncommittedReader.seekToLast();

    // then
    assertThat(nextIndex).isEqualTo(5);
    assertThat(uncommittedReader.hasNext()).isTrue();
    assertThat(uncommittedReader.next().index()).isEqualTo(5);
  }

  @Test
  void shouldSeekToAsqn() {
    // given
    appendEntries(10);

    // when
    final var nextIndex = uncommittedReader.seekToAsqn(5);

    // then
    assertThat(nextIndex).isEqualTo(5);
    assertThat(uncommittedReader.hasNext()).isTrue();
    assertThat(uncommittedReader.next().getPersistedRaftRecord().asqn()).isEqualTo(5);
  }

  @Test
  void shouldSeekToLastAsqn() {
    // given
    appendEntries(10);

    // when
    final var nextIndex = uncommittedReader.seekToAsqn(Long.MAX_VALUE);

    // then
    assertThat(nextIndex).isEqualTo(10);
    assertThat(uncommittedReader.hasNext()).isTrue();
    assertThat(uncommittedReader.next().getPersistedRaftRecord().asqn()).isEqualTo(10);
  }

  private void appendEntries(final int count) {
    for (int i = 0; i < count; i++) {
      final ApplicationEntry applicationEntry = new ApplicationEntry(i + 1, i + 1, data);
      final RaftLogEntry entry = new RaftLogEntry(1, applicationEntry);
      raftlog.append(entry);
    }
  }
}
