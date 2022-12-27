/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.journal.JournalException.OutOfDiskSpace;
import io.camunda.zeebe.journal.file.LibC.InvalidLibC;
import io.camunda.zeebe.journal.util.PosixPathAssert;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.stream.Stream;
import jnr.constants.platform.Errno;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledOnOs(value = OS.LINUX, disabledReason = "Tests specific Linux filesystem functions")
@Execution(ExecutionMode.CONCURRENT)
final class LinuxFsTest {
  private @TempDir Path tmpDir;

  // while a fairly simple test, it's important that this does not break
  @Test
  void shouldDisablePosixFallocate() {
    // given
    final var linuxFs = new LinuxFs();
    linuxFs.disableFallocate();

    // then
    assertThat(linuxFs.isFallocateEnabled()).isFalse();
  }

  @Test
  void shouldPreallocateFile() throws IOException {
    // given
    final var linuxFs = new LinuxFs();
    final var path = tmpDir.resolve("file");
    final var length = 1024 * 1024;

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      linuxFs.fallocate(file.getFD(), 0, length);
    }

    // then
    PosixPathAssert.assertThat(path).hasRealSize(length);
  }

  @Test
  void shouldFailWithNegativeOffset() throws IOException {
    // given
    final var linuxFs = new LinuxFs();
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> linuxFs.fallocate(file.getFD(), -10, 100))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void shouldFailWithNegativeLength() throws IOException {
    // given
    final var linuxFs = new LinuxFs(LibC.ofNativeLibrary());
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> linuxFs.fallocate(file.getFD(), 0, -100))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @ParameterizedTest(name = "{0} => {1}")
  @MethodSource("provideErrorPairs")
  void shouldMapErrNoToException(final Errno errno, final Class<? extends Exception> exception)
      throws IOException {
    // given
    final var linuxFs = new LinuxFs(new FailingLinuxFallocate(), errno::intValue);
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> linuxFs.fallocate(file.getFD(), 0, 1024 * 1024)).isInstanceOf(exception);
    }
  }

  private static Stream<Arguments> provideErrorPairs() {
    return Stream.of(
        Arguments.of(Errno.EBADF, IOException.class),
        Arguments.of(Errno.EFBIG, IOException.class),
        Arguments.of(Errno.EINTR, InterruptedIOException.class),
        Arguments.of(Errno.EINVAL, IllegalArgumentException.class),
        Arguments.of(Errno.ENODEV, IOException.class),
        Arguments.of(Errno.ESPIPE, IOException.class),
        Arguments.of(Errno.ENOSPC, OutOfDiskSpace.class));
  }

  private static final class FailingLinuxFallocate extends InvalidLibC {

    @Override
    public int fallocate(final int fd, final int mode, final long offset, final long len) {
      return -1;
    }
  }
}
