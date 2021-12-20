/*
 * Copyright 2017-present Open Networking Foundation
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
package io.camunda.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.journal.file.record.CorruptedLogException;
import io.camunda.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.StampedLock;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A file based journal. The journal is split into multiple segments files. */
public final class SegmentedJournal implements Journal {
  public static final long ASQN_IGNORE = -1;
  private static final ByteOrder ENDIANNESS = ByteOrder.LITTLE_ENDIAN;
  private static final int SEGMENT_BUFFER_FACTOR = 3;
  private static final int FIRST_SEGMENT_ID = 1;
  private static final int INITIAL_INDEX = 1;
  private final JournalMetrics journalMetrics;
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String name;
  private final File directory;
  private final int maxSegmentSize;
  private final NavigableMap<Long, JournalSegment> segments = new ConcurrentSkipListMap<>();
  private final Collection<SegmentedJournalReader> readers = Sets.newConcurrentHashSet();
  private volatile JournalSegment currentSegment;
  private volatile boolean open = true;
  private final long minFreeDiskSpace;
  private final JournalIndex journalIndex;
  private final SegmentedJournalWriter writer;
  private final long lastWrittenIndex;
  private final StampedLock rwlock = new StampedLock();

  public SegmentedJournal(
      final String name,
      final File directory,
      final int maxSegmentSize,
      final long minFreeSpace,
      final JournalIndex journalIndex,
      final long lastWrittenIndex) {
    this.name = checkNotNull(name, "name cannot be null");
    this.directory = checkNotNull(directory, "directory cannot be null");
    this.maxSegmentSize = maxSegmentSize;
    journalMetrics = new JournalMetrics(name);
    minFreeDiskSpace = minFreeSpace;
    this.journalIndex = journalIndex;
    this.lastWrittenIndex = lastWrittenIndex;
    open();
    writer = new SegmentedJournalWriter(this);
  }

  /**
   * Returns a new SegmentedJournal builder.
   *
   * @return A new Segmented journal builder.
   */
  public static SegmentedJournalBuilder builder() {
    return new SegmentedJournalBuilder();
  }

  @Override
  public JournalRecord append(final long asqn, final DirectBuffer data) {
    return writer.append(asqn, data);
  }

  @Override
  public JournalRecord append(final DirectBuffer data) {
    return writer.append(ASQN_IGNORE, data);
  }

  @Override
  public void append(final JournalRecord record) {
    writer.append(record);
  }

  @Override
  public void deleteAfter(final long indexExclusive) {
    journalMetrics.observeSegmentTruncation(
        () -> {
          final var stamp = rwlock.writeLock();
          try {
            writer.deleteAfter(indexExclusive);
            // Reset segment readers.
            resetAdvancedReaders(indexExclusive + 1);
          } finally {
            rwlock.unlockWrite(stamp);
          }
        });
  }

  @Override
  public void deleteUntil(final long index) {
    final Map.Entry<Long, JournalSegment> segmentEntry = segments.floorEntry(index);
    if (segmentEntry != null) {
      final SortedMap<Long, JournalSegment> compactSegments =
          segments.headMap(segmentEntry.getValue().index());
      if (compactSegments.isEmpty()) {
        log.debug(
            "No segments can be deleted with index < {} (first log index: {})",
            index,
            getFirstIndex());
        return;
      }

      final var stamp = rwlock.writeLock();
      try {
        log.debug(
            "{} - Deleting log up from {} up to {} (removing {} segments)",
            name,
            getFirstIndex(),
            compactSegments.get(compactSegments.lastKey()).index(),
            compactSegments.size());
        for (final JournalSegment segment : compactSegments.values()) {
          log.trace("{} - Deleting segment: {}", name, segment);
          segment.delete();
          journalMetrics.decSegmentCount();
        }

        // removes them from the segment map
        compactSegments.clear();

        journalIndex.deleteUntil(index);
      } finally {
        rwlock.unlockWrite(stamp);
      }
    }
  }

  @Override
  public void reset(final long nextIndex) {
    final var stamp = rwlock.writeLock();
    try {
      journalIndex.clear();
      writer.reset(nextIndex);
    } finally {
      rwlock.unlockWrite(stamp);
    }
  }

  @Override
  public long getLastIndex() {
    return writer.getLastIndex();
  }

  @Override
  public long getFirstIndex() {
    if (!segments.isEmpty()) {
      return segments.firstEntry().getValue().index();
    } else {
      return 0;
    }
  }

  @Override
  public boolean isEmpty() {
    return writer.getNextIndex() - getFirstSegment().index() == 0;
  }

  @Override
  public void flush() {
    writer.flush();
  }

  @Override
  public JournalReader openReader() {
    final var stamped = acquireReadlock();
    try {
      final var reader = new SegmentedJournalReader(this);
      readers.add(reader);
      return reader;
    } finally {
      releaseReadlock(stamped);
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    segments
        .values()
        .forEach(
            segment -> {
              log.debug("Closing segment: {}", segment);
              segment.close();
            });
    currentSegment = null;
    open = false;
  }

  /** Opens the segments. */
  private synchronized void open() {
    final var openDurationTimer = journalMetrics.startJournalOpenDurationTimer();
    // Load existing log segments from disk.
    for (final JournalSegment segment : loadSegments()) {
      segments.put(segment.descriptor().index(), segment);
      journalMetrics.incSegmentCount();
    }

    // If a segment doesn't already exist, create an initial segment starting at index 1.
    if (!segments.isEmpty()) {
      currentSegment = segments.lastEntry().getValue();
    } else {
      final JournalSegmentDescriptor descriptor =
          JournalSegmentDescriptor.builder()
              .withId(FIRST_SEGMENT_ID)
              .withIndex(INITIAL_INDEX)
              .withMaxSegmentSize(maxSegmentSize)
              .build();

      currentSegment = createSegment(descriptor);

      segments.put(1L, currentSegment);
      journalMetrics.incSegmentCount();
    }
    // observe the journal open duration
    openDurationTimer.close();

    // Delete files that were previously marked for deletion but did not get deleted because the
    // node was stopped. It is safe to delete it now since there are no readers opened for these
    // segments.
    deleteDeferredFiles();
  }

  /**
   * Asserts that the journal is open.
   *
   * @throws IllegalStateException if the journal is not open
   */
  private void assertOpen() {
    checkState(currentSegment != null, "journal not open");
  }

  /** Asserts that enough disk space is available to allocate a new segment. */
  private void assertDiskSpace() {
    if (directory().getUsableSpace()
        < Math.max(maxSegmentSize() * SEGMENT_BUFFER_FACTOR, minFreeDiskSpace)) {
      throw new JournalException.OutOfDiskSpace(
          "Not enough space to allocate a new journal segment");
    }
  }

  private long maxSegmentSize() {
    return maxSegmentSize;
  }

  private File directory() {
    return directory;
  }

  /** Resets the current segment, creating a new segment if necessary. */
  private synchronized void resetCurrentSegment() {
    final JournalSegment lastSegment = getLastSegment();
    if (lastSegment != null) {
      currentSegment = lastSegment;
    } else {
      final JournalSegmentDescriptor descriptor =
          JournalSegmentDescriptor.builder()
              .withId(1)
              .withIndex(1)
              .withMaxSegmentSize(maxSegmentSize)
              .build();

      currentSegment = createSegment(descriptor);

      segments.put(1L, currentSegment);
      journalMetrics.incSegmentCount();
    }
  }

  /**
   * Resets and returns the first segment in the journal.
   *
   * @param index the starting index of the journal
   * @return the first segment
   */
  JournalSegment resetSegments(final long index) {
    assertOpen();

    for (final JournalSegment segment : segments.values()) {
      segment.delete();
      journalMetrics.decSegmentCount();
    }
    segments.clear();

    final JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder()
            .withId(1)
            .withIndex(index)
            .withMaxSegmentSize(maxSegmentSize)
            .build();
    currentSegment = createSegment(descriptor);
    segments.put(index, currentSegment);
    journalMetrics.incSegmentCount();
    return currentSegment;
  }

  /**
   * Returns the first segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  JournalSegment getFirstSegment() {
    assertOpen();
    final Map.Entry<Long, JournalSegment> segment = segments.firstEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Returns the last segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  JournalSegment getLastSegment() {
    assertOpen();
    final Map.Entry<Long, JournalSegment> segment = segments.lastEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Creates and returns the next segment.
   *
   * @return The next segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  synchronized JournalSegment getNextSegment() {
    assertOpen();
    assertDiskSpace();

    final JournalSegment lastSegment = getLastSegment();
    final JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder()
            .withId(lastSegment != null ? lastSegment.descriptor().id() + 1 : 1)
            .withIndex(currentSegment.lastIndex() + 1)
            .withMaxSegmentSize(maxSegmentSize)
            .build();

    currentSegment = createSegment(descriptor);

    segments.put(descriptor.index(), currentSegment);
    journalMetrics.incSegmentCount();
    return currentSegment;
  }

  /**
   * Returns the segment following the segment with the given ID.
   *
   * @param index The segment index with which to look up the next segment.
   * @return The next segment for the given index.
   */
  JournalSegment getNextSegment(final long index) {
    final Map.Entry<Long, JournalSegment> nextSegment = segments.higherEntry(index);
    return nextSegment != null ? nextSegment.getValue() : null;
  }

  /**
   * Returns the segment for the given index.
   *
   * @param index The index for which to return the segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  synchronized JournalSegment getSegment(final long index) {
    assertOpen();
    // Check if the current segment contains the given index first in order to prevent an
    // unnecessary map lookup.
    if (currentSegment != null && index > currentSegment.index()) {
      return currentSegment;
    }

    // If the index is in another segment, get the entry with the next lowest first index.
    final Map.Entry<Long, JournalSegment> segment = segments.floorEntry(index);
    if (segment != null) {
      return segment.getValue();
    }
    return getFirstSegment();
  }

  /**
   * Removes a segment.
   *
   * @param segment The segment to remove.
   */
  synchronized void removeSegment(final JournalSegment segment) {
    segments.remove(segment.index());
    journalMetrics.decSegmentCount();
    segment.delete();
    resetCurrentSegment();
  }

  /**
   * Loads all segments from disk.
   *
   * @return A collection of segments for the log.
   */
  protected Collection<JournalSegment> loadSegments() {
    // Ensure log directories are created.
    directory.mkdirs();
    final List<JournalSegment> segments = new ArrayList<>();

    final List<File> files = getSortedLogSegments();
    for (int i = 0; i < files.size(); i++) {
      final File file = files.get(i);

      try {
        log.debug("Found segment file: {}", file.getName());
        final JournalSegment segment = loadExistingSegment(file);

        if (i > 0) {
          checkForIndexGaps(segments.get(i - 1), segment);
        }

        segments.add(segment);
      } catch (final CorruptedLogException e) {
        if (handleSegmentCorruption(files, segments, i)) {
          return segments;
        }

        throw e;
      }
    }

    return segments;
  }

  private void checkForIndexGaps(final JournalSegment prevSegment, final JournalSegment segment) {
    if (prevSegment.lastIndex() != segment.index() - 1) {
      throw new CorruptedLogException(
          String.format(
              "Log segment %s is not aligned with previous segment %s (last index: %d).",
              segment, prevSegment, prevSegment.lastIndex()));
    }
  }

  /** Returns true if segments after corrupted segment were deleted; false, otherwise */
  private boolean handleSegmentCorruption(
      final List<File> files, final List<JournalSegment> segments, final int failedIndex) {
    long lastSegmentIndex = 0;

    if (!segments.isEmpty()) {
      final JournalSegment previousSegment = segments.get(segments.size() - 1);
      lastSegmentIndex = previousSegment.lastIndex();
    }

    if (lastWrittenIndex > lastSegmentIndex) {
      return false;
    }

    log.debug(
        "Found corrupted segment after last ack'ed index {}. Deleting segments {} - {}",
        lastWrittenIndex,
        files.get(failedIndex).getName(),
        files.get(files.size() - 1).getName());

    for (int i = failedIndex; i < files.size(); i++) {
      final File file = files.get(i);
      try {
        Files.delete(file.toPath());
      } catch (final IOException e) {
        throw new JournalException(
            String.format(
                "Failed to delete log segment '%s' when handling corruption.", file.getName()),
            e);
      }
    }

    return true;
  }

  /** Returns an array of valid log segments sorted by their id which may be empty but not null. */
  private List<File> getSortedLogSegments() {
    final File[] files =
        directory.listFiles(file -> file.isFile() && JournalSegmentFile.isSegmentFile(name, file));

    if (files == null) {
      throw new IllegalStateException(
          String.format(
              "Could not list files in directory '%s'. Either the path doesn't point to a directory or an I/O error occurred.",
              directory));
    }

    Arrays.sort(
        files, Comparator.comparingInt(f -> JournalSegmentFile.getSegmentIdFromPath(f.getName())));

    return Arrays.asList(files);
  }

  private void deleteDeferredFiles() {
    try (final DirectoryStream<Path> segmentsToDelete =
        Files.newDirectoryStream(
            directory.toPath(),
            path -> JournalSegmentFile.isDeletedSegmentFile(name, path.getFileName().toString()))) {
      segmentsToDelete.forEach(this::deleteDeferredFile);
    } catch (final IOException e) {
      log.warn(
          "Could not delete segment files marked for deletion in {}. This can result in unnecessary disk usage.",
          directory.toPath(),
          e);
    }
  }

  private void deleteDeferredFile(final Path segmentFileToDelete) {
    try {
      Files.deleteIfExists(segmentFileToDelete);
    } catch (final IOException e) {
      log.warn(
          "Could not delete file {} which is marked for deletion. This can result in unnecessary disk usage.",
          segmentFileToDelete,
          e);
    }
  }

  private JournalSegmentDescriptor readDescriptor(final File file) {
    try (final FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
      final byte version = readVersion(channel, file.getName());
      final int length = JournalSegmentDescriptor.getEncodingLengthForVersion(version);
      if (file.length() < length) {
        throw new CorruptedLogException(
            String.format(
                "Expected segment '%s' with version %d to be at least %d bytes long but it only has %d.",
                file.getName(), version, length, file.length()));
      }

      final ByteBuffer buffer = ByteBuffer.allocate(length);
      final int readBytes = channel.read(buffer, 0);

      if (readBytes != -1 && readBytes < length) {
        throw new JournalException(
            String.format(
                "Expected to read %d bytes of segment '%s' with %d version but only read %d bytes.",
                length, file.getName(), version, readBytes));
      }

      buffer.flip();
      return new JournalSegmentDescriptor(buffer);
    } catch (final IndexOutOfBoundsException e) {
      throw new JournalException(
          String.format(
              "Expected to read descriptor of segment '%s', but nothing was read.", file.getName()),
          e);
    } catch (final UnknownVersionException e) {
      throw new CorruptedLogException(
          String.format("Couldn't read or recognize version of segment '%s'.", file.getName()), e);
    } catch (final IOException e) {
      throw new JournalException(e);
    }
  }

  private byte readVersion(final FileChannel channel, final String fileName) throws IOException {
    final ByteBuffer buffer = ByteBuffer.allocate(1);
    final int readBytes = channel.read(buffer);

    if (readBytes == 0) {
      throw new JournalException(
          String.format(
              "Expected to read the version byte from segment '%s' but nothing was read.",
              fileName));
    } else if (readBytes == -1) {
      throw new CorruptedLogException(
          String.format(
              "Expected to read the version byte from segment '%s' but got EOF instead.",
              fileName));
    }

    return buffer.get(0);
  }

  public void closeReader(final SegmentedJournalReader segmentedJournalReader) {
    readers.remove(segmentedJournalReader);
  }

  /**
   * Resets journal readers to the given index, if they are at a larger index.
   *
   * @param index The index at which to reset readers.
   */
  void resetAdvancedReaders(final long index) {
    for (final SegmentedJournalReader reader : readers) {
      if (reader.getNextIndex() > index) {
        reader.unsafeSeek(index);
      }
    }
  }

  public JournalMetrics getJournalMetrics() {
    return journalMetrics;
  }

  public JournalIndex getJournalIndex() {
    return journalIndex;
  }

  long acquireReadlock() {
    return rwlock.readLock();
  }

  void releaseReadlock(final long stamp) {
    rwlock.unlockRead(stamp);
  }

  private JournalSegment createSegment(final JournalSegmentDescriptor descriptor) {
    final var segmentFile = JournalSegmentFile.createSegmentFile(name, directory, descriptor.id());
    final MappedByteBuffer mappedSegment;

    try {
      mappedSegment = mapNewSegment(segmentFile, descriptor);
    } catch (final IOException e) {
      throw new JournalException(String.format("Failed to map new segment %s", segmentFile), e);
    }

    try {
      descriptor.copyTo(mappedSegment);
      mappedSegment.force();
    } catch (final InternalError e) {
      throw new JournalException(
          String.format(
              "Failed to ensure durability of segment %s with descriptor %s, rolling back",
              segmentFile, descriptor),
          e);
    }

    // while flushing the file's contents ensures its data is present on disk on recovery, it's also
    // necessary to flush the directory to ensure that the file itself is visible as an entry of
    // that directory after recovery
    try {
      FileUtil.flushDirectory(directory.toPath());
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to flush journal directory after creating segment %s", segmentFile),
          e);
    }

    return loadSegment(segmentFile, mappedSegment, descriptor);
  }

  private JournalSegment loadExistingSegment(final File segmentFile) {
    final var descriptor = readDescriptor(segmentFile);
    final MappedByteBuffer mappedSegment;

    try {
      mappedSegment = mapSegment(segmentFile, descriptor, Collections.emptySet());
    } catch (final IOException e) {
      throw new JournalException(
          String.format("Failed to load existing segment %s", segmentFile), e);
    }

    return loadSegment(segmentFile, mappedSegment, descriptor);
  }

  private JournalSegment loadSegment(
      final File file, final MappedByteBuffer buffer, final JournalSegmentDescriptor descriptor) {
    final JournalSegmentFile segmentFile = new JournalSegmentFile(file);
    return new JournalSegment(segmentFile, descriptor, buffer, lastWrittenIndex, journalIndex);
  }

  private MappedByteBuffer mapNewSegment(
      final File segmentFile, final JournalSegmentDescriptor descriptor) throws IOException {
    try {
      return mapSegment(segmentFile, descriptor, Set.of(StandardOpenOption.CREATE_NEW));
    } catch (final FileAlreadyExistsException e) {
      // assuming we haven't written in that segment, just overwrite it; if we have, we may be able
      // reuse, but that's up to the caller
      if (lastWrittenIndex >= descriptor.index()) {
        throw new JournalException(
            String.format(
                "Failed to create journal segment %s, as it already exists, and the last written "
                    + "index %d indicates we've already written to it",
                segmentFile, lastWrittenIndex),
            e);
      }

      return mapSegment(segmentFile, descriptor, Set.of(StandardOpenOption.TRUNCATE_EXISTING));
    }
  }

  private MappedByteBuffer mapSegment(
      final File segmentFile,
      final JournalSegmentDescriptor descriptor,
      final Set<OpenOption> extraOptions)
      throws IOException {
    final var options = new HashSet<>(extraOptions);
    options.add(StandardOpenOption.READ);
    options.add(StandardOpenOption.WRITE);

    try (final var channel = FileChannel.open(segmentFile.toPath(), options)) {
      final var mappedSegment = channel.map(MapMode.READ_WRITE, 0, descriptor.maxSegmentSize());
      mappedSegment.order(ENDIANNESS);

      return mappedSegment;
    }
  }
}
