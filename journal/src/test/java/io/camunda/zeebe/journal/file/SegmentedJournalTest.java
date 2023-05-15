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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.PersistedJournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.util.PosixPathAssert;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("resource")
class SegmentedJournalTest {
  private static final String JOURNAL_NAME = "journal";

  private TestJournalFactory journalFactory;
  private @TempDir Path directory;
  private SegmentedJournal journal;

  @AfterEach
  void tearDown() {
    CloseHelper.quietClose(journal);
  }

  @Test
  void shouldDeleteIndexMappingsOnReset() {
    // given
    journal = openJournal(10);
    // append until there are two index mappings
    journal.append(1, journalFactory.entryData());
    journal.append(2, journalFactory.entryData());
    assertThat(journal.getJournalIndex().lookup(1)).isNotNull();
    assertThat(journal.getJournalIndex().lookup(2)).isNotNull();

    // when
    journal.reset(journal.getLastIndex());

    // then
    assertThat(journal.getJournalIndex().lookup(1)).isNull();
    assertThat(journal.getJournalIndex().lookup(2)).isNull();
  }

  @Test
  void shouldUpdateIndexMappingsOnCompact() {
    // given
    final int entriesPerSegment = 10;
    journal = openJournal(entriesPerSegment);
    for (int i = 0; i < 3 * entriesPerSegment; i++) {
      journal.append(i + 1, journalFactory.entryData());
    }
    assertThat(journal.getJournalIndex().lookup(entriesPerSegment)).isNotNull();

    // when - delete first segment
    journal.deleteUntil(entriesPerSegment + 1);

    // then
    final IndexInfo lookup = journal.getJournalIndex().lookup(entriesPerSegment - 1);
    assertThat(lookup).isNull();
    assertThat(journal.getJournalIndex().lookup(3 * entriesPerSegment)).isNotNull();
  }

  @Test
  void shouldUpdateIndexMappingsOnTruncate() {
    // given
    final int entriesPerSegment = 10;
    journal = openJournal(entriesPerSegment);
    journal.append(1, journalFactory.entryData());
    journal.append(2, journalFactory.entryData());

    assertThat(journal.getJournalIndex().lookup(1)).isNotNull();
    assertThat(journal.getJournalIndex().lookup(2).index()).isEqualTo(2);

    // when
    journal.deleteAfter(1);

    // then
    assertThat(journal.getJournalIndex().lookup(1)).isNotNull();
    assertThat(journal.getJournalIndex().lookup(2).index()).isOne();
  }

  @Test
  void shouldCreateNewSegmentIfEntryExceedsBuffer() {
    // given
    final int asqn = 1;
    // one entry fits but not two
    journal = openJournal(1);

    final JournalReader reader = journal.openReader();

    // when
    for (int i = 0; i < 2; i++) {
      journal.append(asqn + i, journalFactory.entryData());
    }

    // then
    assertThat(journal.getFirstSegment()).isNotEqualTo(journal.getLastSegment());

    for (int i = 0; i < 2; i++) {
      assertThat(reader.hasNext()).isTrue();
      final JournalRecord entry = reader.next();
      assertThat(entry.asqn()).isEqualTo(asqn + i);
      assertThat(entry.data()).isEqualTo(journalFactory.entryData());
    }
  }

  @Test
  void shouldNotTruncateIfIndexIsHigherThanLast() {
    // given
    final int asqn = 1;
    final SegmentedJournal journal = openJournal(1);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < 2; i++) {
      lastIndex = journal.append(asqn + i, journalFactory.entryData()).index();
    }
    journal.deleteAfter(lastIndex);

    // then
    for (int i = 0; i < 2; i++) {
      assertThat(reader.hasNext()).isTrue();
      final JournalRecord entry = reader.next();
      assertThat(entry.asqn()).isEqualTo(asqn + i);
      assertThat(entry.data()).isEqualTo(journalFactory.entryData());
    }
  }

  @Test
  void shouldNotCompactIfIndexIsLowerThanFirst() {
    // given
    final int asqn = 1;
    journal = openJournal(1);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(asqn, journalFactory.entryData());
    final var secondRecord = journal.append(asqn + 1, journalFactory.entryData());
    journal.deleteUntil(firstRecord.index());

    // then
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(secondRecord);
  }

  @Test
  void shouldTruncateNextEntry() {
    // given
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(1, journalFactory.entryData());
    journal.append(2, journalFactory.entryData()).index();
    journal.append(3, journalFactory.entryData()).index();

    assertThat(reader.next()).isEqualTo(firstRecord);
    journal.deleteAfter(firstRecord.index());

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldTruncateReadEntry() {
    // given
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final long first = journal.append(1, journalFactory.entryData()).index();
    journal.append(2, journalFactory.entryData()).index();

    assertThat(reader.hasNext()).isTrue();
    journal.deleteAfter(first - 1);

    // then
    assertThat(reader.hasNext()).isFalse();
    assertThat(journal.getLastIndex()).isZero();
  }

  @Test
  void shouldTruncateNextSegment() {
    // given
    final SegmentedJournal journal = openJournal(1);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(1, journalFactory.entryData());
    journal.append(2, journalFactory.entryData());
    journal.deleteAfter(firstRecord.index());

    // then
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.hasNext()).isFalse();
    assertThat(journal.getLastIndex()).isEqualTo(firstRecord.index());
  }

  @Test
  void shouldReadSegmentStartAfterMidSegmentTruncate() {
    final int entryPerSegment = 2;
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entryData()).index();
    }
    journal.deleteAfter(lastIndex - 1);

    // then
    assertThat(reader.seek(lastIndex - 1)).isEqualTo(lastIndex - 1);
    assertThat(reader.next().index()).isEqualTo(lastIndex - 1);
    assertThat(journal.getLastIndex()).isEqualTo(lastIndex - 1);
  }

  @Test
  void shouldCompactUpToStartOfSegment() {
    final int entryPerSegment = 2;
    final SegmentedJournal journal = openJournal(entryPerSegment);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entryData()).index();
    }
    assertThat(reader.hasNext()).isTrue();
    journal.deleteUntil(lastIndex);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(lastIndex - 1);
    reader.seekToFirst();
    assertThat(reader.next().index()).isEqualTo(lastIndex - 1);
  }

  @Test
  void shouldNotCompactTheLastSegmentWhenNonExistingHigherIndex() {
    final int entryPerSegment = 2;
    final SegmentedJournal journal = openJournal(entryPerSegment);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entryData()).index();
    }
    assertThat(reader.hasNext()).isTrue();
    journal.deleteUntil(lastIndex + 1);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(lastIndex - 1);
    reader.seekToFirst();
    assertThat(reader.next().index()).isEqualTo(lastIndex - 1);
  }

  @Test
  void shouldReturnCorrectFirstIndexAfterCompaction() {
    final int entryPerSegment = 2;
    final SegmentedJournal journal = openJournal(2);

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entryData()).index();
    }
    journal.deleteUntil(lastIndex);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(lastIndex - 1);
  }

  @Test
  void shouldWriteAndReadAfterTruncate() {
    final SegmentedJournal journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final long first = journal.append(1, journalFactory.entryData()).index();
    journal.append(2, journalFactory.entryData());
    journal.deleteAfter(first - 1);
    final var lastRecord = journal.append(3, journalFactory.entryData());

    // then
    assertThat(first).isEqualTo(lastRecord.index());
    assertThat(reader.hasNext()).isTrue();
    assertThat(reader.next()).isEqualTo(lastRecord);
  }

  @Test
  void shouldAppendEntriesOfDifferentSizesOverSegmentSize() {
    // given
    final SegmentedJournal journal = openJournal("1234567890", 1);
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

  @Test
  void shouldUpdateIndexMappingsAfterRestart() {
    // given
    final int entriesPerSegment = 10;
    journal = openJournal(entriesPerSegment);
    journal.append(1, journalFactory.entryData());
    journal.append(2, journalFactory.entryData());
    final var indexBeforeClose = journal.getJournalIndex();

    // when
    journal.close();
    journal = openJournal(entriesPerSegment);

    // then
    final var firstIndexedPosition = 1;
    final var secondIndexedPosition = 2;
    final JournalIndex indexAfterRestart = journal.getJournalIndex();

    assertThat(indexAfterRestart.lookup(firstIndexedPosition).index()).isOne();
    assertThat(indexAfterRestart.lookup(secondIndexedPosition).index())
        .isEqualTo(secondIndexedPosition);
    assertThat(indexAfterRestart.lookup(firstIndexedPosition).position())
        .isEqualTo(indexBeforeClose.lookup(firstIndexedPosition).position());
    assertThat(indexAfterRestart.lookup(secondIndexedPosition).position())
        .isEqualTo(indexBeforeClose.lookup(secondIndexedPosition).position());
  }

  @Test
  void shouldContinueAppendAfterDetectingPartiallyWrittenDescriptor() throws Exception {
    // given
    final File dataFile = getJournalDirectory();
    assertThat(dataFile.mkdirs()).isTrue();
    final File emptyLog = new File(dataFile, "journal-1.log");
    assertThat(emptyLog.createNewFile()).isTrue();

    // when
    final var journal = openJournal(10);
    final var reader = journal.openReader();
    final var record = journal.append(journalFactory.entryData());

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(record.index());
    assertThat(journal.getLastIndex()).isEqualTo(record.index());
    assertThat(reader.next()).isEqualTo(record);
    assertThat(reader.hasNext()).isFalse();
  }

  private File getJournalDirectory() {
    return directory.resolve("data").toFile();
  }

  @Test
  void shouldContinueWritingAfterDetectingCorruptedDescriptorWithOutAckEntries() throws Exception {
    // given
    var journal = openJournal(1);
    journal.close();
    final File dataFile = getJournalDirectory();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    journal = openJournal(1);
    final var reader = journal.openReader();
    final var record = journal.append(journalFactory.entryData());

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(record.index());
    assertThat(journal.getLastIndex()).isEqualTo(record.index());
    assertThat(reader.next()).isEqualTo(record);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldHandleCorruptionAtDescriptorWithSomeAckedEntries() throws Exception {
    // given
    journal = openJournal(1);
    final var firstRecord = (PersistedJournalRecord) journal.append(journalFactory.entryData());
    final var copiedFirstRecord =
        new PersistedJournalRecord(
            firstRecord.metadata(),
            new RecordData(
                firstRecord.index(),
                firstRecord.asqn(),
                BufferUtil.cloneBuffer(firstRecord.data())));
    journal.append(journalFactory.entryData());

    // close the journal before corrupting the segment; since we "flush" when closing, we need to
    // restore the last flushed index to be before the first index of the second segment
    final var lastFlushedIndex = journal.getFirstSegment().lastIndex();
    journal.close();
    journalFactory.metaStore().storeLastFlushedIndex(lastFlushedIndex);

    final File dataFile = getJournalDirectory();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith("2.log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    journal = openJournal(1);
    final var reader = journal.openReader();
    final var lastRecord = journal.append(journalFactory.entryData());

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(copiedFirstRecord.index());
    assertThat(journal.getLastIndex()).isEqualTo(lastRecord.index());
    assertThat(reader.next()).isEqualTo(copiedFirstRecord);
    assertThat(reader.next()).isEqualTo(lastRecord);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldNotDeleteSegmentFileImmediately() {
    // given
    journal = openJournal(2);
    journal.append(journalFactory.entryData());
    journal.append(journalFactory.entryData());
    final var reader = journal.openReader();
    reader.next();

    // when
    journal.reset(100);

    // then
    final File logDirectory = getJournalDirectory();
    assertThat(logDirectory)
        .isDirectoryContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldNotFailOnResetAndOpeningReaderConcurrently() throws InterruptedException {
    // given
    final var latch = new CountDownLatch(2);
    journal = openJournal(2);
    journal.append(journalFactory.entryData());
    journal.append(journalFactory.entryData());

    // when
    new Thread(
            () -> {
              journal.reset(100);
              latch.countDown();
            })
        .start();
    new Thread(
            () -> {
              journal.openReader();
              latch.countDown();
            })
        .start();

    // then
    assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
  }

  // Regression test for https://github.com/camunda/zeebe/issues/7962
  @Test
  void shouldNotFailOnDeleteAndOpeningReaderConcurrently() throws InterruptedException {
    // given
    final var latch = new CountDownLatch(2);
    final var journal = openJournal(2);
    for (int i = 0; i < 10; i++) {
      journal.append(journalFactory.entryData());
    }
    final long indexToCompact = journal.append(journalFactory.entryData()).index();

    // when
    new Thread(
            () -> {
              journal.deleteUntil(indexToCompact);
              latch.countDown();
            })
        .start();
    new Thread(
            () -> {
              journal.openReader();
              latch.countDown();
            })
        .start();

    // then
    assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void shouldDeleteSegmentFileWhenReaderIsClosed() {
    // given
    journal = openJournal(2);
    journal.append(journalFactory.entryData());
    final var reader = journal.openReader();
    journal.reset(100);

    // when
    reader.close();

    // then
    final var logDirectory = getJournalDirectory();
    assertThat(logDirectory)
        .isDirectoryNotContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldDeleteSegmentFileImmediatelyWhenThereAreNoReaders() {
    // given
    journal = openJournal(2);
    journal.append(journalFactory.entryData());

    // when
    journal.reset(100);

    // then
    final var logDirectory = getJournalDirectory();
    assertThat(logDirectory)
        .isDirectoryNotContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldBeAbleToResetAgainWhileThePreviousFileIsNotDeleted() {
    // given
    journal = openJournal(2);
    journal.append(journalFactory.entryData());
    journal.openReader(); // Keep the reader opened so that the file is not deleted.
    journal.reset(100);
    journal.openReader(); // Keep the reader opened so that the file is not deleted.

    // when
    journal.reset(200);

    // then - there are two files deferred for deletion
    final var logDirectory = getJournalDirectory();
    assertThat(
            logDirectory.listFiles(
                file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName())))
        .hasSize(2);
    assertThat(
            logDirectory.listFiles(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName())))
        .hasSize(1);
  }

  @Test
  void shouldReleaseReadLock() throws InterruptedException {
    // given
    final var latch = new CountDownLatch(1);
    final var journal = openJournal(5);

    // delete first segment so that it closes
    final var segment = journal.getFirstSegment();
    segment.delete();

    // expect
    assertThat(segment.isOpen()).isFalse();
    assertThatThrownBy(journal::openReader)
        .withFailMessage("Segment not open")
        .isInstanceOf(IllegalStateException.class);

    // when
    new Thread(
            () -> {
              journal.reset(100);
              latch.countDown();
            })
        .start();

    // then
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void shouldPreallocateSegmentFiles(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var builder =
        SegmentedJournal.builder()
            .withPreallocateSegmentFiles(true)
            .withMaxSegmentSize(segmentSize)
            .withDirectory(tmpDir.toFile())
            .withMetaStore(new InMemory());
    final File firstSegment;

    // when
    try (final var journal = builder.build()) {
      firstSegment = journal.getFirstSegment().file().file();
    }

    // then
    PosixPathAssert.assertThat(firstSegment).hasRealSize(segmentSize);
  }

  @Test
  void shouldNotPreallocateSegmentFiles(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var builder =
        SegmentedJournal.builder()
            .withPreallocateSegmentFiles(false)
            .withMaxSegmentSize(segmentSize)
            .withDirectory(tmpDir.toFile())
            .withMetaStore(new InMemory());
    final File firstSegment;

    // when
    try (final var journal = builder.build()) {
      firstSegment = journal.getFirstSegment().file().file();
    }

    // then
    PosixPathAssert.assertThat(firstSegment).hasRealSizeLessThan(segmentSize);
  }

  @Test
  void shouldUpdateMetastoreAfterFlush() {
    journal = openJournal(2);
    journal.append(1, journalFactory.entryData());
    final var lastWrittenIndex = journal.append(2, journalFactory.entryData()).index();

    // when
    journal.flush();

    // then
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(lastWrittenIndex);
  }

  private SegmentedJournal openJournal(final int entriesPerSegment) {
    return openJournal("test", entriesPerSegment);
  }

  private SegmentedJournal openJournal(final String data, final int entriesPerSegment) {
    journalFactory = new TestJournalFactory(data, entriesPerSegment);
    return journalFactory.journal(directory);
  }
}
