/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkNotNull;

import io.camunda.zeebe.journal.CorruptedJournalException;
import io.camunda.zeebe.journal.JournalException;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Create new segments. Load existing segments from the disk. Keep track of all segments. */
final class SegmentsManager implements AutoCloseable {

  private static final long FIRST_SEGMENT_ID = 1;
  private static final long INITIAL_INDEX = 1;
  private static final long INITIAL_ASQN = SegmentedJournal.ASQN_IGNORE;

  private static final Logger LOG = LoggerFactory.getLogger(SegmentsManager.class);
  private static final Logger THROTTLED_LOG = new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final NavigableMap<Long, Segment> segments = new ConcurrentSkipListMap<>();
  private CompletableFuture<UninitializedSegment> nextSegment = null;

  private final JournalMetrics journalMetrics;
  private final JournalIndex journalIndex;
  private final int maxSegmentSize;
  private final File directory;
  private final SegmentLoader segmentLoader;
  private final String name;
  private final JournalMetaStore metaStore;

  private volatile Segment currentSegment;

  SegmentsManager(
      final JournalIndex journalIndex,
      final int maxSegmentSize,
      final File directory,
      final String name,
      final SegmentLoader segmentLoader,
      final JournalMetrics journalMetrics,
      final JournalMetaStore metaStore) {
    this.name = checkNotNull(name, "name cannot be null");
    this.journalIndex = journalIndex;
    this.maxSegmentSize = maxSegmentSize;
    this.directory = directory;
    this.segmentLoader = segmentLoader;
    this.journalMetrics = journalMetrics;
    this.metaStore = metaStore;
  }

  @Override
  public void close() {
    segments
        .values()
        .forEach(
            segment -> {
              LOG.debug("Closing segment: {}", segment);
              segment.close();
            });

    if (nextSegment != null) {
      try {
        nextSegment.join();
      } catch (final Exception e) {
        LOG.warn(
            "Next segment preparation failed during close, ignoring and proceeding to close", e);
      }
      nextSegment = null;
    }

    currentSegment = null;
  }

  Segment getCurrentSegment() {
    return currentSegment;
  }

  Segment getFirstSegment() {
    final Map.Entry<Long, Segment> segment = segments.firstEntry();
    return segment != null ? segment.getValue() : null;
  }

  Segment getLastSegment() {
    final Map.Entry<Long, Segment> segment = segments.lastEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Creates and returns the next segment.
   *
   * @return The next segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  Segment getNextSegment() {

    final Segment lastSegment = getLastSegment();
    final var lastWrittenAsqn = lastSegment != null ? lastSegment.lastAsqn() : INITIAL_ASQN;
    final var nextSegmentIndex = currentSegment.lastIndex() + 1;
    final SegmentDescriptor descriptor =
        SegmentDescriptor.builder()
            .withId(lastSegment != null ? lastSegment.descriptor().id() + 1 : 1)
            .withIndex(nextSegmentIndex)
            .withMaxSegmentSize(maxSegmentSize)
            .build();
    if (nextSegment != null) {
      try {
        currentSegment =
            nextSegment.join().initializeForUse(nextSegmentIndex, lastWrittenAsqn, journalMetrics);
      } catch (final CompletionException e) {
        LOG.error("Failed to acquire next segment, retrying synchronously now.", e);
        nextSegment = null;
        currentSegment = createSegment(descriptor, lastWrittenAsqn);
      }
    } else {
      currentSegment = createSegment(descriptor, lastWrittenAsqn);
    }
    prepareNextSegment();

    segments.put(descriptor.index(), currentSegment);
    journalMetrics.incSegmentCount();
    return currentSegment;
  }

  Segment getNextSegment(final long index) {
    final Map.Entry<Long, Segment> nextSegment = segments.higherEntry(index);
    return nextSegment != null ? nextSegment.getValue() : null;
  }

  Segment getSegment(final long index) {
    // Check if the current segment contains the given index first in order to prevent an
    // unnecessary map lookup.
    if (currentSegment != null && index > currentSegment.index()) {
      return currentSegment;
    }

    // If the index is in another segment, get the entry with the next lowest first index.
    final Map.Entry<Long, Segment> segment = segments.floorEntry(index);
    if (segment != null) {
      return segment.getValue();
    }
    return getFirstSegment();
  }

  private long getFirstIndex() {
    final var firstSegment = getFirstSegment();
    return firstSegment != null ? firstSegment.index() : 0;
  }

  boolean deleteUntil(final long index) {
    final Map.Entry<Long, Segment> segmentEntry = segments.floorEntry(index);
    if (segmentEntry == null) {
      return false;
    }

    final SortedMap<Long, Segment> compactSegments =
        segments.headMap(segmentEntry.getValue().index());
    if (compactSegments.isEmpty()) {
      THROTTLED_LOG.debug(
          "No segments can be deleted with index < {} (first log index: {})",
          index,
          getFirstIndex());
      return false;
    }

    LOG.debug(
        "{} - Deleting log up from {} up to {} (removing {} segments)",
        name,
        getFirstIndex(),
        compactSegments.get(compactSegments.lastKey()).index(),
        compactSegments.size());
    for (final Segment segment : compactSegments.values()) {
      LOG.trace("{} - Deleting segment: {}", name, segment);
      segment.delete();
      journalMetrics.decSegmentCount();
    }

    // removes them from the segment map
    compactSegments.clear();

    journalIndex.deleteUntil(index);

    return true;
  }

  /**
   * Resets and returns the first segment in the journal.
   *
   * @param index the starting index of the journal
   * @return the first segment
   */
  Segment resetSegments(final long index) {
    // reset the last flushed index before deleting data to avoid data corruption on start up in
    // case of node crash
    // setting the last flushed index to a semantic-null value will let us know on start up that
    // there is "nothing" written, even if we cannot read the descriptor (e.g. if we crash after
    // creating the segment but before writing its descriptor)
    metaStore.resetLastFlushedIndex();

    // delete the segments in reverse order, such that if the operation is interrupted (e.g. crash)
    // in the middle, there are no gaps in the log (or between the log and snapshot)
    final Iterator<Segment> it = segments.descendingMap().values().iterator();
    while (it.hasNext()) {
      // we explicitly do not want to close the segment, as we may be only soft deleting it here to
      // allow readers to finish what they're doing and avoid a race condition with unmapping the
      // underlying buffer
      //noinspection resource
      final var segment = it.next();
      segment.delete();
      it.remove();
      journalMetrics.decSegmentCount();
    }

    final SegmentDescriptor descriptor =
        SegmentDescriptor.builder()
            .withId(1)
            .withIndex(index)
            .withMaxSegmentSize(maxSegmentSize)
            .build();
    currentSegment = createSegment(descriptor, INITIAL_ASQN);
    segments.put(index, currentSegment);
    journalMetrics.incSegmentCount();
    return currentSegment;
  }

  /**
   * Removes a segment.
   *
   * @param segment The segment to remove.
   */
  void removeSegment(final Segment segment) {
    //noinspection resource
    segments.remove(segment.index());
    journalMetrics.decSegmentCount();
    segment.delete();
    resetCurrentSegment();
  }

  /** Resets the current segment, creating a new segment if necessary. */
  private void resetCurrentSegment() {
    final Segment lastSegment = getLastSegment();
    if (lastSegment != null) {
      currentSegment = lastSegment;
    } else {
      final SegmentDescriptor descriptor =
          SegmentDescriptor.builder()
              .withId(FIRST_SEGMENT_ID)
              .withIndex(INITIAL_INDEX)
              .withMaxSegmentSize(maxSegmentSize)
              .build();

      currentSegment = createSegment(descriptor, INITIAL_ASQN);

      segments.put(1L, currentSegment);
      journalMetrics.incSegmentCount();
    }
  }

  /** Loads existing segments from the disk */
  void open() {
    final var openDurationTimer = journalMetrics.startJournalOpenDurationTimer();
    // Load existing log segments from disk.
    for (final Segment segment : loadSegments()) {
      segments.put(segment.descriptor().index(), segment);
      journalMetrics.incSegmentCount();
    }

    // If a segment doesn't already exist, create an initial segment starting at index 1.
    if (!segments.isEmpty()) {
      currentSegment = segments.lastEntry().getValue();
    } else {
      final SegmentDescriptor descriptor =
          SegmentDescriptor.builder()
              .withId(FIRST_SEGMENT_ID)
              .withIndex(INITIAL_INDEX)
              .withMaxSegmentSize(maxSegmentSize)
              .build();

      currentSegment = createSegment(descriptor, INITIAL_ASQN);

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

  private void prepareNextSegment() {
    final var descriptor =
        SegmentDescriptor.builder()
            .withId(currentSegment.id() + 1)
            .withIndex(INITIAL_INDEX)
            .withMaxSegmentSize(maxSegmentSize)
            .build();
    nextSegment = CompletableFuture.supplyAsync(() -> createUninitializedSegment(descriptor));
  }

  SortedMap<Long, Segment> getTailSegments(final long index) {
    final var segment = getSegment(index);
    if (segment == null) {
      return Collections.emptySortedMap();
    }

    return Collections.unmodifiableSortedMap(segments.tailMap(segment.index(), true)); // inclusive
  }

  private UninitializedSegment createUninitializedSegment(final SegmentDescriptor descriptor) {
    final var segmentFile = SegmentFile.createSegmentFile(name, directory, descriptor.id());
    return segmentLoader.createUninitializedSegment(segmentFile.toPath(), descriptor, journalIndex);
  }

  private Segment createSegment(final SegmentDescriptor descriptor, final long lastWrittenAsqn) {
    final var segmentFile = SegmentFile.createSegmentFile(name, directory, descriptor.id());
    return segmentLoader.createSegment(
        segmentFile.toPath(), descriptor, lastWrittenAsqn, journalIndex);
  }

  /**
   * Loads all segments from disk.
   *
   * @return A collection of segments for the log.
   */
  private Collection<Segment> loadSegments() {
    final var lastFlushedIndex = metaStore.loadLastFlushedIndex();

    // Ensure log directories are created.
    //noinspection ResultOfMethodCallIgnored
    directory.mkdirs();
    final List<Segment> segments = new ArrayList<>();

    final List<File> files = getSortedLogSegments();
    Segment previousSegment = null;
    for (int i = 0; i < files.size(); i++) {
      final File file = files.get(i);

      try {
        LOG.debug("Found segment file: {}", file.getName());
        final Segment segment =
            segmentLoader.loadExistingSegment(
                file.toPath(),
                previousSegment != null ? previousSegment.lastAsqn() : INITIAL_ASQN,
                journalIndex);

        if (i > 0) {
          // throws CorruptedJournalException if there is gap
          checkForIndexGaps(segments.get(i - 1), segment);
        }

        final boolean isLastSegment = i == files.size() - 1;
        if (isLastSegment && segment.lastIndex() < lastFlushedIndex) {
          throw new CorruptedJournalException(
              "Expected to find records until index %d, but last index is %d"
                  .formatted(lastFlushedIndex, segment.lastIndex()));
        }

        segments.add(segment);
        previousSegment = segment;
      } catch (final CorruptedJournalException e) {
        if (handleSegmentCorruption(files, segments, i, lastFlushedIndex)) {
          return segments;
        }

        throw e;
      }
    }

    return segments;
  }

  private void checkForIndexGaps(final Segment prevSegment, final Segment segment) {
    if (prevSegment.lastIndex() != segment.index() - 1) {
      throw new CorruptedJournalException(
          String.format(
              "Log segment %s is not aligned with previous segment %s (last index: %d).",
              segment, prevSegment, prevSegment.lastIndex()));
    }
  }

  /** Returns true if segments after corrupted segment were deleted; false, otherwise */
  private boolean handleSegmentCorruption(
      final List<File> files,
      final List<Segment> segments,
      final int failedIndex,
      final long lastFlushedIndex) {
    // if we've never flushed anything, then we can simply go head and delete the segment; otherwise
    // fail if we've already flushed the failing index
    if (metaStore.hasLastFlushedIndex()) {
      long lastSegmentIndex = 0;

      if (!segments.isEmpty()) {
        final Segment previousSegment = segments.get(segments.size() - 1);
        lastSegmentIndex = previousSegment.lastIndex();
      }

      if (lastFlushedIndex > lastSegmentIndex) {
        return false;
      }
    }

    deleteUnflushedSegments(files, failedIndex, lastFlushedIndex);
    return true;
  }

  private void deleteUnflushedSegments(
      final List<File> files, final int failedIndex, final long lastFlushedIndex) {
    LOG.debug(
        "Found corrupted segment after last ack'ed index {}. Deleting segments {} - {}",
        lastFlushedIndex,
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
  }

  /** Returns an array of valid log segments sorted by their id which may be empty but not null. */
  private List<File> getSortedLogSegments() {
    final File[] files =
        directory.listFiles(file -> file.isFile() && SegmentFile.isSegmentFile(name, file));

    if (files == null) {
      throw new IllegalStateException(
          String.format(
              "Could not list files in directory '%s'. Either the path doesn't point to a directory or an I/O error occurred.",
              directory));
    }

    Arrays.sort(files, Comparator.comparingInt(f -> SegmentFile.getSegmentIdFromPath(f.getName())));

    return Arrays.asList(files);
  }

  private void deleteDeferredFiles() {
    try (final DirectoryStream<Path> segmentsToDelete =
        Files.newDirectoryStream(
            directory.toPath(),
            path -> SegmentFile.isDeletedSegmentFile(name, path.getFileName().toString()))) {
      segmentsToDelete.forEach(this::deleteDeferredFile);
    } catch (final IOException e) {
      LOG.warn(
          "Could not delete segment files marked for deletion in {}. This can result in unnecessary disk usage.",
          directory.toPath(),
          e);
    }
  }

  private void deleteDeferredFile(final Path segmentFileToDelete) {
    try {
      Files.deleteIfExists(segmentFileToDelete);
    } catch (final IOException e) {
      LOG.warn(
          "Could not delete file {} which is marked for deletion. This can result in unnecessary disk usage.",
          segmentFileToDelete,
          e);
    }
  }
}
