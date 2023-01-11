/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

import java.nio.MappedByteBuffer;
import java.util.Objects;
import jnr.constants.platform.Errno;
import jnr.ffi.Platform;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class to deal with native Unix file system calls, e.g. posix_madvise. As of now its goal
 * is just to cover Unix platforms (e.g. Linux, macOS). If this diverges, do refactor or split this
 * class.
 */
public final class PosixFs {
  private static final Logger LOGGER = LoggerFactory.getLogger(PosixFs.class);

  // by default, we assume non-Windows platforms support POSIX
  private static volatile boolean supportsMadvise = Platform.getNativePlatform().isUnix();

  private final LibC libC;

  public PosixFs() {
    this(LibC.ofNativeLibrary());
  }

  public PosixFs(final LibC libC) {
    this.libC = Objects.requireNonNull(libC, "must specify a LibC implementation");
  }

  /** Returns a default, thread-safe, static shared {@link PosixFs} instance. */
  public static PosixFs defaultInstance() {
    return SingletonHolder.INSTANCE;
  }

  /**
   * Returns whether calls to {@link #madvise(MappedByteBuffer, long, Advice)} are supported or not.
   * If this returns false, then a call to {@link #madvise(MappedByteBuffer, long, Advice)} will
   * throw an {@link UnsupportedOperationException}.
   *
   * @return true if supported, false otherwise
   */
  public boolean isMadviseEnabled() {
    return supportsMadvise;
  }

  /**
   * Disables usage of {@link #madvise(MappedByteBuffer, long, Advice)}. After calling this, {@link
   * #isMadviseEnabled()} ()} will return false.
   */
  public void disableMadvise() {
    LOGGER.debug("Disabling usage of madvise optimization");
    supportsMadvise = false;
  }

  /**
   * Provides advice to the OS about usage of the memory mapped buffer. See <a
   * href="https://man7.org/linux/man-pages/man3/posix_madvise.3.html">posix_madvise(3)</a> for
   * more.
   *
   * @param buffer the buffer to advise on
   * @param length the length of the range for which the advice is valid
   * @param advice the specific advice
   * @throws IllegalArgumentException if the length is negative, or the advice is not valid on that
   *     system
   * @throws UnsupportedOperationException if the function was previously disabled, or does not
   *     exist on this system
   * @throws IndexOutOfBoundsException if any of the computed page range (using the buffer's start
   *     address up to the given length in bytes) does not belong to this process
   */
  public void madvise(final MappedByteBuffer buffer, final long length, final Advice advice) {
    if (!isMadviseEnabled()) {
      LOGGER.warn("Native system call is disabled, likely not supported by this machine");
      return;
    }

    if (length < 0) {
      throw new IllegalArgumentException(
          String.format("Cannot advise system about negative range [%d]", length));
    }

    if (!isMadviseEnabled()) {
      throw new UnsupportedOperationException(
          "Failed to pre-allocate file natively: posix_fallocate is disabled");
    }

    final Pointer address = Pointer.wrap(Runtime.getSystemRuntime(), buffer);
    final int result = libC.posix_madvise(address, length, advice.value);

    // success
    if (result == 0) {
      return;
    }

    final Errno error = Errno.valueOf(result);
    switch (error) {
      case EINVAL -> throw new IllegalArgumentException(
          "Computed address [%s] of the given buffer is not a multiple of the system page size, or advice [%s] is invalid"
              .formatted(address, advice));
      case ENOMEM -> throw new IndexOutOfBoundsException(
          "Addresses in the specified range [%d, %d] are partially or completely outside the caller's address space"
              .formatted(address.address(), address.getAddress(length)));
      default -> {
        LOGGER.error(
            "Failed to provide advice for memory mapping: the underlying filesystem does not support this operation");
        disableMadvise();
      }
    }
  }

  private static final class SingletonHolder {
    private static final PosixFs INSTANCE = new PosixFs();
  }

  public enum Advice {
    // No further special treatment
    POSIX_MADV_NORMAL(0),
    // Expect random page references
    POSIX_MADV_RANDOM(1),
    // Expect sequential page references
    POSIX_MADV_SEQUENTIAL(2),
    // Will need these pages
    POSIX_MADV_WILLNEED(3),
    // Don't need these pages
    POSIX_MADV_DONTNEED(4);

    private final int value;

    Advice(final int value) {
      this.value = value;
    }
  }
}
