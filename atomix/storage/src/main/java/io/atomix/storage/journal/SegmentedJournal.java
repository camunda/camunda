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
package io.atomix.storage.journal;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import io.atomix.storage.StorageException;
import io.atomix.storage.StorageLevel;
import io.atomix.storage.journal.index.JournalIndex;
import io.atomix.storage.journal.index.SparseJournalIndex;
import io.atomix.storage.statistics.JournalMetrics;
import io.atomix.utils.serializer.Namespace;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Segmented journal. */
public class SegmentedJournal<E> implements Journal<E> {
  private static final int DEFAULT_INDEX_DENSITY = 200;
  private static final int SEGMENT_BUFFER_FACTOR = 3;
  private final JournalMetrics journalMetrics;
  private final Supplier<JournalIndex> journalIndexFactory;
  private final Logger log = LoggerFactory.getLogger(getClass());
  private final String name;
  private final StorageLevel storageLevel;
  private final File directory;
  private final Namespace namespace;
  private final int maxSegmentSize;
  private final int maxEntrySize;
  private final int maxEntriesPerSegment;
  private final boolean flushOnCommit;
  private final SegmentedJournalWriter<E> writer;
  private volatile long commitIndex;
  private final NavigableMap<Long, JournalSegment<E>> segments = new ConcurrentSkipListMap<>();
  private final Collection<SegmentedJournalReader> readers = Sets.newConcurrentHashSet();
  private volatile JournalSegment<E> currentSegment;
  private volatile boolean open = true;

  public SegmentedJournal(
      final String name,
      final StorageLevel storageLevel,
      final File directory,
      final Namespace namespace,
      final int maxSegmentSize,
      final int maxEntrySize,
      final int maxEntriesPerSegment,
      final boolean flushOnCommit,
      final Supplier<JournalIndex> journalIndexFactory) {
    this.name = checkNotNull(name, "name cannot be null");
    this.storageLevel = checkNotNull(storageLevel, "storageLevel cannot be null");
    this.directory = checkNotNull(directory, "directory cannot be null");
    this.namespace = checkNotNull(namespace, "namespace cannot be null");
    this.maxSegmentSize = maxSegmentSize;
    this.maxEntrySize = maxEntrySize;
    this.maxEntriesPerSegment = maxEntriesPerSegment;
    this.flushOnCommit = flushOnCommit;
    journalMetrics = new JournalMetrics(name);
    this.journalIndexFactory =
        journalIndexFactory == null
            ? () -> new SparseJournalIndex(DEFAULT_INDEX_DENSITY)
            : journalIndexFactory;
    open();
    this.writer = openWriter();
  }

  /**
   * Returns a new Raft log builder.
   *
   * @return A new Raft log builder.
   */
  public static <E> Builder<E> builder() {
    return new Builder<>();
  }

  public JournalMetrics getJournalMetrics() {
    return journalMetrics;
  }

  /**
   * Returns the segment file name prefix.
   *
   * @return The segment file name prefix.
   */
  public String name() {
    return name;
  }

  /**
   * Returns the storage directory.
   *
   * <p>The storage directory is the directory to which all segments write files. Segment files for
   * multiple logs may be stored in the storage directory, and files for each log instance will be
   * identified by the {@code prefix} provided when the log is opened.
   *
   * @return The storage directory.
   */
  public File directory() {
    return directory;
  }

  /**
   * Returns the storage level.
   *
   * <p>The storage level dictates how entries within individual journal segments should be stored.
   *
   * @return The storage level.
   */
  public StorageLevel storageLevel() {
    return storageLevel;
  }

  /**
   * Returns the maximum journal segment size.
   *
   * <p>The maximum segment size dictates the maximum size any segment in a segment may consume in
   * bytes.
   *
   * @return The maximum segment size in bytes.
   */
  public int maxSegmentSize() {
    return maxSegmentSize;
  }

  /**
   * Returns the maximum journal entry size.
   *
   * <p>The maximum entry size dictates the maximum size any entry in the segment may consume in
   * bytes.
   *
   * @return the maximum entry size in bytes
   */
  public int maxEntrySize() {
    return maxEntrySize;
  }

  /**
   * Returns the maximum number of entries per segment.
   *
   * <p>The maximum entries per segment dictates the maximum number of entries that are allowed to
   * be stored in any segment in a journal.
   *
   * @return The maximum number of entries per segment.
   * @deprecated since 3.0.2
   */
  @Deprecated
  public int maxEntriesPerSegment() {
    return maxEntriesPerSegment;
  }

  /**
   * Returns the collection of journal segments.
   *
   * @return the collection of journal segments
   */
  public Collection<JournalSegment<E>> segments() {
    return segments.values();
  }

  /**
   * Returns the collection of journal segments with indexes greater than the given index.
   *
   * @param index the starting index
   * @return the journal segments starting with indexes greater than or equal to the given index
   */
  public Collection<JournalSegment<E>> segments(final long index) {
    return segments.tailMap(index).values();
  }

  /**
   * Returns the total size of the journal.
   *
   * @return the total size of the journal
   */
  public long size() {
    return segments.values().stream().mapToLong(segment -> segment.size()).sum();
  }

  @Override
  public SegmentedJournalWriter<E> writer() {
    return writer;
  }

  @Override
  public SegmentedJournalReader<E> openReader(final long index) {
    return openReader(index, SegmentedJournalReader.Mode.ALL);
  }

  /**
   * Opens a new Raft log reader with the given reader mode.
   *
   * @param index The index from which to begin reading entries.
   * @param mode The mode in which to read entries.
   * @return The Raft log reader.
   */
  public SegmentedJournalReader<E> openReader(
      final long index, final SegmentedJournalReader.Mode mode) {
    final SegmentedJournalReader<E> reader = new SegmentedJournalReader<>(this, index, mode);
    readers.add(reader);
    return reader;
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

  /**
   * Opens a new journal writer.
   *
   * @return A new journal writer.
   */
  protected SegmentedJournalWriter<E> openWriter() {
    return new SegmentedJournalWriter<>(this);
  }

  /** Opens the segments. */
  private synchronized void open() {
    // Load existing log segments from disk.
    for (final JournalSegment<E> segment : loadSegments()) {
      segments.put(segment.descriptor().index(), segment);
    }

    // If a segment doesn't already exist, create an initial segment starting at index 1.
    if (!segments.isEmpty()) {
      currentSegment = segments.lastEntry().getValue();
    } else {
      final JournalSegmentDescriptor descriptor =
          JournalSegmentDescriptor.builder()
              .withId(1)
              .withIndex(1)
              .withMaxSegmentSize(maxSegmentSize)
              .withMaxEntries(maxEntriesPerSegment)
              .build();

      currentSegment = createSegment(descriptor);
      currentSegment.descriptor().update(System.currentTimeMillis());

      segments.put(1L, currentSegment);
    }
  }

  /**
   * Asserts that the manager is open.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  private void assertOpen() {
    checkState(currentSegment != null, "journal not open");
  }

  /** Asserts that enough disk space is available to allocate a new segment. */
  private void assertDiskSpace() {
    if (directory().getUsableSpace() < maxSegmentSize() * SEGMENT_BUFFER_FACTOR) {
      throw new StorageException.OutOfDiskSpace(
          "Not enough space to allocate a new journal segment");
    }
  }

  /** Resets the current segment, creating a new segment if necessary. */
  private synchronized void resetCurrentSegment() {
    final JournalSegment<E> lastSegment = getLastSegment();
    if (lastSegment != null) {
      currentSegment = lastSegment;
    } else {
      final JournalSegmentDescriptor descriptor =
          JournalSegmentDescriptor.builder()
              .withId(1)
              .withIndex(1)
              .withMaxSegmentSize(maxSegmentSize)
              .withMaxEntries(maxEntriesPerSegment)
              .build();

      currentSegment = createSegment(descriptor);

      segments.put(1L, currentSegment);
    }
  }

  /**
   * Resets and returns the first segment in the journal.
   *
   * @param index the starting index of the journal
   * @return the first segment
   */
  JournalSegment<E> resetSegments(final long index) {
    assertOpen();

    // If the index already equals the first segment index, skip the reset.
    final JournalSegment<E> firstSegment = getFirstSegment();
    if (index == firstSegment.index()) {
      return firstSegment;
    }

    for (final JournalSegment<E> segment : segments.values()) {
      segment.close();
      segment.delete();
    }
    segments.clear();

    final JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder()
            .withId(1)
            .withIndex(index)
            .withMaxSegmentSize(maxSegmentSize)
            .withMaxEntries(maxEntriesPerSegment)
            .build();
    currentSegment = createSegment(descriptor);
    segments.put(index, currentSegment);
    return currentSegment;
  }

  /**
   * Returns the first segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  JournalSegment<E> getFirstSegment() {
    assertOpen();
    final Map.Entry<Long, JournalSegment<E>> segment = segments.firstEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Returns the last segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  JournalSegment<E> getLastSegment() {
    assertOpen();
    final Map.Entry<Long, JournalSegment<E>> segment = segments.lastEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Creates and returns the next segment.
   *
   * @return The next segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  synchronized JournalSegment<E> getNextSegment() {
    assertOpen();
    assertDiskSpace();

    final JournalSegment lastSegment = getLastSegment();
    final JournalSegmentDescriptor descriptor =
        JournalSegmentDescriptor.builder()
            .withId(lastSegment != null ? lastSegment.descriptor().id() + 1 : 1)
            .withIndex(currentSegment.lastIndex() + 1)
            .withMaxSegmentSize(maxSegmentSize)
            .withMaxEntries(maxEntriesPerSegment)
            .build();

    currentSegment = createSegment(descriptor);

    segments.put(descriptor.index(), currentSegment);
    return currentSegment;
  }

  /**
   * Returns the segment following the segment with the given ID.
   *
   * @param index The segment index with which to look up the next segment.
   * @return The next segment for the given index.
   */
  JournalSegment<E> getNextSegment(final long index) {
    final Map.Entry<Long, JournalSegment<E>> nextSegment = segments.higherEntry(index);
    return nextSegment != null ? nextSegment.getValue() : null;
  }

  /**
   * Returns the segment for the given index.
   *
   * @param index The index for which to return the segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  synchronized JournalSegment<E> getSegment(final long index) {
    assertOpen();
    // Check if the current segment contains the given index first in order to prevent an
    // unnecessary map lookup.
    if (currentSegment != null && index > currentSegment.index()) {
      return currentSegment;
    }

    // If the index is in another segment, get the entry with the next lowest first index.
    final Map.Entry<Long, JournalSegment<E>> segment = segments.floorEntry(index);
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
    segment.close();
    segment.delete();
    resetCurrentSegment();
  }

  /** Creates a new segment. */
  JournalSegment<E> createSegment(final JournalSegmentDescriptor descriptor) {
    final File segmentFile = JournalSegmentFile.createSegmentFile(name, directory, descriptor.id());

    final RandomAccessFile raf;
    final FileChannel channel;
    try {
      raf = new RandomAccessFile(segmentFile, "rw");
      raf.setLength(descriptor.maxSegmentSize());
      channel = raf.getChannel();
    } catch (final IOException e) {
      throw new StorageException(e);
    }

    final ByteBuffer buffer = ByteBuffer.allocate(JournalSegmentDescriptor.BYTES);
    descriptor.copyTo(buffer);
    buffer.flip();
    try {
      channel.write(buffer);
    } catch (final IOException e) {
      throw new StorageException(e);
    } finally {
      try {
        channel.close();
        raf.close();
      } catch (final IOException e) {
        log.warn("Unexpected IOException on closing", e);
      }
    }
    final JournalSegment<E> segment = newSegment(new JournalSegmentFile(segmentFile), descriptor);
    log.debug("Created segment: {}", segment);
    return segment;
  }

  /**
   * Creates a new segment instance.
   *
   * @param segmentFile The segment file.
   * @param descriptor The segment descriptor.
   * @return The segment instance.
   */
  protected JournalSegment<E> newSegment(
      final JournalSegmentFile segmentFile, final JournalSegmentDescriptor descriptor) {
    return new JournalSegment<>(
        segmentFile, descriptor, storageLevel, maxEntrySize, namespace, journalIndexFactory.get());
  }

  /** Loads a segment. */
  private JournalSegment<E> loadSegment(final long segmentId) {
    final File segmentFile = JournalSegmentFile.createSegmentFile(name, directory, segmentId);
    final ByteBuffer buffer = ByteBuffer.allocate(JournalSegmentDescriptor.BYTES);
    try (final FileChannel channel = openChannel(segmentFile)) {
      channel.read(buffer);
      buffer.flip();
      final JournalSegmentDescriptor descriptor = new JournalSegmentDescriptor(buffer);
      final JournalSegment<E> segment = newSegment(new JournalSegmentFile(segmentFile), descriptor);
      log.debug("Loaded disk segment: {} ({})", descriptor.id(), segmentFile.getName());
      return segment;
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  private FileChannel openChannel(final File file) {
    try {
      return FileChannel.open(
          file.toPath(),
          StandardOpenOption.CREATE,
          StandardOpenOption.READ,
          StandardOpenOption.WRITE);
    } catch (final IOException e) {
      throw new StorageException(e);
    }
  }

  /**
   * Loads all segments from disk.
   *
   * @return A collection of segments for the log.
   */
  protected Collection<JournalSegment<E>> loadSegments() {
    // Ensure log directories are created.
    directory.mkdirs();

    final TreeMap<Long, JournalSegment<E>> segments = new TreeMap<>();

    // Iterate through all files in the log directory.
    for (final File file : directory.listFiles(File::isFile)) {

      // If the file looks like a segment file, attempt to load the segment.
      if (JournalSegmentFile.isSegmentFile(name, file)) {
        final JournalSegmentFile segmentFile = new JournalSegmentFile(file);
        final ByteBuffer buffer = ByteBuffer.allocate(JournalSegmentDescriptor.BYTES);
        try (final FileChannel channel = openChannel(file)) {
          channel.read(buffer);
          buffer.flip();
        } catch (final IOException e) {
          throw new StorageException(e);
        }

        final JournalSegmentDescriptor descriptor = new JournalSegmentDescriptor(buffer);

        // Load the segment.
        final JournalSegment<E> segment = loadSegment(descriptor.id());

        // Add the segment to the segments list.
        log.debug(
            "Found segment: {} ({})", segment.descriptor().id(), segmentFile.file().getName());
        segments.put(segment.index(), segment);
      }
    }

    // Verify that all the segments in the log align with one another.
    JournalSegment<E> previousSegment = null;
    boolean corrupted = false;
    final Iterator<Map.Entry<Long, JournalSegment<E>>> iterator = segments.entrySet().iterator();
    while (iterator.hasNext()) {
      final JournalSegment<E> segment = iterator.next().getValue();
      if (previousSegment != null && previousSegment.lastIndex() != segment.index() - 1) {
        log.warn(
            "Journal is inconsistent. {} is not aligned with prior segment {}",
            segment.file().file(),
            previousSegment.file().file());
        corrupted = true;
      }
      if (corrupted) {
        segment.close();
        segment.delete();
        iterator.remove();
      }
      previousSegment = segment;
    }

    return segments.values();
  }

  /**
   * Resets journal readers to the given head.
   *
   * @param index The index at which to reset readers.
   */
  void resetHead(final long index) {
    for (final SegmentedJournalReader reader : readers) {
      if (reader.getNextIndex() < index) {
        reader.reset(index);
      }
    }
  }

  /**
   * Resets journal readers to the given tail.
   *
   * @param index The index at which to reset readers.
   */
  void resetTail(final long index) {
    for (final SegmentedJournalReader reader : readers) {
      if (reader.getNextIndex() >= index) {
        reader.reset(index);
      } else {
        // This is not thread safe https://github.com/zeebe-io/zeebe/issues/4198
        reader.reset(reader.getNextIndex());
      }
    }
  }

  void closeReader(final SegmentedJournalReader reader) {
    readers.remove(reader);
  }

  /**
   * Returns a boolean indicating whether a segment can be removed from the journal prior to the
   * given index.
   *
   * @param index the index from which to remove segments
   * @return indicates whether a segment can be removed from the journal
   */
  public boolean isCompactable(final long index) {
    final Map.Entry<Long, JournalSegment<E>> segmentEntry = segments.floorEntry(index);
    return segmentEntry != null && segments.headMap(segmentEntry.getValue().index()).size() > 0;
  }

  /**
   * Returns the index of the last segment in the log.
   *
   * @param index the compaction index
   * @return the starting index of the last segment in the log
   */
  public long getCompactableIndex(final long index) {
    final Map.Entry<Long, JournalSegment<E>> segmentEntry = segments.floorEntry(index);
    return segmentEntry != null ? segmentEntry.getValue().index() : 0;
  }

  /**
   * Compacts the journal up to the given index.
   *
   * <p>The semantics of compaction are not specified by this interface.
   *
   * @param index The index up to which to compact the journal.
   */
  public void compact(final long index) {
    final Map.Entry<Long, JournalSegment<E>> segmentEntry = segments.floorEntry(index);
    if (segmentEntry != null) {
      final SortedMap<Long, JournalSegment<E>> compactSegments =
          segments.headMap(segmentEntry.getValue().index());
      if (!compactSegments.isEmpty()) {
        log.debug("{} - Compacting {} segment(s)", name, compactSegments.size());
        for (final JournalSegment segment : compactSegments.values()) {
          log.trace("Deleting segment: {}", segment);
          segment.compactIndex(index);
          segment.close();
          segment.delete();
        }
        compactSegments.clear();
        resetHead(segmentEntry.getValue().index());
      }
    }
  }

  /**
   * Returns whether {@code flushOnCommit} is enabled for the log.
   *
   * @return Indicates whether {@code flushOnCommit} is enabled for the log.
   */
  boolean isFlushOnCommit() {
    return flushOnCommit;
  }

  /**
   * Returns the Raft log commit index.
   *
   * @return The Raft log commit index.
   */
  long getCommitIndex() {
    return commitIndex;
  }

  /**
   * Commits entries up to the given index.
   *
   * @param index The index up to which to commit entries.
   */
  void setCommitIndex(final long index) {
    this.commitIndex = index;
  }

  /** Raft log builder. */
  public static class Builder<E> implements io.atomix.utils.Builder<SegmentedJournal<E>> {

    private static final boolean DEFAULT_FLUSH_ON_COMMIT = false;
    private static final String DEFAULT_NAME = "atomix";
    private static final String DEFAULT_DIRECTORY = System.getProperty("user.dir");
    private static final int DEFAULT_MAX_SEGMENT_SIZE = 1024 * 1024 * 32;
    private static final int DEFAULT_MAX_ENTRY_SIZE = 1024 * 1024;
    private static final int DEFAULT_MAX_ENTRIES_PER_SEGMENT = 1024 * 1024;

    protected String name = DEFAULT_NAME;
    protected StorageLevel storageLevel = StorageLevel.DISK;
    protected File directory = new File(DEFAULT_DIRECTORY);
    protected Namespace namespace;
    protected int maxSegmentSize = DEFAULT_MAX_SEGMENT_SIZE;
    protected int maxEntrySize = DEFAULT_MAX_ENTRY_SIZE;
    protected int maxEntriesPerSegment = DEFAULT_MAX_ENTRIES_PER_SEGMENT;

    private boolean flushOnCommit = DEFAULT_FLUSH_ON_COMMIT;
    private Supplier<JournalIndex> journalIndexFactory;

    protected Builder() {}

    /**
     * Sets the storage name.
     *
     * @param name The storage name.
     * @return The storage builder.
     */
    public Builder<E> withName(final String name) {
      this.name = checkNotNull(name, "name cannot be null");
      return this;
    }

    /**
     * Sets the log storage level, returning the builder for method chaining.
     *
     * <p>The storage level indicates how individual entries should be persisted in the journal.
     *
     * @param storageLevel The log storage level.
     * @return The storage builder.
     */
    public Builder<E> withStorageLevel(final StorageLevel storageLevel) {
      this.storageLevel = checkNotNull(storageLevel, "storageLevel cannot be null");
      return this;
    }

    /**
     * Sets the log directory, returning the builder for method chaining.
     *
     * <p>The log will write segment files into the provided directory.
     *
     * @param directory The log directory.
     * @return The storage builder.
     * @throws NullPointerException If the {@code directory} is {@code null}
     */
    public Builder<E> withDirectory(final String directory) {
      return withDirectory(new File(checkNotNull(directory, "directory cannot be null")));
    }

    /**
     * Sets the log directory, returning the builder for method chaining.
     *
     * <p>The log will write segment files into the provided directory.
     *
     * @param directory The log directory.
     * @return The storage builder.
     * @throws NullPointerException If the {@code directory} is {@code null}
     */
    public Builder<E> withDirectory(final File directory) {
      this.directory = checkNotNull(directory, "directory cannot be null");
      return this;
    }

    /**
     * Sets the journal namespace, returning the builder for method chaining.
     *
     * @param namespace The journal serializer.
     * @return The journal builder.
     */
    public Builder<E> withNamespace(final Namespace namespace) {
      this.namespace = checkNotNull(namespace, "namespace cannot be null");
      return this;
    }

    /**
     * Sets the maximum segment size in bytes, returning the builder for method chaining.
     *
     * <p>The maximum segment size dictates when logs should roll over to new segments. As entries
     * are written to a segment of the log, once the size of the segment surpasses the configured
     * maximum segment size, the log will create a new segment and append new entries to that
     * segment.
     *
     * <p>By default, the maximum segment size is {@code 1024 * 1024 * 32}.
     *
     * @param maxSegmentSize The maximum segment size in bytes.
     * @return The storage builder.
     * @throws IllegalArgumentException If the {@code maxSegmentSize} is not positive
     */
    public Builder<E> withMaxSegmentSize(final int maxSegmentSize) {
      checkArgument(
          maxSegmentSize > JournalSegmentDescriptor.BYTES,
          "maxSegmentSize must be greater than " + JournalSegmentDescriptor.BYTES);
      this.maxSegmentSize = maxSegmentSize;
      return this;
    }

    /**
     * Sets the maximum entry size in bytes, returning the builder for method chaining.
     *
     * @param maxEntrySize the maximum entry size in bytes
     * @return the storage builder
     * @throws IllegalArgumentException if the {@code maxEntrySize} is not positive
     */
    public Builder<E> withMaxEntrySize(final int maxEntrySize) {
      checkArgument(maxEntrySize > 0, "maxEntrySize must be positive");
      this.maxEntrySize = maxEntrySize;
      return this;
    }

    /**
     * Sets the maximum number of allows entries per segment, returning the builder for method
     * chaining.
     *
     * <p>The maximum entry count dictates when logs should roll over to new segments. As entries
     * are written to a segment of the log, if the entry count in that segment meets the configured
     * maximum entry count, the log will create a new segment and append new entries to that
     * segment.
     *
     * <p>By default, the maximum entries per segment is {@code 1024 * 1024}.
     *
     * @param maxEntriesPerSegment The maximum number of entries allowed per segment.
     * @return The storage builder.
     * @throws IllegalArgumentException If the {@code maxEntriesPerSegment} not greater than the
     *     default max entries per segment
     * @deprecated since 3.0.2
     */
    @Deprecated
    public Builder<E> withMaxEntriesPerSegment(final int maxEntriesPerSegment) {
      checkArgument(maxEntriesPerSegment > 0, "max entries per segment must be positive");
      checkArgument(
          maxEntriesPerSegment <= DEFAULT_MAX_ENTRIES_PER_SEGMENT,
          "max entries per segment cannot be greater than " + DEFAULT_MAX_ENTRIES_PER_SEGMENT);
      this.maxEntriesPerSegment = maxEntriesPerSegment;
      return this;
    }

    /**
     * Enables flushing buffers to disk when entries are committed to a segment, returning the
     * builder for method chaining.
     *
     * <p>When flush-on-commit is enabled, log entry buffers will be automatically flushed to disk
     * each time an entry is committed in a given segment.
     *
     * @return The storage builder.
     */
    public Builder<E> withFlushOnCommit() {
      return withFlushOnCommit(true);
    }

    /**
     * Sets whether to flush buffers to disk when entries are committed to a segment, returning the
     * builder for method chaining.
     *
     * <p>When flush-on-commit is enabled, log entry buffers will be automatically flushed to disk
     * each time an entry is committed in a given segment.
     *
     * @param flushOnCommit Whether to flush buffers to disk when entries are committed to a
     *     segment.
     * @return The storage builder.
     */
    public Builder<E> withFlushOnCommit(final boolean flushOnCommit) {
      this.flushOnCommit = flushOnCommit;
      return this;
    }

    public Builder<E> withJournalIndexFactory(final Supplier<JournalIndex> journalIndexFactory) {
      this.journalIndexFactory = journalIndexFactory;
      return this;
    }

    @Override
    public SegmentedJournal<E> build() {
      return new SegmentedJournal<>(
          name,
          storageLevel,
          directory,
          namespace,
          maxSegmentSize,
          maxEntrySize,
          maxEntriesPerSegment,
          flushOnCommit,
          journalIndexFactory);
    }
  }
}
