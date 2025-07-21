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
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentedJournalReaderTest {

  private static final int ENTRIES_PER_SEGMENT = 4;

  @TempDir Path directory;

  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private JournalReader reader;
  private SegmentedJournal journal;

  @BeforeEach
  void setup() {
    final int entrySize = FrameUtil.getLength() + getSerializedSize(JournalTestHelper.DATA);

    journal =
        SegmentedJournal.builder(meterRegistry)
            .withDirectory(directory.resolve("data").toFile())
            .withMaxSegmentSize(
                entrySize * ENTRIES_PER_SEGMENT
                    + SegmentDescriptorSerializer.currentEncodingLength())
            .withJournalIndexDensity(ENTRIES_PER_SEGMENT / 2)
            .withMetaStore(new MockJournalMetastore())
            .build();
    reader = journal.openReader();
  }

  @AfterEach
  void afterEach() {
    CloseHelper.quietCloseAll(reader, journal);
  }

  @Test
  void shouldReadAfterCompact() {
    // given
    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 5; i++) {
      assertThat(journal.append(i, JournalTestHelper.RECORD_DATA_WRITER).index()).isEqualTo(i);
    }
    assertThat(reader.hasNext()).isTrue();

    // when - compact up to the first index of segment 3
    final int indexToCompact = ENTRIES_PER_SEGMENT * 2 + 1;
    journal.deleteUntil(indexToCompact);
    reader.seekToFirst();

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next().index()).isEqualTo(indexToCompact);
  }

  @Test
  void shouldSeekToAnyIndexInMultipleSegments() {
    // given
    long asqn = 1;

    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 2; i++) {
      journal.append(asqn++, JournalTestHelper.RECORD_DATA_WRITER).index();
    }

    for (int i = 1; i < ENTRIES_PER_SEGMENT * 2; i++) {
      // when
      reader.seek(i);

      // then
      assertThat(reader.hasNext()).isTrue();
      assertThat(reader.next().index()).isEqualTo(i);
    }
  }

  @Test
  void shouldSeekToAnyAsqnInMultipleSegments() {
    // given

    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 2; i++) {
      journal.append(i, JournalTestHelper.RECORD_DATA_WRITER).index();
    }

    for (int i = 1; i <= ENTRIES_PER_SEGMENT * 2; i++) {
      // when
      reader.seekToAsqn(i);

      // then
      assertThat(reader.hasNext()).isTrue();
      assertThat(reader.next().asqn()).isEqualTo(i);
    }
  }

  @Test
  void shouldNotReadWhenAccessingDeletedSegment() {
    // given
    journal.append(JournalTestHelper.RECORD_DATA_WRITER);
    final var reader = journal.openReader();

    // when
    journal.reset(100);

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldReadAfterReset() {
    // given
    journal.append(JournalTestHelper.RECORD_DATA_WRITER);
    final var reader = journal.openReader();
    final int resetIndex = 100;
    journal.reset(resetIndex);
    journal.append(JournalTestHelper.RECORD_DATA_WRITER);

    // when
    reader.seekToFirst();

    // then
    assertThat(reader.next().index()).isEqualTo(resetIndex);
  }

  @Test
  void shouldBuiltIndexOnDemandWhileSeek() {
    // given
    for (int i = 1; i <= ENTRIES_PER_SEGMENT; i++) {
      assertThat(journal.append(i, JournalTestHelper.RECORD_DATA_WRITER).index()).isEqualTo(i);
    }

    // simulate restart with no index
    journal.getJournalIndex().clear();
    assertThat(journal.getJournalIndex().lookup(journal.getLastIndex())).isNull();

    // when
    reader.seekToLast();

    // then
    assertThat(journal.getJournalIndex().lookup(journal.getLastIndex()))
        .describedAs("Index must be built during seek")
        .isNotNull();
  }

  @Test
  void shouldSkipOverlappingEntries() {
    // given an overlapping journal
    final var journalFactory = new TestJournalFactory(3);
    journal = JournalTestHelper.openJournal(journalFactory, directory);
    final var secondaryJournal =
        JournalTestHelper.openJournal(journalFactory, directory.resolve("secondary"));

    JournalTestHelper.appendJournalEntries(journal, 1, 2, 3);
    journal.getLastSegment().updateDescriptor();

    secondaryJournal.reset(3);
    JournalTestHelper.appendJournalEntries(secondaryJournal, 3, 4, 5);
    secondaryJournal.getLastSegment().updateDescriptor();
    journal.close();
    secondaryJournal.close();

    JournalTestHelper.mergeJournals(directory, directory.resolve("secondary"));

    // when opening the journal
    journal = JournalTestHelper.openJournal(journalFactory, directory);

    reader = journal.openReader();
    reader.seekToFirst();

    // then the reader should skip the overlapping entries
    JournalRecord record = null;
    JournalRecord previousRecord = null;
    while (reader.hasNext()) {
      previousRecord = record;
      record = reader.next();
      if (previousRecord != null) {
        assertThat(record.index()).isGreaterThan(previousRecord.index());
      }
    }
    assertThat(record.asqn()).isEqualTo(5);
  }

  @Test
  void shouldSkipMultipleSegments() {
    // given an overlapping journal with multiple segments
    var journalFactory = new TestJournalFactory(3);
    journal = JournalTestHelper.openJournal(journalFactory, directory);

    JournalTestHelper.appendJournalEntries(journal, 1, 2);
    journal.getLastSegment().updateDescriptor();
    journal.close();

    final var secondaryJournal =
        JournalTestHelper.openJournal(journalFactory, directory.resolve("secondary"));
    secondaryJournal.reset(2);
    JournalTestHelper.appendJournalEntries(secondaryJournal, 2, 3, 4);
    secondaryJournal.getLastSegment().updateDescriptor();
    secondaryJournal.close();

    journalFactory = new TestJournalFactory(5);
    final var thirdJournal =
        JournalTestHelper.openJournal(journalFactory, directory.resolve("third"));
    thirdJournal.reset(3);
    JournalTestHelper.appendJournalEntries(thirdJournal, 3, 4, 5, 6, 7);
    thirdJournal.getLastSegment().updateDescriptor();
    thirdJournal.close();

    JournalTestHelper.mergeJournals(
        directory, directory.resolve("secondary"), directory.resolve("third"));

    // when opening the journal
    journalFactory = new TestJournalFactory(3);
    journal = JournalTestHelper.openJournal(journalFactory, directory);
    reader = journal.openReader();
    reader.seekToFirst();

    // then the reader should skip the overlapping entries across multiple segments
    JournalRecord record = null;
    JournalRecord previousRecord = null;
    while (reader.hasNext()) {
      previousRecord = record;
      record = reader.next();
      if (previousRecord != null) {
        assertThat(record.index()).isGreaterThan(previousRecord.index());
      }
      assertThat(record.asqn()).isLessThanOrEqualTo(7);
    }
    assertThat(record.asqn()).isEqualTo(7);
  }

  @Test
  void shouldSeekToOverlappingEntriesInBothDirections() {
    // given an overlapping journal with multiple segments
    var journalFactory = new TestJournalFactory(3);
    journal = JournalTestHelper.openJournal(journalFactory, directory);

    JournalTestHelper.appendJournalEntries(journal, 1, 2);
    journal.getLastSegment().updateDescriptor();
    journal.close();

    final var secondaryJournal =
        JournalTestHelper.openJournal(journalFactory, directory.resolve("secondary"));
    secondaryJournal.reset(2);
    JournalTestHelper.appendJournalEntries(secondaryJournal, 2, 3, 4);
    secondaryJournal.getLastSegment().updateDescriptor();
    secondaryJournal.close();

    journalFactory = new TestJournalFactory(5);
    final var thirdJournal =
        JournalTestHelper.openJournal(journalFactory, directory.resolve("third"));
    thirdJournal.reset(3);
    JournalTestHelper.appendJournalEntries(thirdJournal, 3, 4, 5, 6, 7);
    thirdJournal.getLastSegment().updateDescriptor();
    thirdJournal.close();

    JournalTestHelper.mergeJournals(
        directory, directory.resolve("secondary"), directory.resolve("third"));

    // when opening the journal
    journalFactory = new TestJournalFactory(3);
    journal = JournalTestHelper.openJournal(journalFactory, directory);
    reader = journal.openReader();
    reader.seekToFirst();

    // then the reader should be able to seek through all etries in both directions
    for (int i = 1; i <= 7; i++) {
      reader.seek(i);
      assertThat(reader.hasNext()).isTrue();
      final var record = reader.next();
      assertThat(record.asqn()).isEqualTo(i);
    }

    // reader.seekToFirst();
    for (int i = 7; i >= 1; i--) {
      reader.seek(i);
      assertThat(reader.hasNext()).isTrue();
      final var record = reader.next();
      assertThat(record.asqn()).isEqualTo(i);
    }
  }

  private int getSerializedSize(final DirectBuffer data) {
    final var record = new RecordData(Long.MAX_VALUE, Long.MAX_VALUE, data);
    final var serializer = new SBESerializer();
    final ByteBuffer buffer = ByteBuffer.allocate(128);
    return serializer.writeData(record, new UnsafeBuffer(buffer), 0).get()
        + serializer.getMetadataLength();
  }
}
