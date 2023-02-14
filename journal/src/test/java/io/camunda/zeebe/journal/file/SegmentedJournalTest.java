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

import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.record.PersistedJournalRecord;
import io.camunda.zeebe.journal.record.RecordData;
import io.camunda.zeebe.journal.record.SBESerializer;
import io.camunda.zeebe.journal.util.MockJournalMetastore;
import io.camunda.zeebe.journal.util.PosixPathAssert;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("resource")
class SegmentedJournalTest {
  private static final String JOURNAL_NAME = "journal";

  @TempDir Path directory;
  private final int journalIndexDensity = 1;
  private final DirectBuffer data = new UnsafeBuffer("test".getBytes(StandardCharsets.UTF_8));
  private final int entrySize = getSerializedSize(data);

  private SegmentedJournal journal;
  private final MockJournalMetastore metaStore = new MockJournalMetastore();

  @AfterEach
  void teardown() {
    CloseHelper.quietClose(journal);
  }

  @Test
  void shouldDeleteIndexMappingsOnReset() {
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
  void shouldUpdateIndexMappingsOnCompact() {
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
    final IndexInfo lookup = journal.getJournalIndex().lookup(entriesPerSegment - 1);
    assertThat(lookup).isNull();
    assertThat(journal.getJournalIndex().lookup(3 * entriesPerSegment)).isNotNull();
  }

  @Test
  void shouldUpdateIndexMappingsOnTruncate() {
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
    assertThat(journal.getJournalIndex().lookup(2 * journalIndexDensity).index()).isOne();
  }

  @Test
  void shouldCreateNewSegmentIfEntryExceedsBuffer() {
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
  void shouldNotTruncateIfIndexIsHigherThanLast() {
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
  void shouldNotCompactIfIndexIsLowerThanFirst() {
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
  void shouldTruncateNextEntry() {
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
  void shouldTruncateReadEntry() {
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
    assertThat(journal.getLastIndex()).isZero();
  }

  @Test
  void shouldTruncateNextSegment() {
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
  void shouldReadSegmentStartAfterMidSegmentTruncate() {
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
  void shouldCompactUpToStartOfSegment() {
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
      lastIndex = journal.append(i + 1, data).index();
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
      lastIndex = journal.append(i + 1, data).index();
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
  void shouldAppendEntriesOfDifferentSizesOverSegmentSize() {
    // given
    data.wrap("1234567890".getBytes(StandardCharsets.UTF_8));
    final int entrySize = getSerializedSize(data);
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

  @Test
  void shouldUpdateIndexMappingsAfterRestart() {
    // given
    final int entriesPerSegment = 10;
    long asqn = 1;
    SegmentedJournal journal = openJournal(entriesPerSegment);
    for (int i = 0; i < 2 * journalIndexDensity; i++) {
      journal.append(asqn++, data);
    }
    final var indexBeforeClose = journal.getJournalIndex();

    // when
    journal.close();
    journal = openJournal(entriesPerSegment);

    // then
    final var firstIndexedPosition = journalIndexDensity;
    final var secondIndexedPosition = 2 * journalIndexDensity;
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
    final var record = journal.append(data);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(record.index());
    assertThat(journal.getLastIndex()).isEqualTo(record.index());
    assertThat(reader.next()).isEqualTo(record);
    assertThat(reader.hasNext()).isFalse();
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
    final var record = journal.append(data);

    // then
    assertThat(journal.getFirstIndex()).isEqualTo(record.index());
    assertThat(journal.getLastIndex()).isEqualTo(record.index());
    assertThat(reader.next()).isEqualTo(record);
    assertThat(reader.hasNext()).isFalse();
  }

  @Test
  void shouldHandleCorruptionAtDescriptorWithSomeAckedEntries() throws Exception {
    // given
    var journal = openJournal(1);
    final var firstRecord = (PersistedJournalRecord) journal.append(data);
    final var copiedFirstRecord =
        new PersistedJournalRecord(
            firstRecord.metadata(),
            new RecordData(
                firstRecord.index(),
                firstRecord.asqn(),
                BufferUtil.cloneBuffer(firstRecord.data())));
    journal.append(data);

    journal.close();
    final File dataFile = getJournalDirectory();
    final File logFile =
        Objects.requireNonNull(dataFile.listFiles(f -> f.getName().endsWith("2.log")))[0];
    LogCorrupter.corruptDescriptor(logFile);

    // when/then
    journal = openJournal(1);
    final var reader = journal.openReader();
    final var lastRecord = journal.append(data);

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
    final var journal = openJournal(2);
    journal.append(data);
    journal.append(data);
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
    final var journal = openJournal(2);
    journal.append(data);
    journal.append(data);

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
      journal.append(data);
    }
    final long indexToCompact = journal.append(data).index();

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
    final var journal = openJournal(2);
    journal.append(data);
    final var reader = journal.openReader();
    journal.reset(100);

    // when
    reader.close();

    // then
    final File logDirectory = getJournalDirectory();
    assertThat(logDirectory)
        .isDirectoryNotContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldDeleteSegmentFileImmediatelyWhenThereAreNoReaders() {
    // given
    final var journal = openJournal(2);
    journal.append(data);

    // when
    journal.reset(100);

    // then
    final File logDirectory = getJournalDirectory();
    assertThat(logDirectory)
        .isDirectoryNotContaining(
            file -> SegmentFile.isDeletedSegmentFile(JOURNAL_NAME, file.getName()))
        .isDirectoryContaining(file -> SegmentFile.isSegmentFile(JOURNAL_NAME, file.getName()));
  }

  @Test
  void shouldBeAbleToResetAgainWhileThePreviousFileIsNotDeleted() {
    // given
    final var journal = openJournal(2);
    journal.append(data);
    journal.openReader(); // Keep the reader opened so that the file is not deleted.
    journal.reset(100);
    journal.openReader(); // Keep the reader opened so that the file is not deleted.

    // when
    journal.reset(200);

    // then
    final File logDirectory = getJournalDirectory();

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
  void shouldUpdateMetastoreAfterFlush() {
    journal = openJournal(2);
    journal.append(1, data);
    final var lastWrittenIndex = journal.append(2, data).index();

    // when
    journal.flush();

    // then
    assertThat(metaStore.loadLastFlushedIndex()).isEqualTo(lastWrittenIndex);
  }

  private SegmentedJournal openJournal(final float entriesPerSegment) {
    return openJournal(entriesPerSegment, entrySize);
  }

  private SegmentedJournal openJournal(final float entriesPerSegment, final int entrySize) {
    final var maxSegmentSize =
        (long) (entrySize * entriesPerSegment) + SegmentDescriptor.getEncodingLength();
    return openJournal(maxSegmentSize, true);
  }

  private SegmentedJournal openJournal(
      final long maxSegmentSize, final boolean preallocateSegmentFiles) {
    return SegmentedJournal.builder()
        .withDirectory(getJournalDirectory())
        .withMaxSegmentSize((int) maxSegmentSize)
        .withJournalIndexDensity(journalIndexDensity)
        .withName(JOURNAL_NAME)
        .withPreallocateSegmentFiles(preallocateSegmentFiles)
        .withMetaStore(metaStore)
        .build();
  }

  private File getJournalDirectory() {
    return directory.resolve("data").toFile();
  }

  private int getSerializedSize(final DirectBuffer data) {
    final var record = new RecordData(1, 1, data);
    final var serializer = new SBESerializer();
    final ByteBuffer buffer = ByteBuffer.allocate(128);
    return serializer.writeData(record, new UnsafeBuffer(buffer), 0).get()
        + FrameUtil.getLength()
        + serializer.getMetadataLength();
  }
}
