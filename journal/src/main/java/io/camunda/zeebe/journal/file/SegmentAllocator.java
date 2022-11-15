/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import org.agrona.IoUtil;

/** Defines the strategy when it comes to pre-allocating segment files. */
@FunctionalInterface
public interface SegmentAllocator {

  /**
   * Pre-allocates {@code segmentSize} disk space for file corresponding to the given descriptor and
   * channel.
   *
   * @param fd an open file descriptor to the file that will be pre-allocated
   * @param channel an open channel to the file to pre-allocate
   * @param segmentSize the desired size of the segment on disk, in bytes
   * @throws IOException if any error occur during pre-allocation; if this is thrown, no guarantees
   *     are made about the state of the file on disk, and no resources are closed
   */
  void allocate(FileDescriptor fd, FileChannel channel, final long segmentSize) throws IOException;

  /** Returns an allocator which does nothing, i.e. does not allocate disk space. */
  static SegmentAllocator noop() {
    return (f, c, s) -> {};
  }

  /** Returns an allocator which fills the file by writing chunks of zeros to disk. */
  static SegmentAllocator fill() {
    return (f, c, s) -> IoUtil.fill(c, 0, s, (byte) 0);
  }

  static SegmentAllocator posix() {
    return new PosixSegmentAllocator();
  }
}
