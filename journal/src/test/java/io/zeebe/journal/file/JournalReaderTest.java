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

import static io.zeebe.journal.file.SegmentedJournal.ASQN_IGNORE;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.journal.JournalReader;
import io.zeebe.journal.JournalRecord;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalReaderTest {

  private static final int ENTRIES = 4;

  private final DirectBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private JournalReader reader;
  private SegmentedJournal journal;

  @BeforeEach
  public void setup(final @TempDir Path tempDir) {
    final File directory = tempDir.resolve("data").toFile();

    journal =
        SegmentedJournal.builder().withDirectory(directory).withJournalIndexDensity(5).build();
    reader = journal.openReader();
  }

  @Test
  void shouldSeek() {
    // given
    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, data).index();
    }

    // when
    final long nextIndex = reader.seek(2);

    // then
    for (int i = 0; i < 3; i++) {
      final JournalRecord record = reader.next();
      assertThat(record.asqn()).isEqualTo(nextIndex + i);
      assertThat(record.index()).isEqualTo(nextIndex + i);
      assertThat(record.data()).isEqualTo(data);
    }
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldSeekToFirst() {
    // given
    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, data).index();
    }
    reader.next(); // move reader before seekToFirst
    reader.next();

    // when
    final long nextIndex = reader.seekToFirst();

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    final JournalRecord record = reader.next();
    assertThat(record.index()).isEqualTo(nextIndex);
    assertThat(record.asqn()).isEqualTo(1);
  }

  @Test
  void shouldSeekToLast() {
    // given
    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, data).index();
    }

    // when
    final long nextIndex = reader.seekToLast();

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex());
    final JournalRecord record = reader.next();
    assertThat(record.index()).isEqualTo(nextIndex);
    assertThat(record.asqn()).isEqualTo(4);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldNotReadIfSeekIsHigherThanLast() {
    // given
    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, data).index();
    }

    // when
    final long nextIndex = reader.seek(99L);

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex() + 1);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldReadAppendedDataAfterSeek() {
    // given
    for (int i = 0; i < ENTRIES; i++) {
      journal.append(data).index();
    }

    // when
    final long nextIndex = reader.seek(99L);
    assertThat(reader.hasNext()).isFalse();
    journal.append(data);

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex());
    assertThat(reader.hasNext()).isTrue();
  }

  @Test
  void shouldSeekToAsqn() {
    // given that all records with index i will have an asqn of startAsqn + i
    final long startAsqn = 10;
    for (int i = 0; i < ENTRIES; i++) {
      assertThat(journal.append(startAsqn + i, data)).isNotNull();
    }
    assertThat(reader.hasNext()).isTrue();

    // when
    final long nextIndex = reader.seekToAsqn(startAsqn + 2);

    // then
    assertThat(nextIndex).isEqualTo(3);
    assertThat(reader.hasNext()).isTrue();

    final JournalRecord next = reader.next();
    assertThat(next.index()).isEqualTo(nextIndex);
    assertThat(next.asqn()).isEqualTo(startAsqn + 2);
  }

  @Test
  void shouldSeekToHighestAsqnLowerThanProvidedAsqn() {
    // given
    final var expectedRecord = journal.append(1, data);
    journal.append(5, data);

    // when
    final long nextIndex = reader.seekToAsqn(4);

    // then
    assertThat(nextIndex).isEqualTo(expectedRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(expectedRecord);
  }

  @Test
  void shouldSeekToHighestLowerAsqnSkippingRecordsWithNoAsqn() {
    // given
    final var expectedRecord = journal.append(1, data);
    journal.append(data);
    journal.append(5, data);

    // when
    final long nextIndex = reader.seekToAsqn(3);

    // then
    assertThat(nextIndex).isEqualTo(expectedRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(expectedRecord);
  }

  @Test
  void shouldSeekToFirstWhenAllAsqnIsHigher() {
    // given
    final var expectedRecord = journal.append(data);
    journal.append(5, data);

    // when
    final long nextIndex = reader.seekToAsqn(1);

    // then
    assertThat(nextIndex).isEqualTo(expectedRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(expectedRecord);
    assertThat(reader.next().asqn()).isEqualTo(5);
  }

  @Test
  void shouldSeekToFirstIfLowerThanFirst() {
    // given
    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, data).index();
    }

    // when
    final long nextIndex = reader.seek(-1);

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    assertThat(reader.hasNext()).isTrue();
    final JournalRecord record = reader.next();
    assertThat(record.asqn()).isEqualTo(1);
    assertThat(record.index()).isEqualTo(nextIndex);
    assertThat(record.data()).isEqualTo(data);
  }

  @Test
  void shouldSeekAfterTruncate() {
    // given
    long lastIndex = -1;
    for (int i = 1; i <= ENTRIES; i++) {
      lastIndex = journal.append(i, data).index();
    }

    // when
    journal.deleteAfter(lastIndex - 2);
    final long nextIndex = reader.seek(lastIndex - 2);

    // then
    assertThat(nextIndex).isEqualTo(lastIndex - 2);
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(nextIndex);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldSeekAfterCompact() {
    // given
    journal.append(1, data).index();
    journal.append(2, data).index();
    journal.append(3, data).index();

    // when
    journal.deleteUntil(3);
    final long nextIndex = reader.seek(3);

    // then
    assertThat(nextIndex).isEqualTo(3);
    final JournalRecord next = reader.next();
    assertThat(next.index()).isEqualTo(nextIndex);
    assertThat(next.asqn()).isEqualTo(3);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldSeekToIndex() {
    // given
    long asqn = 1;
    JournalRecord lastRecordWritten = null;
    for (int i = 1; i <= ENTRIES; i++) {
      final JournalRecord record = journal.append(asqn++, data);
      assertThat(record.index()).isEqualTo(i);
      lastRecordWritten = record;
    }
    assertThat(reader.hasNext()).isTrue();

    // when - compact up to the first index of segment 3
    final long nextIndex = reader.seek(lastRecordWritten.index() - 1);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(nextIndex);
  }

  @Test
  void shouldSeekToFirstWithEmptyJournal() {
    // when
    final long nextIndex = reader.seekToFirst();

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldSeekToLastWithEmptyJournal() {
    // when
    final long nextIndex = reader.seekToLast();

    // then
    assertThat(nextIndex).isEqualTo(journal.getLastIndex());
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldSeekToFirstWhenNoRecordsWithValidAsqnExists() {
    // given
    for (int i = 0; i < ENTRIES; i++) {
      journal.append(data);
    }

    // when
    final long nextIndex = reader.seekToAsqn(32);

    // then
    assertThat(nextIndex).isEqualTo(journal.getFirstIndex());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().asqn()).isEqualTo(ASQN_IGNORE);
  }
}
