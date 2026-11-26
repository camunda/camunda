/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.journal.JournalException.SyncFailed;
import io.camunda.zeebe.journal.fs.PosixFs;
import io.camunda.zeebe.journal.util.PosixPathAssert;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@SuppressWarnings("resource")
@Execution(ExecutionMode.CONCURRENT)
final class SegmentLoaderTest {
  @AutoClose private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  @Test
  void shouldPreallocateNewFileIfUnusedSegmentAlreadyExists(final @TempDir Path tmpDir)
      throws IOException {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var descriptor =
        SegmentDescriptor.builder().withId(1).withIndex(1).withMaxSegmentSize(segmentSize).build();
    final var segmentLoader =
        new SegmentLoader(
            segmentSize * 2,
            new JournalMetrics(meterRegistry),
            SegmentAllocator.defaultAllocator());
    final var segmentFile = tmpDir.resolve("segment.log");

    // when - "unused" segment can happen if we crashed in the middle of creating the new segment
    Files.writeString(segmentFile, "foo");
    segmentLoader.createSegment(segmentFile, descriptor, 0, new SparseJournalIndex(1));

    // then
    PosixPathAssert.assertThat(segmentFile).hasRealSize(segmentSize);
  }

  @Test
  void shouldFailToCreateSegmentWhenItsDirectoryFailsToSync(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var descriptor =
        SegmentDescriptor.builder().withId(1).withIndex(1).withMaxSegmentSize(segmentSize).build();
    final var segmentLoader =
        new SegmentLoader(
            segmentSize * 2,
            new JournalMetrics(meterRegistry),
            SegmentAllocator.defaultAllocator());
    final var segmentFile = tmpDir.resolve("segment.log");

    // when
    final Throwable failure;
    try (final var fileUtil = mockStatic(FileUtil.class, CALLS_REAL_METHODS)) {
      fileUtil
          .when(() -> FileUtil.flushDirectory(tmpDir))
          .thenThrow(new IOException("Input/output error"));
      failure =
          catchThrowable(
              () ->
                  segmentLoader.createSegment(
                      segmentFile, descriptor, 0, new SparseJournalIndex(1)));
    }

    // then - the new file may not be in the directory on disk, so the journal must stop
    assertThat(failure).isInstanceOf(SyncFailed.class);
  }

  @Test
  void shouldDeleteNewSegmentWhileMapped(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var descriptor =
        SegmentDescriptor.builder().withId(1).withIndex(1).withMaxSegmentSize(segmentSize).build();
    final var segmentLoader =
        new SegmentLoader(
            segmentSize * 2,
            new JournalMetrics(meterRegistry),
            SegmentAllocator.defaultAllocator());
    final var segmentFile = tmpDir.resolve("segment.log");
    final var segment =
        segmentLoader.createSegment(segmentFile, descriptor, 0, new SparseJournalIndex(1));

    // when
    segment.delete();

    // then
    assertThat(tmpDir).isEmptyDirectory();
  }

  @Test
  void shouldDropPageCacheBeforeLoadingExistingSegment(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var descriptor =
        SegmentDescriptor.builder().withId(1).withIndex(1).withMaxSegmentSize(segmentSize).build();
    final var segmentFile = tmpDir.resolve("segment.log");
    new SegmentLoader(
            segmentSize * 2, new JournalMetrics(meterRegistry), SegmentAllocator.defaultAllocator())
        .createSegment(segmentFile, descriptor, 0, new SparseJournalIndex(1))
        .close();
    final var posixFs = mock(PosixFs.class);
    final var segmentLoader =
        new SegmentLoader(
            segmentSize * 2,
            new JournalMetrics(meterRegistry),
            SegmentAllocator.defaultAllocator(),
            posixFs);

    // when
    segmentLoader.loadExistingSegment(segmentFile, 0, new SparseJournalIndex(1)).close();

    // then
    verify(posixFs).dropPageCache(segmentFile);
  }
}
