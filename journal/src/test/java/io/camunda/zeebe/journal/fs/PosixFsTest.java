/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.journal.JournalException.OutOfDiskSpace;
import io.camunda.zeebe.journal.fs.LibC.InvalidLibC;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.stream.Stream;
import jnr.constants.platform.Errno;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Windows does not provide any LibC")
@Execution(ExecutionMode.CONCURRENT)
final class PosixFsTest {
  private @TempDir Path tmpDir;

  // while a fairly simple test, it's important that this does not break
  @Test
  void shouldDisablePosixFallocate() {
    // given
    final var posixFs = new PosixFs();
    posixFs.disablePosixFallocate();

    // then
    assertThat(posixFs.isPosixFallocateEnabled()).isFalse();
  }

  @Test
  void shouldPreallocateFile() throws IOException {
    // given
    final var posixFs = new PosixFs();
    final var path = tmpDir.resolve("file");
    final var length = 1024 * 1024;

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      posixFs.posixFallocate(file.getFD(), 0, length);
    }

    // then
    assertThat(path).hasSize(length);
  }

  @Test
  void shouldFailWithNegativeOffset() throws IOException {
    // given
    final var posixFs = new PosixFs();
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> posixFs.posixFallocate(file.getFD(), -10, 100))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void shouldFailWithNegativeLength() throws IOException {
    // given
    final var posixFs = new PosixFs(LibC.ofNativeLibrary());
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> posixFs.posixFallocate(file.getFD(), 0, -100))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @ParameterizedTest(name = "{0} => {1}")
  @MethodSource("provideErrorPairs")
  void shouldMapErrNoToException(final Errno errno, final Class<? extends Exception> exception)
      throws IOException {
    // given
    final var posixFs = new PosixFs(new FailingPosixFallocate(errno));
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> posixFs.posixFallocate(file.getFD(), 0, 1024 * 1024))
          .isInstanceOf(exception);
    }
  }

  @Test
  void shouldDisablePosixFallocateOnInvalidFS() throws IOException {
    // given
    final var posixFs = new PosixFs(new FailingPosixFallocate(Errno.EINVAL));
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> posixFs.posixFallocate(file.getFD(), 0, 1024 * 1024))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    // then
    assertThat(posixFs.isPosixFallocateEnabled()).isFalse();
  }

  private static Stream<Arguments> provideErrorPairs() {
    return Stream.of(
        Arguments.of(Errno.EBADF, IOException.class),
        Arguments.of(Errno.EFBIG, IOException.class),
        Arguments.of(Errno.EINTR, InterruptedIOException.class),
        Arguments.of(Errno.EINVAL, UnsupportedOperationException.class),
        Arguments.of(Errno.ENODEV, IOException.class),
        Arguments.of(Errno.ESPIPE, IOException.class),
        Arguments.of(Errno.ENOSPC, OutOfDiskSpace.class));
  }

  private static final class FailingPosixFallocate extends InvalidLibC {
    private final Errno errno;

    private FailingPosixFallocate(final Errno errno) {
      this.errno = errno;
    }

    @Override
    public int posix_fallocate(final int fd, final long offset, final long len) {
      return errno.intValue();
    }
  }
}
