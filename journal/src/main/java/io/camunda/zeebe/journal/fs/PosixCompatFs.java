/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.FileChannel;
import org.agrona.IoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link VirtualFs} implementation which attempts to use POSIX compliant system calls where
 * possible, and provides fallback implementations when it's not the case.
 */
final class PosixCompatFs implements VirtualFs {
  private static final Logger LOGGER = LoggerFactory.getLogger(PosixCompatFs.class);

  private final PosixFs posixFs;

  PosixCompatFs() {
    this(new PosixFs(LibC.ofNativeLibrary()));
  }

  PosixCompatFs(final PosixFs posixFs) {
    this.posixFs = posixFs;
  }

  /**
   * Pre-allocates disk space, by first trying to use a {@link PosixFs} to natively and cheaply
   * allocate disk space, without performing any I/O. If this is not possible, falls back to writing
   * out 4Kb blocks of 0s until we've reached the expected length, or at most 1 block more.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public void preallocate(
      final FileDescriptor descriptor, final FileChannel channel, final long length)
      throws IOException {
    if (length < 0) {
      throw new IllegalArgumentException(
          String.format(
              "Expected to preallocate [%d] bytes, but the length cannot be negative", length));
    }

    if (!posixFs.isPosixFallocateEnabled()) {
      IoUtil.fill(channel, 0, length, (byte) 0);
      return;
    }

    try {
      posixFs.posixFallocate(descriptor, 0, length);
    } catch (final UnsupportedOperationException e) {
      LOGGER.warn(
          "Cannot use native calls to pre-allocate files; will fallback to zero-ing from now on",
          e);
      IoUtil.fill(channel, 0, length, (byte) 0);
    } catch (final IOException e) {
      throw new IOException(
          String.format("Failed to pre-allocate new file of length [%d]", length), e);
    }
  }
}
