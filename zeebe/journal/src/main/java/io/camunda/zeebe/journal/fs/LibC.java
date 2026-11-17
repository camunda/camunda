/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

import io.camunda.zeebe.util.VisibleForTesting;
import jnr.ffi.LibraryLoader;
import jnr.ffi.annotations.In;
import jnr.ffi.types.off_t;

/**
 * Used to bind certain calls from libc to Java methods via JNA.
 *
 * <p>Note that method names do not follow our conventions, but this is necessary here because the
 * names must match those of the C library.
 *
 * <p>See {@link #ofNativeLibrary()} for an example of how to use this.
 */
@SuppressWarnings({"checkstyle:methodname", "java:S100"})
public interface LibC {
  int posix_fallocate(final @In int fd, final @In @off_t long offset, final @In @off_t long len);

  /**
   * Returns a process-wide, cached instance of LibC bound to the system's C library (e.g. glibc,
   * musl, etc.).
   *
   * <p>If it fails to bind to the C library, it will return a {@link InvalidLibC} instance which
   * throws {@link UnsupportedOperationException} on every call.
   *
   * <p>Binding is done exactly once per JVM, on first use, via {@link LibCHolder}'s class
   * initialization (guaranteed thread-safe and only-once by the JVM). This matters because {@link
   * LibraryLoader#loadLibrary} is not safe to call concurrently from multiple threads for the same
   * library: every {@code SegmentAllocator.defaultAllocator()} call (i.e. every raft partition
   * bootstrap) used to trigger its own native bind, and doing that from several partitions' actor
   * threads at once could livelock inside jnr-ffi's internal (non-thread-safe) library registry.
   *
   * @return the shared instance of this library
   */
  static LibC ofNativeLibrary() {
    return LibCHolder.INSTANCE;
  }

  @VisibleForTesting
  static LibC ofNativeLibrary(final String libraryName) {
    return LibCHolder.bind(libraryName);
  }

  /**
   * Dummy implementation which throws {@link UnsupportedOperationException} on every call.
   * Explicitly left non-final so test classes can extend it and overload only these methods they
   * care about.
   */
  class InvalidLibC implements LibC {

    @Override
    public int posix_fallocate(final int fd, final long offset, final long len) {
      throw new UnsupportedOperationException();
    }
  }
}
