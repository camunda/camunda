/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.util.IndexEntry;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.IoUtil;

/**
 * An out-of-band, memory-mapped secondary index for a specific {@link Segment}. It natively indexes
 * logical Application Sequence Numbers (ASQN) directly to their absolute physical offset and length
 * within the segment's .log file.
 *
 * <p>This allows O(1) zero-copy reads without parsing log headers or frames.
 */
public final class SegmentIndex implements AutoCloseable {

  private static final int ENTRY_SIZE =
      32; // 8(lowest) + 8(highest) + 8(index) + 4(offset) + 4(length)

  private final Path indexPath;
  private final FileChannel indexChannel;
  private final MappedByteBuffer mappedIndex;

  private final AtomicInteger entryCount = new AtomicInteger(0);

  /**
   * Initializes or loads the segment index file (.sidx).
   *
   * @param segmentPath The path of the associated .log file.
   * @param maxSegmentSize The maximum possible size of the .log file, used to estimate index
   *     capacity.
   * @throws IOException If the file cannot be mapped.
   */
  public SegmentIndex(final Path segmentPath, final int maxSegmentSize) throws IOException {
    final String segmentFileName = segmentPath.getFileName().toString();
    indexPath = segmentPath.getParent().resolve(segmentFileName + ".sidx");

    indexChannel =
        FileChannel.open(
            indexPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.READ,
            StandardOpenOption.WRITE);

    // Pre-allocate the max possible index size to avoid resizing the memory map.
    // Assuming a conservative minimum payload size of 128 bytes.
    final int maxEntries = Math.max(10_000, maxSegmentSize / 128);
    final int indexSizeBytes = maxEntries * ENTRY_SIZE;

    mappedIndex = indexChannel.map(FileChannel.MapMode.READ_WRITE, 0, indexSizeBytes);
  }

  /**
   * Appends a new coordinate to the index. Note: This does not force the data to disk. It relies on
   * lazy OS flushing.
   */
  public void appendEntry(
      final long lowestAsqn,
      final long highestAsqn,
      final long index,
      final int position,
      final int length) {
    final int currentCount = entryCount.get();
    final int pos = currentCount * ENTRY_SIZE;

    mappedIndex.putLong(pos, lowestAsqn);
    mappedIndex.putLong(pos + 8, highestAsqn); // NEW: The upper bound of the batch
    mappedIndex.putLong(pos + 16, index);
    mappedIndex.putInt(pos + 24, position);
    mappedIndex.putInt(pos + 28, length);

    entryCount.set(currentCount + 1);
  }

  /**
   * Retrieves slices starting from the batch that contains the requested ASQN, up to the given
   * upper bound index (exclusive).
   *
   * @param lowerBoundAsqn The requested logical offset.
   * @param upperBoundIndex The maximum index entry to read (exclusive). Used to enforce High
   *     Watermarks.
   * @return A list of slices.
   */
  public List<io.camunda.zeebe.util.IndexEntry> scanEntries(
      final long lowerBoundAsqn, final int maxBytes, final long upperBoundIndex) {
    final int currentCount = entryCount.get();
    if (currentCount == 0) {
      return Collections.emptyList();
    }

    int startEntryIndex = floorAsqn(lowerBoundAsqn);

    if (startEntryIndex == -1) {
      startEntryIndex = 0; // Request is older than our oldest entry
    }

    if (startEntryIndex >= currentCount) {
      return Collections.emptyList();
    }

    int readBytes = 0;
    final var result = new ArrayList<IndexEntry>();
    for (int i = startEntryIndex; i < currentCount; i++) {
      final int pos = i * ENTRY_SIZE;
      final var currentLowestAsqn = mappedIndex.getLong(pos);
      final var currentHighestAsqn = mappedIndex.getLong(pos + 8);
      final long currentIndex = mappedIndex.getLong(pos + 16);
      final var currentPosition = mappedIndex.getInt(pos + 24);
      final var currentLength = mappedIndex.getInt(pos + 28);
      readBytes += currentLength;

      if (lowerBoundAsqn > currentHighestAsqn) {
        continue;
      }

      if (currentIndex > upperBoundIndex) {
        break;
      }

      final var indexEntry =
          new io.camunda.zeebe.util.IndexEntry(
              currentLowestAsqn, currentHighestAsqn, currentIndex, currentPosition, currentLength);
      result.add(indexEntry);

      if (readBytes >= maxBytes) {
        break;
      }
    }

    return result;
  }

  /** Called during Raft log truncation to drop invalid index entries. */
  public void truncate(final long maxAsqnToKeep) {
    // Find the first index entry that is strictly greater than the max kept ASQN
    final int newCeiling = ceilingAsqn(maxAsqnToKeep + 1);
    if (newCeiling != -1) {
      // Instantly "deletes" the overwritten batches
      entryCount.set(newCeiling);
    }
  }

  /** Returns the ASQN of the last appended entry. Used during Startup Recovery. */
  public long getLastIndexedAsqn() {
    final var currentEntryCount = entryCount.get();
    if (currentEntryCount == 0) {
      return -1L;
    }
    return mappedIndex.getLong(((currentEntryCount - 1) * ENTRY_SIZE) + 8);
  }

  /** Returns the total number of entries currently in the index. */
  public int getEntryCount() {
    return entryCount.get();
  }

  public long getFirstIndexedAsqn() {
    final var currentEntryCount = entryCount.get();
    if (currentEntryCount <= 0) {
      return -1L;
    }

    return mappedIndex.getLong(0);
  }

  /**
   * O(log N) Floor Binary Search. Returns the index of the greatest ASQN that is <= the target
   * ASQN.
   */
  private int floorAsqn(final long targetAsqn) {
    final var currentEntryCount = entryCount.get();
    int low = 0;
    int high = currentEntryCount - 1;
    int result = -1;

    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final long midVal = mappedIndex.getLong(mid * ENTRY_SIZE);

      if (midVal == targetAsqn) {
        return mid; // Exact match
      } else if (midVal < targetAsqn) {
        result = mid; // Potential floor
        low = mid + 1; // Look right
      } else {
        high = mid - 1; // Look left
      }
    }
    return result;
  }

  /**
   * O(log N) Ceiling Binary Search. Returns the index of the smallest ASQN that is >= the target
   * ASQN.
   */
  private int ceilingAsqn(final long targetAsqn) {
    final var currentEntryCount = entryCount.get();
    int low = 0;
    int high = currentEntryCount - 1;
    int result = -1;

    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final long midVal = mappedIndex.getLong(mid * ENTRY_SIZE);

      if (midVal >= targetAsqn) {
        result = mid; // Potential ceiling
        high = mid - 1; // Look left
      } else {
        low = mid + 1; // Look right
      }
    }
    return result;
  }

  @Override
  public void close() throws Exception {
    indexChannel.close();
    IoUtil.unmap(mappedIndex);
  }

  public void delete() {
    try {
      Files.deleteIfExists(indexPath);
    } catch (final IOException ignored) {
    }
  }
}
