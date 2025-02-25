/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.util.PosixPathAssert;
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
    final var segmentLoader = new SegmentLoader(segmentSize * 2, new JournalMetrics(meterRegistry));
    final var segmentFile = tmpDir.resolve("segment.log");

    // when - "unused" segment can happen if we crashed in the middle of creating the new segment
    Files.writeString(segmentFile, "foo");
    segmentLoader.createSegment(segmentFile, descriptor, 0, new SparseJournalIndex(1));

    // then
    PosixPathAssert.assertThat(segmentFile).hasRealSize(segmentSize);
  }
}
