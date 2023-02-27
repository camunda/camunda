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

import static io.camunda.zeebe.journal.file.SegmentedJournal.ASQN_IGNORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.zeebe.journal.JournalException.InvalidAsqn;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.PersistedJournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.journal.util.PosixPathAssert;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.Assertions;
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
    journal.append(1, journalFactory.entry());
    journal.append(2, journalFactory.entry());
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
      journal.append(i + 1, journalFactory.entry());
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
    journal.append(1, journalFactory.entry());
    journal.append(2, journalFactory.entry());

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
      journal.append(asqn + i, journalFactory.entry());
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
    journal = openJournal(1);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < 2; i++) {
      lastIndex = journal.append(asqn + i, journalFactory.entry()).index();
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
    final var firstRecord = journal.append(asqn, journalFactory.entry());
    final var secondRecord = journal.append(asqn + 1, journalFactory.entry());
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
    journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(1, journalFactory.entry());
    journal.append(2, journalFactory.entry()).index();
    journal.append(3, journalFactory.entry()).index();

    assertThat(reader.next()).isEqualTo(firstRecord);
    journal.deleteAfter(firstRecord.index());

    // then
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldTruncateReadEntry() {
    // given
    journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final long first = journal.append(1, journalFactory.entry()).index();
    journal.append(2, journalFactory.entry()).index();

    assertThat(reader.hasNext()).isTrue();
    journal.deleteAfter(first - 1);

    // then
    assertThat(reader.hasNext()).isFalse();
    assertThat(journal.getLastIndex()).isZero();
  }

  @Test
  void shouldTruncateNextSegment() {
    // given
    journal = openJournal(1);
    final JournalReader reader = journal.openReader();

    // when
    final var firstRecord = journal.append(1, journalFactory.entry());
    journal.append(2, journalFactory.entry());
    journal.deleteAfter(firstRecord.index());

    // then
    assertThat(reader.next()).isEqualTo(firstRecord);
    assertThat(reader.hasNext()).isFalse();
    assertThat(journal.getLastIndex()).isEqualTo(firstRecord.index());
  }

  @Test
  void shouldReadSegmentStartAfterMidSegmentTruncate() {
    final int entryPerSegment = 2;
    journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entry()).index();
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
    journal = openJournal(entryPerSegment);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entry()).index();
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
    journal = openJournal(entryPerSegment);
    final JournalReader reader = journal.openReader();

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entry()).index();
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
    journal = openJournal(2);

    // when
    long lastIndex = -1;
    for (int i = 0; i < entryPerSegment * 2; i++) {
      lastIndex = journal.append(i + 1, journalFactory.entry()).index();
    }
    journal.deleteUntil(lastIndex);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(lastIndex - 1);
  }

  @Test
  void shouldWriteAndReadAfterTruncate() {
    journal = openJournal(2);
    final JournalReader reader = journal.openReader();

    // when
    final long first = journal.append(1, journalFactory.entry()).index();
    journal.append(2, journalFactory.entry());
    journal.deleteAfter(first - 1);
    final var lastRecord =
        journal.append(
            3,
            new DirectBufferWriter()
                .wrap(new UnsafeBuffer("new".getBytes(StandardCharsets.UTF_8))));

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
    final var firstRecord =
        journal.append(new DirectBufferWriter().wrap(new UnsafeBuffer("12345".getBytes())));
    final var secondRecord =
        journal.append(new DirectBufferWriter().wrap(new UnsafeBuffer("1234567".getBytes())));
    final var thirdRecord =
        journal.append(new DirectBufferWriter().wrap(new UnsafeBuffer("1234567890".getBytes())));

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
    journal.append(1, journalFactory.entry());
    journal.append(2, journalFactory.entry());
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
    final File dataFile = directory.resolve("data").toFile();
    assertThat(dataFile.mkdirs()).isTrue();
    final File emptyLog = new File(dataFile, "journal-1.log");
    assertThat(emptyLog.createNewFile()).isTrue();

    // when
    journal = openJournal(10);
    final var reader = journal.openReader();
    final var record = journal.append(journalFactory.entry());

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(record.index());
    assertThat(journal.getLastIndex()).isEqualTo(record.index());
    assertThat(reader.next()).isEqualTo(record);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldContinueWritingAfterDetectingCorruptedDescriptorWithOutAckEntries() throws Exception {
    // given
    journal = openJournal(1);
    journal.close();
    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith(".log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    journal = openJournal(1);
    final var reader = journal.openReader();
    final var record = journal.append(journalFactory.entry());

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
    final var firstRecord = (PersistedJournalRecord) journal.append(journalFactory.entry());
    final var copiedFirstRecord =
        new PersistedJournalRecord(
            firstRecord.metadata(),
            new RecordData(
                firstRecord.index(),
                firstRecord.asqn(),
                BufferUtil.cloneBuffer(firstRecord.data())));
    journal.append(journalFactory.entry());

    // close the journal before corrupting the segment; since we "flush" when closing, we need to
    // restore the last flushed index to be before the first index of the second segment
    final var lastFlushedIndex = journal.getFirstSegment().lastIndex();
    journal.close();
    journalFactory.metaStore().storeLastFlushedIndex(lastFlushedIndex);

    final File dataFile = directory.resolve("data").toFile();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith("2.log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    journal = openJournal(1);
    final var reader = journal.openReader();
    final var lastRecord = journal.append(journalFactory.entry());

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
    journal.append(journalFactory.entry());
    journal.append(journalFactory.entry());
    final var reader = journal.openReader();
    reader.next();

    // when
    journal.reset(100);

    // then
    final File logDirectory = directory.resolve("data").toFile();
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
    journal.append(journalFactory.entry());
    journal.append(journalFactory.entry());

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
    journal = openJournal(2);
    for (int i = 0; i < 10; i++) {
      journal.append(journalFactory.entry());
    }
    final long indexToCompact = journal.append(journalFactory.entry()).index();

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
    journal.append(journalFactory.entry());
    final var reader = journal.openReader();
    journal.reset(100);

    // when
    reader.close();

    // then
    final File logDirectory = directory.resolve("data").toFile();
    assertThat(logDirectory)
        .isDirectoryNotContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldDeleteSegmentFileImmediatelyWhenThereAreNoReaders() {
    // given
    journal = openJournal(2);
    journal.append(journalFactory.entry());

    // when
    journal.reset(100);

    // then
    final File logDirectory = directory.resolve("data").toFile();
    assertThat(logDirectory)
        .isDirectoryNotContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldBeAbleToResetAgainWhileThePreviousFileIsNotDeleted() {
    // given
    journal = openJournal(2);
    journal.append(journalFactory.entry());
    journal.openReader(); // Keep the reader opened so that the file is not deleted.
    journal.reset(100);
    journal.openReader(); // Keep the reader opened so that the file is not deleted.

    // when
    journal.reset(200);

    // then
    final File logDirectory = directory.resolve("data").toFile();

    // there are two files deferred for deletion
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
    journal = openJournal(5);

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
            .withMetaStore(new MockJournalMetastore());
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
            .withMetaStore(new MockJournalMetastore());
    final File firstSegment;

    // when
    try (final var journal = builder.build()) {
      firstSegment = journal.getFirstSegment().file().file();
    }

    // then
    PosixPathAssert.assertThat(firstSegment).hasRealSizeLessThan(segmentSize);
  }

  @Test
  void shouldValidateAsqnBeforeCreatingNewSegment() {
    // given
    // one entry fits but not two
    journal = openJournal(1);
    journal.append(1, journalFactory.entry());

    // when/then
    Assertions.assertThatThrownBy(() -> journal.append(1, journalFactory.entry()))
        .isInstanceOf(InvalidAsqn.class);
    assertThat(journal.getFirstSegment()).isEqualTo(journal.getLastSegment());
  }

  @Test
  void shouldValidateAsqnWhenWritingToNewSegment() {
    // given
    // one entry fits but not two
    journal = openJournal(1);
    journal.append(1, journalFactory.entry());

    // when
    // force creation of new segment with an asqn ignore entry
    journal.append(ASQN_IGNORE, journalFactory.entry());

    // then
    // validation of the asqn should fail on the new segment as well
    Assertions.assertThatThrownBy(() -> journal.append(1, journalFactory.entry()))
        .isInstanceOf(InvalidAsqn.class);

    assertThat(journal.getFirstSegment()).isNotEqualTo(journal.getLastSegment());
  }

  @Test
  void shouldValidateAsqnWhenWritingAfterRestartOnSameSegment() {
    // given
    // one entry fits but not two
    final int entriesPerSegment = 5;
    journal = openJournal(entriesPerSegment);

    journal.append(1, journalFactory.entry());

    // when
    journal.close();
    journal = openJournal(entriesPerSegment);

    // then
    final SegmentedJournal finalJournal = journal;
    Assertions.assertThatThrownBy(() -> finalJournal.append(1, journalFactory.entry()))
        .isInstanceOf(InvalidAsqn.class);

    assertThat(journal.getFirstSegment()).isEqualTo(journal.getLastSegment());
  }

  @Test
  void shouldValidateAsqnWhenWritingAfterRestartOnNewSegmentWithOnlyAsqnIgnoreRecord() {
    // given
    // one entry fits but not two
    final int entriesPerSegment = 1;
    journal = openJournal(entriesPerSegment);

    journal.append(1, journalFactory.entry());

    // when
    // force creation of new segment with an asqn ignore entry
    journal.append(ASQN_IGNORE, journalFactory.entry());
    journal.close();

    // then
    try (final var reopenedJournal = openJournal(entriesPerSegment)) {
      Assertions.assertThatThrownBy(() -> reopenedJournal.append(1, journalFactory.entry()))
          .isInstanceOf(InvalidAsqn.class);
      assertThat(reopenedJournal.getFirstSegment()).isNotEqualTo(reopenedJournal.getLastSegment());
    }
  }

  @Test
  void shouldResetAsqnOnTruncateToLastAsqnOfPreviousSegment() {
    // given
    journal = openJournal(2);
    final long initalAsqn = journal.getFirstSegment().lastAsqn();
    journal.append(1, journalFactory.entry());

    // when
    journal.deleteAfter(journal.getFirstSegment().index() - 1);

    // then
    assertThat(journal.getFirstSegment().lastAsqn()).isEqualTo(initalAsqn);
    assertThat(journal.getFirstSegment()).isEqualTo(journal.getLastSegment());
  }

  @Test
  void shouldResetAsqnOnTruncateToLastAsqnOfPreviousSegmentWhenNewSegContainsAsqnIgnoreOnly() {
    // given
    journal = openJournal(2);
    journal.append(1, journalFactory.entry());
    final var expectedLastAsqn = journal.append(2, journalFactory.entry()).asqn();
    // write to second segment
    final var indexToTruncate = journal.append(-1, journalFactory.entry()).index();
    journal.append(3, journalFactory.entry());

    // when
    journal.deleteAfter(indexToTruncate);

    // then
    assertThat(journal.getLastSegment().lastAsqn()).isEqualTo(expectedLastAsqn);
  }

  @Test
  void shouldSuceedToAppendWithPreviousAsqnAfterTruncateToIndexOfPreviousSegment() {
    // given
    journal = openJournal(2);
    journal.append(1, journalFactory.entry());

    // when
    journal.deleteAfter(journal.getFirstSegment().index() - 1);

    // then
    journal.append(1, journalFactory.entry());
  }

  @Test
  void shouldResetAsqnOnTruncateToIndexWithinCurrentSegment() {
    // given
    journal = openJournal(2);
    final JournalRecord firstJournalRecord = journal.append(1, journalFactory.entry());
    journal.append(2, journalFactory.entry());

    // when
    journal.deleteAfter(firstJournalRecord.index());

    // then
    assertThat(journal.getLastSegment().lastAsqn()).isEqualTo(firstJournalRecord.asqn());
  }

  @Test
  void shouldSucceedToAppendWithPreviousAsqnAfterTruncateToIndexWithinCurrentSegment() {
    // given
    journal = openJournal(2);
    final JournalRecord firstJournalRecord = journal.append(1, journalFactory.entry());
    journal.append(2, journalFactory.entry());

    // when
    journal.deleteAfter(firstJournalRecord.index());

    // then
    journal.append(2, journalFactory.entry());
  }

  // this test ensure that flushing is thread-safe w.r.t. write-exclusive methods such as
  // deleteUntil, deleteAfter, and reset
  @Test
  void shouldPreventWriteExclusiveOperationsWhileFlushing() {
    // given
    final var barrier = new Phaser(2);
    journal = openJournal(2);
    journal.append(1, journalFactory.entry());
    final var lastWrittenIndex = journal.append(2, journalFactory.entry()).index();

    // when
    journalFactory
        .metaStore()
        .setOnStoreFlushedIndex(
            () -> {
              // two synchronization points ensure that we check the lock status strictly while the
              // other thread is busy flushing, and not before or after
              barrier.arriveAndAwaitAdvance();
              barrier.arriveAndAwaitAdvance();
            });
    final var flushed = CompletableFuture.runAsync(journal::flush);

    // then
    barrier.arriveAndAwaitAdvance();
    assertThat(journal.rwlock().isReadLocked()).isTrue();
    barrier.arrive();
    assertThat(flushed).succeedsWithin(Duration.ofSeconds(5));
    assertThat(journalFactory.metaStore().loadLastFlushedIndex()).isEqualTo(lastWrittenIndex);
  }

  private SegmentedJournal openJournal(final int entriesPerSegment) {
    return openJournal("test", entriesPerSegment);
  }

  private SegmentedJournal openJournal(final String data, final int entriesPerSegment) {
    journalFactory = new TestJournalFactory(data, entriesPerSegment);
    return journalFactory.journal(journalFactory.segmentsManager(directory));
  }
}
