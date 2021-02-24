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

import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import io.zeebe.journal.JournalReader;
import io.zeebe.journal.JournalRecord;
import io.zeebe.journal.file.record.PersistedJournalRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SegmentedJournalTest {

  @TempDir Path directory;
  private final Namespace namespace =
      new Namespace.Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(PersistedJournalRecord.class)
          .register(UnsafeBuffer.class)
          .name("Journal")
          .build();
  private final int journalIndexDensity = 5;
  private final DirectBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final int entrySize =
      namespace.serialize(new PersistedJournalRecord(1, 1, Integer.MAX_VALUE, data)).length
          + Integer.BYTES;

  @Test
  public void shouldDeleteIndexMappingsOnReset() {
    // given
    final SegmentedJournal journal = openJournal(10);

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
    final int entriesPerSegment = 10;
    long asqn = 1;
    final SegmentedJournal journal = openJournal(entriesPerSegment);
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
    final int entriesPerSegment = 10;
    long asqn = 1;
    final SegmentedJournal journal = openJournal(entriesPerSegment);
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

  @Test
  public void shouldCreateNewSegmentIfEntryExceedsBuffer() {
    // given
    final int asqn = 1;
    // one entry fits but not two
    final SegmentedJournal journal = openJournal(1.5f);

    final JournalReader reader = journal.openReader();

    // when
    for (int i = 0; i < 2; i++) {
      journal.append(asqn + i, data);
    }

    // then
    assertThat(journal.getFirstSegment()).isNotEqualTo(journal.getLastSegment());

    for (int i = 0; i < 2; i++) {
      assertThat(reader.hasNext()).isTrue();
      final JournalRecord entry = reader.next();
      assertThat(entry.asqn()).isEqualTo(asqn + i);
      assertThat(entry.data()).isEqualTo(data);
    }
  }

  @Test
  public void shouldNotTruncateIfIndexIsHigherThanLast() {
    // given
    final int asqn = 1;
    final SegmentedJournal journal = openJournal(1);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < 2; i++) {
      lastIndex = journal.append(asqn + i, data).index();
    }
    journal.deleteAfter(lastIndex);

    // then
    for (int i = 0; i < 2; i++) {
      assertThat(reader.hasNext()).isTrue();
      final JournalRecord entry = reader.next();
      assertThat(entry.asqn()).isEqualTo(asqn + i);
      assertThat(entry.data()).isEqualTo(data);
    }
  }

  @Test
  public void shouldNotCompactIfIndexIsLowerThanFirst() {
    // given
    final int asqn = 1;
    final SegmentedJournal journal = openJournal(1.5f);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(asqn, data);
    final var secondRecord = journal.append(asqn + 1, data);
    journal.deleteUntil(firstRecord.index());

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(secondRecord);
  }

  @Test
  public void shouldTruncateNextEntry() {
    // given
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(1, data);
    journal.append(2, data).index();
    journal.append(3, data).index();

    assertThat(reader.next()).isEqualTo(firstRecord);
    journal.deleteAfter(firstRecord.index());

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldTruncateReadEntry() {
    // given
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final long first = journal.append(1, data).index();
    journal.append(2, data).index();

    assertThat(reader.hasNext()).isTrue();
    journal.deleteAfter(first - 1);

    // then
    assertThat(reader.hasNext()).isFalse();
    assertThat(journal.getLastIndex()).isEqualTo(0);
  }

  @Test
  public void shouldTruncateNextSegment() {
    // given
    final SegmentedJournal journal = openJournal(1);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(1, data);
    journal.append(2, data);
    journal.deleteAfter(firstRecord.index());

    // then
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.hasNext()).isFalse();
    assertThat(journal.getLastIndex()).isEqualTo(firstRecord.index());
  }

  @Test
  public void shouldReadSegmentStartAfterMidSegmentTruncate() {
    final int entryPerSegment = 2;
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, data).index();
    }
    journal.deleteAfter(lastIndex - 1);

    // then
    assertThat(reader.seek(lastIndex - 1)).isEqualTo(lastIndex - 1);
    assertThat(reader.next().index()).isEqualTo(lastIndex - 1);
    assertThat(journal.getLastIndex()).isEqualTo(lastIndex - 1);
  }

  @Test
  public void shouldCompactUpToStartOfSegment() {
    final int entryPerSegment = 2;
    final SegmentedJournal journal = openJournal(entryPerSegment);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, data).index();
    }
    assertThat(reader.hasNext()).isTrue();
    journal.deleteUntil(lastIndex);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(lastIndex - 1);
    assertThat(reader.next().index()).isEqualTo(lastIndex - 1);
  }

  @Test
  public void shouldReturnCorrectFirstIndexAfterCompaction() {
    final int entryPerSegment = 2;
    final SegmentedJournal journal = openJournal(2);

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, data).index();
    }
    journal.deleteUntil(lastIndex);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(lastIndex - 1);
  }

  @Test
  public void shouldWriteAndReadAfterTruncate() {
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final long first = journal.append(1, data).index();
    journal.append(2, data);
    journal.deleteAfter(first - 1);
    data.wrap("new".getBytes());
    final var lastRecord = journal.append(3, data);

    // then
    assertThat(first).isEqualTo(lastRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(lastRecord);
  }

  @Test
  public void shouldAppendEntriesOfDifferentSizesOverSegmentSize() {
    // given
    data.wrap("1234567890".getBytes(StandardCharsets.UTF_8));
    final int entrySize =
        namespace.serialize(new PersistedJournalRecord(1, 1, Integer.MAX_VALUE, data)).length
            + Integer.BYTES;
    final SegmentedJournal journal = openJournal(1, entrySize);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(new UnsafeBuffer("12345".getBytes()));
    final var secondRecord = journal.append(new UnsafeBuffer("1234567".getBytes()));
    final var thirdRecord = journal.append(new UnsafeBuffer("1234567890".getBytes()));

    // then
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.next()).isEqualTo(secondRecord);
    assertThat(reader.next()).isEqualTo(thirdRecord);
    assertThat(reader.hasNext()).isFalse();
  }

  private SegmentedJournal openJournal(final float entriesPerSegment) {
    return openJournal(entriesPerSegment, entrySize);
  }

  private SegmentedJournal openJournal(final float entriesPerSegment, final int entrySize) {
    return SegmentedJournal.builder()
        .withDirectory(directory.resolve("data").toFile())
        .withMaxSegmentSize((int) (entrySize * entriesPerSegment) + JournalSegmentDescriptor.BYTES)
        .withMaxEntrySize(entrySize)
        .withJournalIndexDensity(journalIndexDensity)
        .build();
  }
}
