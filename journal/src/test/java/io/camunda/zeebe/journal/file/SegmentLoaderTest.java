/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.util.PosixPathAssert;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.agrona.IoUtil;
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
    final var segmentLoader = new SegmentLoader(createTemplateFile(tmpDir, segmentSize), true);
    final var segmentFile = tmpDir.resolve("segment.log");
    final var descriptor =
        JournalSegmentDescriptor.builder().withId(1).withMaxSegmentSize(segmentSize).build();

    // when
    segmentLoader.createSegment(segmentFile, descriptor, 1, new SparseJournalIndex(1));

    // then
    PosixPathAssert.assertThat(segmentFile).hasRealSize(segmentSize);
  }

  @Test
  void shouldNotPreallocateSegmentFiles(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var segmentLoader = new SegmentLoader(null, false);
    final var segmentFile = tmpDir.resolve("segment.log");
    final var descriptor =
        JournalSegmentDescriptor.builder().withId(1).withMaxSegmentSize(segmentSize).build();

    // when
    segmentLoader.createSegment(segmentFile, descriptor, 1, new SparseJournalIndex(1));

    // then
    PosixPathAssert.assertThat(segmentFile).hasRealSizeLessThan(segmentSize);
  }

  @Test
  void shouldPreallocateNewFileIfUnusedSegmentAlreadyExists(final @TempDir Path tmpDir)
      throws IOException {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var descriptor =
        JournalSegmentDescriptor.builder()
            .withId(1)
            .withIndex(1)
            .withMaxSegmentSize(segmentSize)
            .build();
    final var lastWrittenIndex = descriptor.index() - 1;
    final var segmentLoader = new SegmentLoader(createTemplateFile(tmpDir, segmentSize), true);
    final var segmentFile = tmpDir.resolve("segment.log");

    // when - the segment is "unused" if the lastWrittenIndex is less than the expected first index
    // this can happen if we crashed in the middle of creating the new segment
    Files.writeString(segmentFile, "foo");
    segmentLoader.createSegment(
        segmentFile, descriptor, lastWrittenIndex, new SparseJournalIndex(1));

    // then
    PosixPathAssert.assertThat(segmentFile).hasRealSize(segmentSize);
  }

  private Path createTemplateFile(final Path directory, final long maxSegmentSize) {
    try {
      final var path = Files.createTempFile(directory, "segment", ".tpl");
      try (final var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
        IoUtil.fill(channel, 0, maxSegmentSize, (byte) 0);
      }
      return path;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
