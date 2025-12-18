/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

  PosixSegmentAllocator(final PosixFs posixFs, final SegmentAllocator fallback) {
    this.posixFs = posixFs;
    this.fallback = fallback;
  }

  PosixSegmentAllocator(final SegmentAllocator fallback) {
    this(new PosixFs(), fallback);
  }

  @Override
  public void allocate(
      final FileChannel channel, final FileDescriptor fileDescriptor, final long segmentSize)
      throws IOException {
    if (!posixFs.isPosixFallocateEnabled()) {
      fallback.allocate(channel, fileDescriptor, segmentSize);
      return;
    }

    try {
      posixFs.posixFallocate(fileDescriptor, 0, segmentSize);
    } catch (final UnsupportedOperationException e) {
      LOGGER.warn(
          "Failed to use native system call to pre-allocate file, will use fallback from now on {}",
          fallback,
          e);
      posixFs.disablePosixFallocate();
      fallback.allocate(channel, fileDescriptor, segmentSize);
    }
  }
}
