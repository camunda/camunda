/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jnr.posix.FileStat;
import jnr.posix.POSIX;
import jnr.posix.POSIXFactory;
import jnr.posix.util.Platform;
import org.assertj.core.api.AbstractPathAssert;

/**
 * A set of assertions to test properties of {@link Path} using native calls, with fallback
 * implementations for non-POSIX systems.
 */
@SuppressWarnings("UnusedReturnValue")
public final class PosixPathAssert extends AbstractPathAssert<PosixPathAssert> {

  public PosixPathAssert(final Path actual) {
    super(actual, PosixPathAssert.class);
  }

  public static PosixPathAssert assertThat(final Path path) {
    return new PosixPathAssert(path);
  }

  public static PosixPathAssert assertThat(final File file) {
    return assertThat(file.toPath());
  }

  /**
   * Verifies that the real file size in bytes is strictly less than the expected size in bytes.
   *
   * @param sizeInBytes the strict upper bound of the real file size
   * @return itself for chaining
   */
  public PosixPathAssert hasRealSizeLessThan(final long sizeInBytes) {
    isNotNull().isRegularFile();

    final var realSize = getRealSize(actual);
    if (realSize >= sizeInBytes) {
      throw failure(
          "%nExpected file%n  <%s>%nto have a real size less than%n  <%d> bytes%nbut "
              + "it was%n  <%d> bytes (<%d> more bytes)",
          actual, sizeInBytes, realSize, realSize - sizeInBytes);
    }

    return this;
  }

  /**
   * Verifies the real file size in bytes of actual is equal to expected. If the given expected size
   * is not a multiple of the I/O block size, it will be rounded up to the nearest greater multiple,
   * as the real size will always be a multiple of the block size.
   *
   * @param sizeInBytes the expected size in bytes; may be rounded up to the nearest block size
   * @throws AssertionError if actual is null
   * @throws AssertionError if the path does not point to a regular file (e.g. pipe, directory,
   *     socket)
   * @throws AssertionError if the real size (in terms of allocated blocks) is less than the
   *     expected size (rounded up to the nearest block size multiple)
   * @return itself for chaining
   */
  public PosixPathAssert hasRealSize(final long sizeInBytes) {
    isNotNull().isRegularFile();

    if (Platform.IS_WINDOWS) {
      return hasSize(sizeInBytes);
    }

    final var expectedSize = roundUpToBlockSize(sizeInBytes);
    final var realSize = getRealSize(actual);
    // could use paths.assertHasSize, but I feel this error message is more descriptive
    if (expectedSize != realSize) {
      throw failure(
          "%nExpected file%n  <%s>%nto have a real size of%n  <%d> bytes (or <%d> "
              + "bytes rounded up to the block size)%nbut it was %n  <%d> bytes (diff <%d> bytes)",
          actual, expectedSize, sizeInBytes, realSize, expectedSize - realSize);
    }

    return myself;
  }

  /**
   * Returns the actual size of the file on disk by checking the blocks allocated for this file. On
   * most modern UNIX systems, doing {@link Files#size(Path)} returns that size as reported by the
   * file's metadata, which may not be the real size (e.g. compressed file systems, sparse files,
   * etc.). Using the {@code lstat} function from the C library we can get the actual size on disk
   * of the file.
   *
   * <p>{@code lstat} will return the number of 512-bytes blocks used by a file. To get the real
   * size, you simply multiply by 512. Note that unless your file size is aligned with the block
   * size of your device, then the real size may be slightly larger, as more blocks may have been
   * allocated.
   *
   * <p>NOTE: on Windows, sparse files are not the default, so {@link File#length()} is appropriate.
   * Plus, there is no {@code lstat} function, and the equivalent function {@code wstat} does not
   * return the number of blocks.
   *
   * @param file the file to get the size of
   * @return the actual size on disk of the file
   */
  private long getRealSize(final Path file) {
    if (Platform.IS_WINDOWS) {
      try {
        return Files.size(file);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    final POSIX posixFunctions = POSIXFactory.getNativePOSIX();
    final var pathString = file.toString();
    final FileStat stat = posixFunctions.stat(pathString);

    return stat.blocks() * 512;
  }

  /**
   * Returns the I/O block size of the device containing the given file. This can be used to compute
   * an upper bound for the real file size. On Windows, as we use {@link Files#size(Path)} for the
   * real size, this simply returns 0.
   *
   * @param file the file to get the block size of
   * @return the I/O block size of the device containing the file
   */
  private long getBlockSize(final Path file) {
    if (Platform.IS_WINDOWS) {
      return 0;
    }

    final POSIX posixFunctions = POSIXFactory.getNativePOSIX();
    final var pathString = file.toString();
    final FileStat stat = posixFunctions.stat(pathString);

    return stat.blockSize();
  }

  private long roundUpToBlockSize(final long expectedSize) {
    final var blockSize = getBlockSize(actual);
    return (expectedSize + blockSize - 1) - (expectedSize + blockSize - 1) % blockSize;
  }
}
