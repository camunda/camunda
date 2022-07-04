/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.fs;

import com.sun.jna.LastErrorException;
import com.sun.jna.Platform;
import com.sun.jna.platform.linux.ErrNo;
import io.camunda.zeebe.util.Loggers;
import io.camunda.zeebe.util.error.OutOfDiskSpaceException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.slf4j.Logger;

/**
 * A helper class to deal with native Unix file system calls, e.g. posix_fallocate. As of now its
 * goal is just to cover Unix platforms (e.g. Linux, macOS). If this diverges, do refactor or split
 * this class.
 */
public final class NativeFS {
  public static final NativeFS DEFAULT = new NativeFS(LibC.ofNativeLibrary());
  private static final Logger LOGGER = Loggers.FILE_LOGGER;
  private static final VarHandle FILE_DESCRIPTOR_FD;

  static {
    VarHandle fileDescriptorFD;
    try {
      fileDescriptorFD =
          MethodHandles.privateLookupIn(FileDescriptor.class, MethodHandles.lookup())
              .findVarHandle(FileDescriptor.class, "fd", int.class);
    } catch (final NoSuchFieldException | IllegalAccessException e) {
      LOGGER.warn("Cannot look up file descriptor via reflection; NativeFS will be disabled", e);
      fileDescriptorFD = null;
    }

    FILE_DESCRIPTOR_FD = fileDescriptorFD;
  }

  // by default, we assume non-Windows platforms support posix_fallocate. some may not, and some
  // file systems may not, and normally C libraries will emulate the behavior, but some (e.g. musl)
  // may return EOPNOTSUPP, in which case we want to set this flag to false.
  //
  // note that this flag assumes there is only one underlying filesystem for the whole application
  private volatile boolean supportsPosixFallocate =
      FILE_DESCRIPTOR_FD != null && (Platform.isLinux() || Platform.isMac());

  private final LibC libC;

  public NativeFS(final LibC libC) {
    this.libC = libC;
  }

  public boolean supportsPosixFallocate() {
    return supportsPosixFallocate;
  }

  public void disablePosixFallocate() {
    LOGGER.debug("Disabling usage of posix_fallocate optimization");
    supportsPosixFallocate = false;
  }

  /**
   * Calls posix_fallocate system call, delegating the allocation of the blocks to the C library.
   * Callers are expected to check {@link #supportsPosixFallocate()} before calling this method.
   *
   * <p>For glibc, this means it will delegate the call to the file system. If it supports it (e.g.
   * ext4), this is much more performant than writing a file, as the blocks are reserved for the
   * file, but no I/O operations take place (other than updating the file's metadata). Not only
   * this, but the blocks reserved are in most cases contiguous, making for less disk fragmentation.
   *
   * <p>When the file system does not support the call, then the library will emulate it by actually
   * zero-ing the file. In some cases (e.g. musl), the library will do no emulation, and instead we
   * will throw an {@link UnsupportedOperationException} and set {@link #supportsPosixFallocate} to
   * false.
   *
   * <p><a href="https://man7.org/linux/man-pages/man3/posix_fallocate.3.html">See the man pages for
   * posix_fallocate</a>
   *
   * @param descriptor the file descriptor of the file we want to grow
   * @param offset the offset at which we want to start growing the file
   * @param length the length, in bytes, of the region to grow
   * @throws IllegalArgumentException if offset or length is negative
   * @throws InterruptedIOException if an interrupt occurs while it was allocating the file, meaning
   *     it may not have been fully allocated
   * @throws UnsupportedOperationException if the underlying file system does not support the
   *     operation
   * @throws OutOfDiskSpaceException if there is not enough disk space to allocate the file
   * @throws IOException if the file descriptor is invalid (e.g. not opened for writing, not
   *     pointing to a regular file, etc.)
   * @throws LastErrorException if the return error code is not one we know from the documentation
   */
  public void posixFallocate(final FileDescriptor descriptor, final long offset, final long length)
      throws IOException {
    if (offset < 0) {
      throw new IllegalArgumentException(
          String.format("Cannot allocate file with a negative offset of [%d]", offset));
    }

    if (length < 0) {
      throw new IllegalArgumentException(
          String.format("Cannot allocate file with a negative length of [%d]", length));
    }

    final int fd = (int) FILE_DESCRIPTOR_FD.get(descriptor);
    final int result = libC.posix_fallocate(fd, offset, length);

    switch (result) {
      case 0: // success
        return;
      case ErrNo.EBADF:
        throw new IOException(
            "Failed to pre-allocate file: it doesn't have a valid file descriptor, or it's not "
                + "opened for writing");
      case ErrNo.EFBIG:
        throw new IOException(
            String.format(
                "Failed to pre-allocate file: it's length [%d] would exceed the system's maximum "
                    + "file length",
                offset + length));
      case ErrNo.EINTR:
        throw new InterruptedIOException("Failed to pre-allocate file interrupted during call");
      case ErrNo.EINVAL:
        disablePosixFallocate();
        throw new UnsupportedOperationException(
            "Failed to pre-allocate file: the underlying filesystem does not support this operation");
      case ErrNo.ENODEV, ErrNo.ESPIPE:
        throw new IOException(
            "Failed to pre-allocate file: the descriptor does not point to a regular file");
      case ErrNo.ENOSPC:
        throw new OutOfDiskSpaceException("Failed to pre-allocate file: there is not enough space");
      default: // unexpected error
        throw new LastErrorException(result);
    }
  }
}
