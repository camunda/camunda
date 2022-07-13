/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

import io.camunda.zeebe.journal.JournalException.OutOfDiskSpace;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Abstracts away native file system operations. This allows us to better test dependents in
 * isolation.
 */
public interface VirtualFs {

  /**
   * Pre-allocates disk space for the file pointed at by the given descriptor/channel.
   *
   * @param descriptor the file descriptor of the file we want to grow
   * @param channel the file channel pointing to the same file as the descriptor above
   * @param offset the offset at which we want to start growing the file
   * @param length the length, in bytes, of the region to grow
   * @throws IllegalArgumentException if offset or length is negative
   * @throws UnsupportedOperationException if the underlying implementation does not support this
   * @throws OutOfDiskSpace if there is not enough disk space to allocate the file
   * @throws IOException if an exception occurs during an I/O call; see the cause for more
   */
  void preallocate(FileDescriptor descriptor, final FileChannel channel, long offset, long length)
      throws IOException;

  /**
   * Creates a default implementation which attempts to leverage native system calls where possible,
   * but provides portable fallback implementations.
   *
   * @return default portable implementation
   */
  static VirtualFs createDefault() {
    return new PosixCompatFs();
  }
}
