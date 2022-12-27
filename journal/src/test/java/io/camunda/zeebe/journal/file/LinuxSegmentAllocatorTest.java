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
import java.io.RandomAccessFile;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@EnabledOnOs(value = OS.LINUX, disabledReason = "Tests specific Linux filesystem operations")
@Execution(ExecutionMode.CONCURRENT)
final class LinuxSegmentAllocatorTest {
  @Test
  void shouldUseFallbackWhenFallocateNotSupported(final @TempDir Path tmpDir) throws IOException {
    // given
    final var linuxFs = new LinuxFs(new LibC.InvalidLibC());
    final var fallback = SegmentAllocator.fill();
    final var allocator = new LinuxSegmentAllocator(linuxFs, fallback);
    final var segmentFile = tmpDir.resolve("file");
    final var size = 1024 * 1024;

    // when
    try (final var file = new RandomAccessFile(segmentFile.toFile(), "rw")) {
      allocator.allocate(file.getFD(), file.getChannel(), size);
    }

    // then
    PosixPathAssert.assertThat(segmentFile).hasRealSize(size);
    Assertions.assertThat(linuxFs.isFallocateEnabled()).as("has disabled fallocate").isFalse();
  }

  @Test
  void shouldPreallocateFile(final @TempDir Path tmpDir) throws IOException {
    // given
    final var linuxFs = new LinuxFs();
    final var allocator = new LinuxSegmentAllocator(linuxFs);
    final var segmentFile = tmpDir.resolve("file");
    final var size = 1024 * 1024;

    // when
    try (final var file = new RandomAccessFile(segmentFile.toFile(), "rw")) {
      allocator.allocate(file.getFD(), file.getChannel(), size);
    }

    // then
    PosixPathAssert.assertThat(segmentFile).hasRealSize(size);
  }
}
