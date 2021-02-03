/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.journal.JournalRecord;
import io.zeebe.journal.StorageException.InvalidChecksum;
import io.zeebe.journal.StorageException.InvalidIndex;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SegmentedJournalTest {

  @TempDir Path directory;
  final DirectBuffer data = new UnsafeBuffer();
  private SegmentedJournal journal;
  private byte[] entry;
  private int entriesPerSegment;
  private final int journalIndexDensity = 5;

  @BeforeEach
  public void setup() {
    final var namespace =
        new Namespace.Builder()
            .register(Namespaces.BASIC)
            .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
            .register(PersistedJournalRecord.class)
            .register(UnsafeBuffer.class)
            .name("Journal")
            .build();

    entry = "TestData".getBytes();
    data.wrap(entry);

    final var entrySize =
        namespace.serialize(new PersistedJournalRecord(1, 1, Integer.MAX_VALUE, data)).length
            + Integer.BYTES;
    entriesPerSegment = 10;

    journal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data").toFile())
            .withMaxSegmentSize(entriesPerSegment * entrySize + JournalSegmentDescriptor.BYTES)
            .withJournalIndexDensity(journalIndexDensity)
            .build();
  }

  @Test
  public void shouldBeEmpty() {
    // when-then
    assertThat(journal.isEmpty()).isTrue();
  }

  @Test
  public void shouldNotBeEmpty() {
    // given
    journal.append(1, data);

    // when-then
    assertThat(journal.isEmpty()).isFalse();
  }

  @Test
  public void shouldAppendData() {
    // when
    final var recordAppended = journal.append(1, data);
    assertThat(recordAppended.index()).isEqualTo(1);
    assertThat(recordAppended.asqn()).isEqualTo(1);

    // then
    final var recordRead = journal.openReader().next();
    assertThat(recordAppended.index()).isEqualTo(recordRead.index());
    assertThat(recordAppended.asqn()).isEqualTo(recordRead.asqn());
    assertThat(recordAppended.checksum()).isEqualTo(recordRead.checksum());
  }

  @Test
  public void shouldAppendMultipleRecords() {
    // when
    for (int i = 0; i < 10; i++) {
      final var recordAppended = journal.append(i + 10, data);
      assertThat(recordAppended.index()).isEqualTo(i + 1);
    }

    // then
    final var reader = journal.openReader();
    for (int i = 0; i < 10; i++) {
      assertThat(reader.hasNext()).isTrue();
      final var recordRead = reader.next();
      assertThat(recordRead.index()).isEqualTo(i + 1);
      final byte[] data = new byte[recordRead.data().capacity()];
      recordRead.data().getBytes(0, data);
      assertThat(recordRead.asqn()).isEqualTo(i + 10);
      assertThat(data).containsExactly(entry);
    }
  }

  @Test
  public void shouldAppendAndReadMultipleRecords() {
    final var reader = journal.openReader();
    for (int i = 0; i < 10; i++) {
      // given
      entry = ("TestData" + i).getBytes();
      data.wrap(entry);

      // when
      final var recordAppended = journal.append(i + 10, data);
      assertThat(recordAppended.index()).isEqualTo(i + 1);

      // then
      assertThat(reader.hasNext()).isTrue();
      final var recordRead = reader.next();
      assertThat(recordRead.index()).isEqualTo(i + 1);
      final byte[] data = new byte[recordRead.data().capacity()];
      recordRead.data().getBytes(0, data);
      assertThat(recordRead.asqn()).isEqualTo(i + 10);
      assertThat(data).containsExactly(entry);
    }
  }

  @Test
  public void shouldReset() {
    // given
    long asqn = 1;
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(asqn++, data);
    journal.append(asqn++, data);

    // when
    journal.reset(2);

    // then
    assertThat(journal.getLastIndex()).isEqualTo(1);
    final var record = journal.append(asqn++, data);
    assertThat(record.index()).isEqualTo(2);
  }

  @Test
  public void shouldResetWhileReading() {
    // given
    final var reader = journal.openReader();
    long asqn = 1;
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(asqn++, data);
    journal.append(asqn++, data);
    final var record1 = reader.next();
    assertThat(record1.index()).isEqualTo(1);

    // when
    journal.reset(2);

    // then
    assertThat(journal.getLastIndex()).isEqualTo(1);
    final var record = journal.append(asqn++, data);
    assertThat(record.index()).isEqualTo(2);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var record2 = reader.next();
    assertThat(record2.index()).isEqualTo(2);
    assertThat(record2.asqn()).isEqualTo(record.asqn());
  }

  @Test
  public void shouldTruncate() {
    // given
    final var reader = journal.openReader();
    assertThat(journal.getLastIndex()).isEqualTo(0);
    journal.append(1, data);
    journal.append(2, data);
    journal.append(3, data);
    final var record1 = reader.next();
    assertThat(record1.index()).isEqualTo(1);

    // when
    journal.deleteAfter(2);

    // then - should write after the truncated index
    assertThat(journal.getLastIndex()).isEqualTo(2);
    final var record = journal.append(4, data);
    assertThat(record.index()).isEqualTo(3);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var record2 = reader.next();
    assertThat(record2.index()).isEqualTo(2);
    assertThat(record2.asqn()).isEqualTo(2);
    assertThat(reader.hasNext()).isTrue();

    // then - should not read truncated entry
    final var record3 = reader.next();
    assertThat(record3.index()).isEqualTo(3);
    assertThat(record3.asqn()).isEqualTo(4);
  }

  @Test
  public void shouldNotReadTruncatedEntries() {
    // given
    final int totalWrites = 10;
    final int truncateIndex = 5;
    int asqn = 1;
    final Map<Integer, JournalRecord> written = new HashMap<>();

    final var reader = journal.openReader();

    int writerIndex;
    for (writerIndex = 1; writerIndex <= totalWrites; writerIndex++) {
      final var record = journal.append(asqn++, data);
      assertThat(record.index()).isEqualTo(writerIndex);
      written.put(writerIndex, record);
    }

    int readerIndex;
    for (readerIndex = 1; readerIndex <= truncateIndex; readerIndex++) {
      assertThat(reader.hasNext()).isTrue();
      final var record = reader.next();
      assertThat(record.index()).isEqualTo(readerIndex);
      assertThat(record.asqn()).isEqualTo(written.get(readerIndex).asqn());
    }

    // when
    journal.deleteAfter(truncateIndex);

    for (writerIndex = truncateIndex + 1; writerIndex <= totalWrites; writerIndex++) {
      final var record = journal.append(asqn++, data);
      assertThat(record.index()).isEqualTo(writerIndex);
      written.put(writerIndex, record);
    }

    for (; readerIndex <= totalWrites; readerIndex++) {
      assertThat(reader.hasNext()).isTrue();
      final var record = reader.next();
      assertThat(record.index()).isEqualTo(readerIndex);
      assertThat(record.asqn()).isEqualTo(written.get(readerIndex).asqn());
    }
  }

  @Test
  public void shouldReadAfterCompact() {
    // given
    final var reader = journal.openReader();
    long asqn = 1;

    for (int i = 1; i <= entriesPerSegment * 5; i++) {
      assertThat(journal.append(asqn++, data).index()).isEqualTo(i);
    }
    assertThat(reader.hasNext()).isTrue();

    // when - compact up to the first index of segment 3
    final int indexToCompact = entriesPerSegment * 2 + 1;
    journal.deleteUntil(indexToCompact);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(indexToCompact);
  }

  @Test
  public void shouldSeekToIndex() {
    // given
    final var reader = journal.openReader();
    long asqn = 1;
    JournalRecord lastRecordWritten = null;
    for (int i = 1; i <= entriesPerSegment * 2; i++) {
      final JournalRecord record = journal.append(asqn++, data);
      assertThat(record.index()).isEqualTo(i);
      lastRecordWritten = record;
    }
    assertThat(reader.hasNext()).isTrue();

    // when - compact up to the first index of segment 3
    reader.seek(lastRecordWritten.index() - 1);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(lastRecordWritten.index() - 1);
  }

  @Test
  public void shouldSeekToAsqn() {
    // given
    final var reader = journal.openReader();
    long asqn = 10;
    JournalRecord lastRecordWritten = null;
    for (int i = 1; i <= entriesPerSegment * 2; i++) {
      final JournalRecord record = journal.append(asqn++, data);
      assertThat(record.index()).isEqualTo(i);
      lastRecordWritten = record;
    }
    assertThat(reader.hasNext()).isTrue();

    // when
    reader.seekToAsqn(lastRecordWritten.asqn() - 2);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().asqn()).isEqualTo(lastRecordWritten.asqn() - 2);
  }

  @Test
  public void shouldAppendJournalRecord() {
    // given
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .build();
    final var record = journal.append(10, data);

    // when
    receiverJournal.append(record);

    // then
    final var reader = receiverJournal.openReader();
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().asqn()).isEqualTo(10);
  }

  @Test
  public void shouldNotAppendRecordWithInvalidIndex() {
    // given
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .build();
    final var record = journal.append(1, data);
    receiverJournal.append(record);

    // when
    final var invalidIndexRecord = record;

    // then
    assertThatThrownBy(() -> receiverJournal.append(invalidIndexRecord))
        .isInstanceOf(InvalidIndex.class);
  }

  @Test
  public void shouldNotAppendRecordWithInvalidChecksum() {
    // given
    final var receiverJournal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data-2").toFile())
            .withJournalIndexDensity(5)
            .build();
    final var record = journal.append(1, data);

    // when
    final var invalidChecksumRecord =
        new PersistedJournalRecord(record.index(), record.asqn(), -1, record.data());

    // then
    assertThatThrownBy(() -> receiverJournal.append(invalidChecksumRecord))
        .isInstanceOf(InvalidChecksum.class);
  }

  @Test
  public void shouldDeleteIndexMappingsOnReset() {
    // given
    long asqn = 1;
    // append until there are two index mappings
    for (int i = 0; i < 2 * journalIndexDensity; i++) {
      journal.append(asqn++, data);
    }
    assertThat(journal.getJournalIndex().lookup(journalIndexDensity)).isNotNull();
    assertThat(journal.getJournalIndex().lookup(2 * journalIndexDensity)).isNotNull();

    // when
    journal.reset(journal.getLastIndex());

    // then
    assertThat(journal.getJournalIndex().lookup(journalIndexDensity)).isNull();
    assertThat(journal.getJournalIndex().lookup(2 * journalIndexDensity)).isNull();
  }

  @Test
  public void shouldUpdateIndexMappingsOnCompact() {
    // given
    long asqn = 1;
    for (int i = 0; i < 3 * entriesPerSegment; i++) {
      journal.append(asqn++, data);
    }
    assertThat(journal.getJournalIndex().lookup(entriesPerSegment)).isNotNull();

    // when - delete first segment
    journal.deleteUntil(entriesPerSegment + 1);

    // then
    assertThat(journal.getJournalIndex().lookup(entriesPerSegment)).isNull();
    assertThat(journal.getJournalIndex().lookup(3 * entriesPerSegment)).isNotNull();
  }

  @Test
  public void shouldUpdateIndexMappingsOnTruncate() {
    // given
    long asqn = 1;
    for (int i = 0; i < 2 * journalIndexDensity; i++) {
      journal.append(asqn++, data);
    }
    assertThat(journal.getJournalIndex().lookup(journalIndexDensity)).isNotNull();
    assertThat(journal.getJournalIndex().lookup(2 * journalIndexDensity).index())
        .isEqualTo(2 * journalIndexDensity);

    // when
    journal.deleteAfter(journalIndexDensity);

    // then
    assertThat(journal.getJournalIndex().lookup(journalIndexDensity)).isNotNull();
    assertThat(journal.getJournalIndex().lookup(2 * journalIndexDensity).index())
        .isEqualTo(journalIndexDensity);
  }
}
