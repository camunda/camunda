/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jnr.posix.FileStat;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import jnr.posix.util.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@SuppressWarnings("resource")
@Execution(ExecutionMode.CONCURRENT)
final class SegmentLoaderTest {
  @Test
  void shouldPreallocateSegmentFiles(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var segmentLoader = new SegmentLoader(true);
    final var segmentFile = tmpDir.resolve("segment.log");
    final var descriptor =
        SegmentDescriptor.builder().withId(1).withMaxSegmentSize(segmentSize).build();

    // when
    segmentLoader.createSegment(segmentFile, descriptor, 1, new SparseJournalIndex(1));

    // then
    final var realSize = getRealSize(segmentFile);
    final var maxRealSize = segmentSize + getBlockSize(segmentFile);
    assertThat(realSize)
        .as(
            "Expected <%s> to have a real size between <%d> and <%d> bytes, but it had <%d>",
            segmentFile, segmentSize, maxRealSize, realSize)
        .isBetween((long) segmentSize, maxRealSize);
  }

  @Test
  void shouldNotPreallocateSegmentFiles(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var segmentLoader = new SegmentLoader(false);
    final var segmentFile = tmpDir.resolve("segment.log");
    final var descriptor =
        SegmentDescriptor.builder().withId(1).withMaxSegmentSize(segmentSize).build();

    // when
    segmentLoader.createSegment(segmentFile, descriptor, 1, new SparseJournalIndex(1));

    // then
    final var realSize = getRealSize(segmentFile);
    assertThat(realSize)
        .as(
            "Expected <%s> to have a real size less than <%d> bytes, but it had <%d>",
            segmentFile, segmentSize, realSize)
        .isLessThan(segmentSize);
  }

  @Test
  void shouldPreallocateNewFileIfUnusedSegmentAlreadyExists(final @TempDir Path tmpDir)
      throws IOException {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var descriptor =
        SegmentDescriptor.builder().withId(1).withIndex(1).withMaxSegmentSize(segmentSize).build();
    final var lastWrittenIndex = descriptor.index() - 1;
    final var segmentLoader = new SegmentLoader(true);
    final var segmentFile = tmpDir.resolve("segment.log");

    // when - the segment is "unused" if the lastWrittenIndex is less than the expected first index
    // this can happen if we crashed in the middle of creating the new segment
    Files.writeString(segmentFile, "foo");
    segmentLoader.createSegment(
        segmentFile, descriptor, lastWrittenIndex, new SparseJournalIndex(1));

    // then
    final var realSize = getRealSize(segmentFile);
    final var maxRealSize = segmentSize + getBlockSize(segmentFile);
    assertThat(realSize)
        .as(
            "Expected <%s> to have a real size between <%d> and <%d> bytes, but it had <%d>",
            segmentFile, segmentSize, maxRealSize, realSize)
        .isBetween((long) segmentSize, maxRealSize);
  }

  /**
   * Returns the actual size of the file on disk by checking the blocks allocated for this file. On
   * most modern UNIX systems, doing {@link Files#size(Path)} returns that size as reported by the
   * file's metadata, which may not be the real size (e.g. compressed file systems, sparse files,
   * etc.). Using the {@code lstat} function from the C library we can get the actual size on disk
   * of the file.
   *
   * <p>{@code lstat} will return the number of 512-bytes blocks used by a file. To get the real
   * size, you simply multiply by 512. Note that unless your file size is aligned with the block
   * size of your device, then the real size may be slightly larger, as more blocks may have been
   * allocated.
   *
   * <p>NOTE: on Windows, sparse files are not the default, so {@link File#length()} is appropriate.
   * Plus, there is no {@code lstat} function, and the equivalent function {@code wstat} does not
   * return the number of blocks.
   *
   * @param file the file to get the size of
   * @return the actual size on disk of the file
   */
  private long getRealSize(final Path file) {
    if (Platform.IS_WINDOWS) {
      try {
        return Files.size(file);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    final POSIX posixFunctions = POSIXFactory.getNativePOSIX();
    final var pathString = file.toString();
    final FileStat stat = posixFunctions.stat(pathString);

    return stat.blocks() * 512;
  }

  /**
   * Returns the I/O block size of the device containing the given file. This can be used to compute
   * an upper bound for the real file size. On Windows, as we use {@link Files#size(Path)} for the
   * real size, this simply returns 0.
   *
   * @param file the file to get the block size of
   * @return the I/O block size of the device containing the file
   */
  private long getBlockSize(final Path file) {
    if (Platform.IS_WINDOWS) {
      return 0;
    }

    final POSIX posixFunctions = POSIXFactory.getNativePOSIX();
    final var pathString = file.toString();
    final FileStat stat = posixFunctions.stat(pathString);

    return stat.blockSize();
  }
}
