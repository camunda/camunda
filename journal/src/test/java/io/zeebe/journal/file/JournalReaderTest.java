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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class JournalReaderTest {

  private static final int ENTRIES = 4;
  @TempDir Path directory;
  private final Namespace namespace =
      new Namespace.Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(PersistedJournalRecord.class)
          .register(UnsafeBuffer.class)
          .name("Journal")
          .build();
  private final DirectBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final int entrySize =
      namespace.serialize(new PersistedJournalRecord(1, 1, Integer.MAX_VALUE, data)).length
          + Integer.BYTES;
  private JournalReader reader;
  private SegmentedJournal journal;

  @BeforeEach
  public void setup() {
    journal =
        SegmentedJournal.builder()
            .withDirectory(directory.resolve("data").toFile())
            .withMaxSegmentSize(entrySize * ENTRIES / 2 + JournalSegmentDescriptor.BYTES)
            .withMaxEntrySize(entrySize)
            .withJournalIndexDensity(5)
            .build();
    reader = journal.openReader();
  }

  @Test
  public void shouldSeek() {
    // when
    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, data).index();
    }
    reader.seek(2);

    // then
    for (int i = 0; i < 3; i++) {
      final JournalRecord record = reader.next();
      assertThat(record.asqn()).isEqualTo(2 + i);
      assertThat(record.index()).isEqualTo(2 + i);
      assertThat(record.data()).isEqualTo(data);
    }
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToFirst() {
    // when
    long firstIndex = -1;
    for (int i = 1; i <= ENTRIES; i++) {
      final long index = journal.append(i, data).index();
      if (firstIndex == -1) {
        firstIndex = index;
      }
    }
    reader.next(); // move reader before seekToFirst
    reader.next();
    reader.seekToFirst();

    // then
    final JournalRecord record = reader.next();
    assertThat(record.index()).isEqualTo(firstIndex);
    assertThat(record.asqn()).isEqualTo(1);
  }

  @Test
  public void shouldSeekToLast() {
    // when
    long lastIndex = -1;
    for (int i = 1; i <= ENTRIES; i++) {
      lastIndex = journal.append(i, data).index();
    }
    reader.seekToLast();

    // then
    final JournalRecord record = reader.next();
    assertThat(record.index()).isEqualTo(lastIndex);
    assertThat(record.asqn()).isEqualTo(4);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldNotReadIfSeekIsHigherThanLast() {
    // when
    for (int i = 1; i <= ENTRIES; i++) {
      journal.append(i, data).index();
    }
    reader.seek(99L);

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldReadAppendedDataAfterSeek() {
    // when
    for (int i = 0; i < ENTRIES; i++) {
      journal.append(data).index();
    }

    reader.seek(99L);
    assertThat(reader.hasNext()).isFalse();
    journal.append(data);

    // then
    assertThat(reader.hasNext()).isTrue();
  }

  @Test
  public void shouldSeekToAsqn() {
    // given
    long asqn = 10;
    JournalRecord lastRecordWritten = null;
    for (int i = 1; i <= ENTRIES; i++) {
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
  public void shouldSeekToHighestAsqnLowerThanProvidedAsqn() {
    // given
    final var expectedRecord = journal.append(1, data);
    journal.append(5, data);

    // when
    reader.seekToAsqn(4);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var record = reader.next();
    assertThat(record.index()).isEqualTo(expectedRecord.index());
    assertThat(record.asqn()).isEqualTo(expectedRecord.asqn());
    assertThat(record.data()).isEqualTo(expectedRecord.data());
    assertThat(record.checksum()).isEqualTo(expectedRecord.checksum());
  }

  @Test
  @Disabled("https://github.com/zeebe-io/zeebe/issues/6358")
  public void shouldSeekToHighestLowerAsqnEvenIfRecordHasNone() {
    // given
    final var expectedRecord = journal.append(1, data);
    journal.append(data);
    journal.append(5, data);

    // when
    reader.seekToAsqn(3);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var record = reader.next();
    assertThat(record.index()).isEqualTo(expectedRecord.index());
    assertThat(record.asqn()).isEqualTo(expectedRecord.asqn());
    assertThat(record.data()).isEqualTo(expectedRecord.data());
    assertThat(record.checksum()).isEqualTo(expectedRecord.checksum());
  }

  @Test
  public void shouldSeekToNonExistentAsqn() {
    // given
    final var expectedRecord = journal.append(data);
    journal.append(5, data);

    // when
    reader.seekToAsqn(1);

    // then
    assertThat(reader.hasNext()).isTrue();
    final var record = reader.next();
    assertThat(record.index()).isEqualTo(expectedRecord.index());
    assertThat(record.asqn()).isEqualTo(expectedRecord.asqn());
    assertThat(record.data()).isEqualTo(expectedRecord.data());
    assertThat(record.checksum()).isEqualTo(expectedRecord.checksum());

    assertThat(reader.next().asqn()).isEqualTo(5);
  }

  @Test
  public void shouldSeekToFirstIfLowerThanFirst() {
    // when
    long firstIndex = -1;
    for (int i = 1; i <= ENTRIES; i++) {
      final long index = journal.append(i, data).index();

      if (firstIndex == -1) {
        firstIndex = index;
      }
    }
    reader.seek(-1);

    // then
    assertThat(reader.hasNext()).isTrue();
    final JournalRecord record = reader.next();
    assertThat(record.asqn()).isEqualTo(1);
    assertThat(record.index()).isEqualTo(firstIndex);
    assertThat(record.data()).isEqualTo(data);
  }

  @Test
  public void shouldSeekAfterTruncate() {
    // when
    long lastIndex = -1;
    for (int i = 1; i <= ENTRIES; i++) {
      lastIndex = journal.append(i, data).index();
    }
    journal.deleteAfter(lastIndex - 2);
    reader.seek(lastIndex - 2);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(lastIndex - 2);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekAfterCompact() {
    // when
    journal.append(1, data).index();
    journal.append(2, data).index();
    journal.append(3, data).index();
    journal.deleteUntil(3);
    reader.seek(1);

    // then
    final JournalRecord next = reader.next();
    assertThat(next.index()).isEqualTo(3);
    assertThat(next.asqn()).isEqualTo(3);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  public void shouldSeekToIndex() {
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
    reader.seek(lastRecordWritten.index() - 1);

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(lastRecordWritten.index() - 1);
  }
}
