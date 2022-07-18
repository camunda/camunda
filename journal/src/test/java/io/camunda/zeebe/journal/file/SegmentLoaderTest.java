/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@SuppressWarnings("resource")
@Execution(ExecutionMode.CONCURRENT)
final class SegmentLoaderTest {
  @Test
  void shouldCreateNewSegment(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var segmentLoader = new SegmentLoader();
    final var journalIndex = new SparseJournalIndex(1);
    final var segmentFile = tmpDir.resolve("segment.log");
    final var descriptor =
        JournalSegmentDescriptor.builder()
            .withId(1)
            .withIndex(1)
            .withMaxSegmentSize(segmentSize)
            .build();

    // when
    final var createdSegment = segmentLoader.createSegment(segmentFile, descriptor, journalIndex);

    // then
    assertThat(createdSegment.file().file().toPath())
        .exists()
        .isRegularFile()
        .isReadable()
        .isEqualByComparingTo(segmentFile);
    assertThat(createdSegment.descriptor()).isEqualTo(descriptor);
    assertThat(createdSegment.id()).isEqualTo(descriptor.id());
    assertThat(createdSegment.index()).isEqualTo(descriptor.index());
    assertThat(createdSegment.isOpen()).isTrue();
  }

  @Test
  void shouldLoadExistingSegment(final @TempDir Path tmpDir) {
    // given
    final var segmentSize = 4 * 1024 * 1024;
    final var segmentLoader = new SegmentLoader();
    final var journalIndex = new SparseJournalIndex(1);
    final var segmentFile = tmpDir.resolve("segment.log");
    final var descriptor =
        JournalSegmentDescriptor.builder()
            .withId(1)
            .withIndex(1)
            .withMaxSegmentSize(segmentSize)
            .build();
    final var createdSegment = segmentLoader.createSegment(segmentFile, descriptor, journalIndex);
    final var entryData = BufferUtil.wrapString("foo");
    createdSegment.writer().append(1, entryData);

    // when
    final var loadedSegment = segmentLoader.loadExistingSegment(segmentFile, 1, journalIndex);

    // then
    assertThat(loadedSegment.descriptor()).isEqualTo(descriptor);
    assertThat(loadedSegment.createReader().next().data()).isEqualTo(entryData);
  }
}
