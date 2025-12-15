/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.index;

import io.camunda.zeebe.backup.api.BackupIndex;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.agrona.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A compact backup index that stores backup entries in a memory-mapped file. The index is sorted by
 * checkpoint id to allow efficient searching.
 *
 * <p>The index file format is as follows:
 *
 * <ul>
 *   <li>Header:
 *       <ul>
 *         <li>4 bytes: version (int)
 *         <li>4 bytes: number of entries (int)
 *       </ul>
 *   <li>Entries (each entry is 24 bytes):
 *       <ul>
 *         <li>8 bytes: checkpoint id (long)
 *         <li>8 bytes: first log position (long)
 *         <li>8 bytes: last log position (long)
 *       </ul>
 * </ul>
 *
 * The stored entries can be read via the {@link #all()} method which returns a stream of all
 * entries.
 *
 * <p><b>Concurrent modifications</b> are not supported. The index should be modified by a single
 * writer at a time. Readers can read concurrently while modifications are being made, but they will
 * only see a consistent snapshot of the index as of the time they called {@link #all()}.
 *
 * @apiNote Changes to the index are not automatically flushed to disk. Users should call {@link
 *     #flush()} to ensure changes are persisted. Changes are automatically flushed on {@link
 *     #close()}.
 * @implNote Appending a new entry to the end of the index is efficient. Inserting entries in the
 *     middle of the index or removing entries requires rewriting the index file, which is done in a
 *     crash-safe manner using a temporary file. The index file is preallocated to avoid frequent
 *     remapping when adding new entries.
 * @see BackupIndex
 */
public final class CompactBackupIndex implements BackupIndex, AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(CompactBackupIndex.class);

  /**
   * Number of entries to preallocate space for when creating the initial mapping. This avoids
   * frequent remapping when adding new entries.
   */
  private static final int PREALLOCATED_ENTRIES = 1024;

  private static final int HEADER_SIZE =
      Integer.BYTES // version
          + Integer.BYTES; // entries;
  private static final int ENTRY_SIZE =
      Long.BYTES // checkpointId
          + Long.BYTES // firstLogPosition
          + Long.BYTES; // lastLogPosition

  private static final int VERSION = 1;

  private final Path path;

  /** The file channel for the index file. Might be replaced when rewriting the index. */
  private FileChannel file;

  /** The memory-mapped buffer for the index file. Might be replaced when remapping or rewriting */
  private MappedByteBuffer buffer;

  private CompactBackupIndex(final Path path, final FileChannel file) {
    this.path = path;
    this.file = file;
    createInitialMapping(file);
  }

  public static CompactBackupIndex open(final Path path)
      throws IOException, IndexCorruption, PartialIndexCorruption {
    final var index =
        new CompactBackupIndex(
            path,
            FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE));
    index.validate();
    return index;
  }

  public static CompactBackupIndex create(final Path path) throws IOException {
    final var index =
        new CompactBackupIndex(
            path,
            FileChannel.open(
                path,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW));
    index.buffer.limit(HEADER_SIZE + index.getEntries() * ENTRY_SIZE);
    return index;
  }

  public void flush() {
    buffer.force();
  }

  /**
   * Returns a stream of all backup entries in the index. The stream supports parallel processing
   * and reflects the state of the index at the time of calling this method. Concurrent
   * modifications to the index done through {@link #add(IndexedBackup)} and {@link #remove(long)}
   * are not reflected in the returned stream.
   */
  @Override
  public Stream<IndexedBackup> all() {
    final var spliterator = new EntrySpliterator(buffer.duplicate().position(HEADER_SIZE));
    return StreamSupport.stream(spliterator, true);
  }

  /**
   * Adds a new backup entry to the index. The index is sorted so the entry will be written at the
   * correct position.
   */
  @Override
  public void add(final IndexedBackup newEntry) {
    final var existingEntry = byCheckpointId(newEntry.checkpointId());
    if (existingEntry != null) {
      return;
    }
    remapIfNeeded();
    if (buffer.position() == buffer.limit()) {
      appendNewEntry(newEntry);
    } else {
      spliceNewEntry(newEntry);
    }
  }

  @Override
  public void remove(final long checkpointId) {
    final var existingEntry = byCheckpointId(checkpointId);
    if (existingEntry == null) {
      return;
    }
    if (buffer.position() == buffer.limit()) {
      removeLastEntry();
    } else {
      removeMiddleEntry();
    }
  }

  /**
   * Validates the index file format and checks for corruption. Does not hold any locks, because it
   * is only called during initialization.
   *
   * @throws IndexCorruption if the index is corrupted and cannot be used
   * @throws PartialIndexCorruption if the index is partially corrupted, but some entries can be
   *     recovered.
   */
  private void validate() throws IndexCorruption, PartialIndexCorruption {
    final var version = getVersion();
    if (version != VERSION) {
      throw new IndexCorruption("Unsupported backup index version: " + version);
    }

    final var entries = getEntries();
    if (entries < 0) {
      throw new IllegalStateException(
          "Corrupt backup index: negative number of entries: " + entries);
    }
    if (buffer.capacity() < HEADER_SIZE + entries * ENTRY_SIZE) {
      throw new IndexCorruption(
          "Corrupt backup index: expected size for "
              + entries
              + " entries, but file is too small: "
              + buffer.capacity());
    }
    seek(entries - 1);
    final IndexedBackup lastValidEntry;
    if (entries > 0) {
      lastValidEntry = readEntry();
    } else {
      lastValidEntry = null;
    }
    buffer.position(HEADER_SIZE + (entries) * ENTRY_SIZE);
    // Check that remaining bytes are zeroed out
    final var chunkSize = 1024;
    //noinspection MismatchedReadAndWriteOfArray, only used for comparison
    final var zeros = new byte[chunkSize];
    final var chunk = new byte[chunkSize];
    while (buffer.remaining() >= chunkSize) {
      buffer.get(chunk);
      final var mismatch = Arrays.mismatch(chunk, zeros);
      if (mismatch != -1) {
        throw new PartialIndexCorruption(
            "Corrupt backup index: non-zero byte %s found at position %d"
                .formatted(
                    String.format("0x%02X", chunk[mismatch]),
                    buffer.position() - chunkSize + mismatch),
            lastValidEntry);
      }
    }
    // Check the remaining bytes one by one
    while (buffer.hasRemaining()) {
      if (buffer.get() != 0) {
        throw new PartialIndexCorruption(
            "Corrupt backup index: non-zero bytes found at %d after last valid entry"
                .formatted(buffer.position()),
            lastValidEntry);
      }
    }
    buffer.limit(HEADER_SIZE + getEntries() * ENTRY_SIZE);

    if (Files.exists(resolveTemporaryIndexCopy())) {
      LOG.warn(
          "Found temporary backup index copy file. This indicates that a previous modification of the backup index did not complete successfully. The temporary file will be deleted.");
      try {
        Files.delete(resolveTemporaryIndexCopy());
      } catch (final IOException e) {
        LOG.error(
            "Failed to delete temporary backup index copy file at {}",
            resolveTemporaryIndexCopy(),
            e);
      }
    }
  }

  private int getVersion() {
    return buffer.getInt(0);
  }

  private void setVersion() {
    buffer.putInt(0, VERSION);
  }

  private int getEntries() {
    return buffer.getInt(Integer.BYTES);
  }

  private void setEntries(final int entries) {
    buffer.putInt(Integer.BYTES, entries);
  }

  private void createInitialMapping(final FileChannel file) {
    try {
      // Preallocate for at least 1024 entries
      final var minCapacity = PREALLOCATED_ENTRIES * ENTRY_SIZE + HEADER_SIZE;
      final var fileSize = file.size();
      final var mappingCapacity = Math.max(fileSize, minCapacity);
      buffer = file.map(MapMode.READ_WRITE, 0, mappingCapacity);
      if (fileSize == 0) {
        // Initialize header;
        setVersion();
        setEntries(0);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to map backup index", e);
    }
  }

  /**
   * Remaps the buffer if needed to ensure it has enough capacity for at least one more entry. The
   * new buffer capacity is increased by 50% to avoid frequent remapping.
   */
  private void remapIfNeeded() {
    final var entries = getEntries();
    final var requiredCapacity = (long) (entries + 1) * ENTRY_SIZE + HEADER_SIZE;
    if (buffer.capacity() < requiredCapacity) {
      if (requiredCapacity > Integer.MAX_VALUE) {
        throw new IllegalStateException(
            "Backup index is too large to be mapped into memory: required capacity "
                + requiredCapacity
                + " for "
                + (entries + 1)
                + " entries exceeds maximum of "
                + Integer.MAX_VALUE);
      }
      try {
        final var position = buffer.position();
        final var limit = buffer.limit();
        // Increase capacity by 50% but cap at Integer.MAX_VALUE to avoid overflow
        final var newCapacity =
            Math.min((long) (entries * 1.5) * ENTRY_SIZE + HEADER_SIZE, Integer.MAX_VALUE);
        // Remap with the new capacity but keep the current position and limit.
        // The previous buffer is intentionally not unmapped here so that existing streams can still
        // read from it.
        buffer = file.map(MapMode.READ_WRITE, 0, newCapacity).position(position).limit(limit);
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to remap backup index", e);
      }
    }
  }

  /**
   * Searches for a backup entry by checkpoint id. If the entry is not found, the buffer position is
   * set to the insertion point.
   *
   * @param checkpointId the checkpoint id to search for
   * @return the found entry, or null if not found
   */
  IndexedBackup byCheckpointId(final long checkpointId) {
    return searchFor(entry -> Long.compare(entry.checkpointId(), checkpointId));
  }

  /**
   * Searches for an entry using binary search. The buffer position is set to the beginning of the
   * found entry or to the insertion point if not found.
   *
   * @param search the search comparator. Should return 0 if the entry is found, negative if the
   *     entry is greater than the searched entry, and positive if the entry is less than the
   *     searched entry.
   * @return the found entry, or null if not found.
   */
  private IndexedBackup searchFor(final SearchComparator search) {
    // binary search
    int left = 0;
    int right = getEntries() - 1;
    // fast path for appending at the end
    seek(right);
    final var lastEntry = readEntry();
    if (lastEntry == null || search.test(lastEntry) < 0) {
      return null;
    }
    while (left <= right) {
      final int mid = left + (right - left) / 2;
      seek(mid);
      final var entry = readEntry();
      final var comparison = search.test(entry);
      if (comparison == 0) {
        // Seek back to the found entry
        seek(mid);
        return entry;
      } else if (comparison < 0) {
        left = mid + 1;
      } else {
        right = mid - 1;
      }
    }
    // Entry not found, position at insertion point
    seek(left);
    return null;
  }

  /**
   * Seeks to the entry at the given index. If the index is negative, the position is set to the
   * start of the first entry. If the index is greater than the number of entries, the position is
   * set to the end of the last entry, i.e. the position where the next entry can be written.
   */
  private void seek(final int index) {
    buffer.position(HEADER_SIZE + Math.clamp(0, index, getEntries()) * ENTRY_SIZE);
  }

  private void removeMiddleEntry() {
    onTemporaryCopy(
        (original, copy) -> {
          try {
            copy.position(0);
            file.transferTo(0, buffer.position(), copy);
            file.transferTo(buffer.position() + ENTRY_SIZE, buffer.capacity(), copy);
            return -1;
          } catch (final IOException e) {
            throw new UncheckedIOException("Could not create new backup index", e);
          }
        });
  }

  void removeLastEntry() {
    // Directly write zeros to remove the last entry for clarity and efficiency
    buffer.putLong(0L);
    buffer.putLong(0L);
    buffer.putLong(0L);
    setEntries(getEntries() - 1);
    buffer.limit(buffer.position());
  }

  /** Appends a new entry at the end of the buffer. */
  private void appendNewEntry(final IndexedBackup newEntry) {
    // Move to the end of the buffer
    buffer.position(buffer.limit());
    // Extend the limit to make space for the new entry
    buffer.limit(buffer.limit() + ENTRY_SIZE);

    // Then write the new entry
    writeEntry(newEntry);
    setEntries(getEntries() + 1);
  }

  private void spliceNewEntry(final IndexedBackup newEntry) {
    onTemporaryCopy(
        (original, copy) -> {
          try {
            copy.position(0);
            original.transferTo(0, buffer.position(), copy);
            copy.position(buffer.position() + ENTRY_SIZE);
            original.transferTo(buffer.position(), buffer.capacity(), copy);
            writeEntry(newEntry);
            return 1;
          } catch (final IOException e) {
            throw new UncheckedIOException("Could not create new backup index", e);
          }
        });
  }

  /**
   * Creates a temporary copy of the index file, applies the given action, and replaces the index.
   * The action should return the number of added or removed entries. The buffer is remapped to the
   * new file before the action is run.
   */
  void onTemporaryCopy(final CopyAction action) {
    final var tempPath = resolveTemporaryIndexCopy();
    try {
      final var copy =
          FileChannel.open(
              tempPath,
              StandardOpenOption.READ,
              StandardOpenOption.WRITE,
              StandardOpenOption.CREATE);
      final var originalCapacity = buffer.capacity();
      final var originalPosition = buffer.position();
      final var originalLimit = buffer.limit();
      // Immediately remap the buffer to the temporary file so that the action has access to it.
      // The original buffer is intentionally not unmapped here so that existing streams can still
      // read from it.
      buffer =
          copy.map(MapMode.READ_WRITE, 0, originalCapacity)
              .position(originalPosition)
              .limit(originalLimit);
      final int addedOrRemovedEntries;
      try {
        addedOrRemovedEntries = action.update(file, copy);
      } catch (final IOException e) {
        throw new UncheckedIOException("Could not modify the copied backup index", e);
      }

      final var newEntries = getEntries() + addedOrRemovedEntries;
      buffer.limit(HEADER_SIZE + newEntries * ENTRY_SIZE);
      setEntries(newEntries);

      Files.move(tempPath, path, StandardCopyOption.ATOMIC_MOVE);
      file.close();
      file = copy;
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to modify backup index in temporary copy", e);
    }
  }

  private Path resolveTemporaryIndexCopy() {
    return path.getParent().resolve(path.getFileName() + ".tmp");
  }

  /** Writes an entry at the current buffer position and advances the position. */
  private void writeEntry(final IndexedBackup entry) {
    buffer.putLong(entry.checkpointId());
    buffer.putLong(entry.firstLogPosition());
    buffer.putLong(entry.lastLogPosition());
  }

  private IndexedBackup readEntry() {
    if (buffer.remaining() < ENTRY_SIZE) {
      return null;
    }
    return readEntry(buffer);
  }

  private static IndexedBackup readEntry(final ByteBuffer buffer) {
    return new IndexedBackup(buffer.getLong(), buffer.getLong(), buffer.getLong());
  }

  @Override
  public void close() throws IOException {
    if (file != null) {
      flush();
      IoUtil.unmap(buffer);
      file.close();
      file = null;
      buffer = null;
    }
  }

  public static final class IndexCorruption extends Exception {
    private IndexCorruption(final String message) {
      super(message);
    }
  }

  public static final class PartialIndexCorruption extends Exception {
    private final IndexedBackup lastValidEntry;

    private PartialIndexCorruption(final String message, final IndexedBackup lastValidEntry) {
      super(
          message
              + (lastValidEntry != null
                  ? ", last valid entry: " + lastValidEntry
                  : ", no valid entries found."));
      this.lastValidEntry = lastValidEntry;
    }

    public IndexedBackup getLastValidEntry() {
      return lastValidEntry;
    }
  }

  /**
   * A spliterator over backup index entries. This spliterator supports splitting for parallel
   * processing. The underlying buffer is frozen on creating a new instance which guarantees that
   * modification done through {@link CompactBackupIndex#add(IndexedBackup)} and {@link
   * CompactBackupIndex#remove(long)} after this spliterator is opened are not reflected.
   */
  @SuppressWarnings("ClassCanBeRecord")
  private static final class EntrySpliterator implements Spliterator<IndexedBackup> {
    private final MappedByteBuffer buffer;

    public EntrySpliterator(final MappedByteBuffer buffer) {
      this.buffer = buffer;
    }

    @Override
    public boolean tryAdvance(final java.util.function.Consumer<? super IndexedBackup> action) {
      if (buffer.remaining() >= ENTRY_SIZE) {
        action.accept(readEntry(buffer));
        return true;
      } else {
        return false;
      }
    }

    @Override
    public Spliterator<IndexedBackup> trySplit() {
      // Don't split if there are less than 1024 entries remaining to avoid overhead from too many
      // small splits.
      if (buffer.remaining() < ENTRY_SIZE * 1024) {
        return null;
      }
      // Split in the middle. We can't just divide by 2, because we need to ensure we split at an
      // entry boundary.
      final var availableEntries = buffer.remaining() / ENTRY_SIZE;
      final var mid = buffer.position() + (availableEntries / 2) * ENTRY_SIZE;
      // The new buffer starts in the first half. That's the contract for `trySplit` with the
      // `Spliterator.ORDERED` characteristic.
      final var splitBuffer = buffer.duplicate().position(buffer.position()).limit(mid);
      // We continue with the second half.
      buffer.position(mid);
      return new EntrySpliterator(splitBuffer);
    }

    @Override
    public long estimateSize() {
      return buffer.remaining() / ENTRY_SIZE;
    }

    @Override
    public int characteristics() {
      return Spliterator.SUBSIZED
          | Spliterator.IMMUTABLE
          | Spliterator.ORDERED
          | Spliterator.NONNULL;
    }
  }

  public interface SearchComparator {
    int test(IndexedBackup entry);
  }

  /**
   * An action that can copy from the original index file to a temporary copy in order to modify it.
   * On completion, the temporary copy will replace the original index file.
   */
  private interface CopyAction {

    /**
     * Applies the action on the temporary copy of the index file. By the time this method is
     * called, the buffer has already been remapped to the temporary file, all reads of the original
     * index must be done through {@link CompactBackupIndex#file}.
     *
     * @param copy the file channel for the temporary copy of the index file. The channel is
     *     positioned at the beginning.
     * @return The number of added (positive) or removed (negative) entries.
     * @throws IOException if an I/O error occurs during this operation.
     */
    int update(FileChannel original, FileChannel copy) throws IOException;
  }
}
