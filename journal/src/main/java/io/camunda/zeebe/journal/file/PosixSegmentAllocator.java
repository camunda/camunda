/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import io.camunda.zeebe.journal.fs.PosixFs;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PosixSegmentAllocator implements SegmentAllocator {
  private static final Logger LOGGER = LoggerFactory.getLogger(PosixSegmentAllocator.class);
  private final PosixFs posixFs;
  private final SegmentAllocator fallback;

  PosixSegmentAllocator() {
    this(new PosixFs());
  }

  PosixSegmentAllocator(final PosixFs posixFs) {
    this(posixFs, SegmentAllocator.fill());
  }

  PosixSegmentAllocator(final PosixFs posixFs, final SegmentAllocator fallback) {
    this.posixFs = posixFs;
    this.fallback = fallback;
  }

  @Override
  public void allocate(
      final FileDescriptor descriptor, final FileChannel segmentChannel, final long segmentSize)
      throws IOException {
    if (!posixFs.isPosixFallocateEnabled()) {
      fallback.allocate(descriptor, segmentChannel, segmentSize);
      return;
    }

    try {
      posixFs.posixFallocate(descriptor, 0, segmentSize);
    } catch (final UnsupportedOperationException e) {
      LOGGER.warn(
          "Failed to use native system call to pre-allocate file, will use fallback from now on",
          e);
      posixFs.disablePosixFallocate();
      fallback.allocate(descriptor, segmentChannel, segmentSize);
    }
  }
}
