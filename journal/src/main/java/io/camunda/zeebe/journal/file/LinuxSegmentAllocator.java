/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.file.LinuxFs.FallocateFlags;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LinuxSegmentAllocator implements SegmentAllocator {
  private static final Logger LOGGER = LoggerFactory.getLogger(LinuxSegmentAllocator.class);
  private final LinuxFs linuxFs;
  private final SegmentAllocator fallback;

  LinuxSegmentAllocator() {
    this(new LinuxFs());
  }

  LinuxSegmentAllocator(final LinuxFs linuxFs) {
    this(linuxFs, SegmentAllocator.posix());
  }

  LinuxSegmentAllocator(final LinuxFs linuxFs, final SegmentAllocator fallback) {
    this.linuxFs = Objects.requireNonNull(linuxFs, "must specify a Linux file system abstraction");
    this.fallback =
        Objects.requireNonNull(
            fallback, "must specify a fallback allocator or SegmentAllocator::noop");
  }

  @Override
  public void allocate(
      final FileDescriptor descriptor, final FileChannel segmentChannel, final long segmentSize)
      throws IOException {
    if (!linuxFs.isFallocateEnabled()) {
      fallback.allocate(descriptor, segmentChannel, segmentSize);
      return;
    }

    try {
      linuxFs.fallocate(descriptor, 0, segmentSize, FallocateFlags.FALLOC_FL_ZERO_RANGE);
    } catch (final UnsupportedOperationException e) {
      LOGGER.warn(
          "Failed to use native system call to pre-allocate file, will use fallback from now on",
          e);
      linuxFs.disableFallocate();
      fallback.allocate(descriptor, segmentChannel, segmentSize);
    }
  }
}
