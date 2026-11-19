/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.storage.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.storage.log.entry.SerializedApplicationEntry;
import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Committed entries that read back as zeros, as after a lost writeback, look like the end of the
 * log to a reader. See https://github.com/camunda/camunda/issues/69499.
 */
class RaftLogCommittedReaderMissingEntryTest {

  // small enough that a few hundred entries span several segments
  private static final int SEGMENT_SIZE = 8 * 1024;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final ByteBuffer data =
      ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(123456);

  private @TempDir Path directory;
  private RaftLog raftLog;
  private RaftLogReader reader;

  @BeforeEach
  void setup() {
    raftLog =
        RaftLog.builder(meterRegistry)
            .withDirectory(directory.toFile())
            .withName("test")
            .withMaxSegmentSize(SEGMENT_SIZE)
            .withMetaStore(new InMemory())
            .build();
    reader = raftLog.openCommittedReaderFailingOnMissingEntries();
  }

  @AfterEach
  void tearDown() {
    reader.close();
    raftLog.close();
  }

  @Test
  void shouldFailWhenCommittedEntryReadsBackAsZeros() throws IOException {
    // given
    appendEntries(3);
    zeroEntriesAppendedBy(() -> appendEntries(3));
    raftLog.setCommitIndex(6);

    // when
    for (int index = 1; index <= 3; index++) {
      assertThat(reader.next().index()).isEqualTo(index);
    }

    // then
    assertThatThrownBy(reader::hasNext)
        .isInstanceOf(CorruptedJournalException.class)
        .hasMessageContaining("committed entry at index 4 (commit index 6)");
  }

  @Test
  void shouldFailWhenSeekingIntoZeroedCommittedEntries() throws IOException {
    // given
    appendEntries(3);
    zeroEntriesAppendedBy(() -> appendEntries(3));
    raftLog.setCommitIndex(6);

    // when
    reader.seek(5);

    // then
    assertThatThrownBy(reader::hasNext).isInstanceOf(CorruptedJournalException.class);
  }

  @Test
  void shouldNotFailOnZeroedEntriesThatAreNotCommitted() throws IOException {
    // given
    appendEntries(3);
    zeroEntriesAppendedBy(() -> appendEntries(3));
    raftLog.setCommitIndex(3);

    // when
    reader.seek(4);

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldReportEndOfLogOnZeroedCommittedEntriesWhenNotAskedToFail() throws IOException {
    // given
    appendEntries(3);
    zeroEntriesAppendedBy(() -> appendEntries(3));
    raftLog.setCommitIndex(6);

    // when
    try (final var lenientReader = raftLog.openCommittedReader()) {
      lenientReader.seek(4);

      // then
      assertThat(lenientReader.hasNext()).isFalse();
    }
  }

  @Test
  void shouldNotFailWhenSegmentBeingReadIsCompacted() {
    // given - a reader in the first of several segments
    appendEntries(500);
    raftLog.setCommitIndex(500);
    assertThat(reader.next().index()).isEqualTo(1);

    // when
    assertThat(raftLog.deleteUntil(400)).isTrue();
    assertThat(raftLog.getFirstIndex()).isGreaterThan(2);

    // then - nothing to read until the reader is sought to an index that still exists
    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.seek(raftLog.getFirstIndex())).isEqualTo(raftLog.getFirstIndex());
    assertThat(reader.next().index()).isEqualTo(raftLog.getFirstIndex());
  }

  @Test
  void shouldNotFailWhenCompactedSegmentWasReadToItsEnd() {
    // given - a reader that read its segment to the end, so the next entry is in the next segment
    appendEntries(500);
    raftLog.setCommitIndex(500);
    // every entry's ASQN is its index
    final long firstIndexOfKeptSegment = raftLog.getTailSegments(400).firstAsqn().orElseThrow();
    while (reader.next().index() < firstIndexOfKeptSegment - 1) {
      // read on
    }

    // when
    assertThat(raftLog.deleteUntil(400)).isTrue();
    assertThat(raftLog.getFirstIndex()).isEqualTo(firstIndexOfKeptSegment);

    // then - the next entry exists, the reader just has to be sought to it
    assertThat(reader.hasNext()).isFalse();
    assertThat(reader.seek(firstIndexOfKeptSegment)).isEqualTo(firstIndexOfKeptSegment);
    assertThat(reader.next().index()).isEqualTo(firstIndexOfKeptSegment);
  }

  @Test
  void shouldNotFailWhenLogIsResetForSnapshot() {
    // given
    appendEntries(5);
    raftLog.setCommitIndex(3);
    assertThat(reader.next().index()).isEqualTo(1);

    // when
    raftLog.reset(10);

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldNotFailWhileEntriesAreAppendedCommittedAndCompactedConcurrently() throws Exception {
    // given
    final int entryCount = 20_000;
    final var readerFailed = new AtomicBoolean();
    final var lastReadIndex =
        CompletableFuture.supplyAsync(
            () -> {
              long lastIndex = 0;
              try {
                while (lastIndex < entryCount) {
                  if (reader.hasNext()) {
                    final long index = reader.next().index();
                    assertThat(index).isGreaterThan(lastIndex);
                    lastIndex = index;
                  } else {
                    // left behind by compaction, or at the end of what is committed so far
                    lastIndex = Math.max(lastIndex, reader.seek(lastIndex + 1) - 1);
                    Thread.onSpinWait();
                  }
                }
                return lastIndex;
              } catch (final Throwable e) {
                readerFailed.set(true);
                throw e;
              }
            });

    // when
    for (int index = 1; index <= entryCount && !readerFailed.get(); index++) {
      appendEntries(1);
      raftLog.setCommitIndex(index);
      if (index % 1000 == 0) {
        raftLog.deleteUntil(index - 500);
      }
    }

    // then
    assertThatCode(() -> lastReadIndex.get(30, TimeUnit.SECONDS)).doesNotThrowAnyException();
    assertThat(lastReadIndex.join()).isEqualTo(entryCount);
  }

  private void appendEntries(final int count) {
    for (int i = 0; i < count; i++) {
      final long position = raftLog.getLastIndex() + 1;
      final var applicationEntry = new SerializedApplicationEntry(position, position, data);
      raftLog.append(new RaftLogEntry(1, applicationEntry));
    }
  }

  /**
   * Writes zeros over what the given action appends, through the file rather than the mapping the
   * log reads from, which then reads the zeros back.
   */
  private void zeroEntriesAppendedBy(final Runnable append) throws IOException {
    final var segment = onlySegment();
    final long from = endOfData(segment);
    append.run();
    final long until = endOfData(segment);
    assertThat(until).isGreaterThan(from);

    try (final var channel = FileChannel.open(segment, StandardOpenOption.WRITE)) {
      final var zeros = ByteBuffer.allocate(Math.toIntExact(until - from));
      while (zeros.hasRemaining()) {
        channel.write(zeros, from + zeros.position());
      }
    }
  }

  private Path onlySegment() throws IOException {
    try (final var files = Files.list(directory)) {
      final var segments = files.filter(file -> file.toString().endsWith(".log")).toList();
      assertThat(segments).hasSize(1);
      return segments.getFirst();
    }
  }

  /** Returns the offset after the last non-zero byte; segments are pre-allocated with zeros. */
  private static long endOfData(final Path segment) throws IOException {
    final byte[] bytes = Files.readAllBytes(segment);
    int end = bytes.length;
    while (end > 0 && bytes[end - 1] == 0) {
      end--;
    }
    return end;
  }
}
