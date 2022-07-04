/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sun.jna.platform.linux.ErrNo;
import io.camunda.zeebe.util.error.OutOfDiskSpaceException;
import io.camunda.zeebe.util.fs.LibC.InvalidLibC;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.util.stream.Stream;
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
final class NativeFSTest {
  private @TempDir Path tmpDir;

  // while a fairly simple test, it's important that this does not break
  @Test
  void shouldDisablePosixFallocate() {
    // given
    final var libC = new NativeFS(LibC.ofNativeLibrary());
    libC.disablePosixFallocate();

    // then
    assertThat(libC.supportsPosixFallocate()).isFalse();
  }

  @Test
  void shouldAllocateFile() throws IOException {
    // given
    final var libC = new NativeFS(LibC.ofNativeLibrary());
    final var path = tmpDir.resolve("file");
    final var length = 1024 * 1024;

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      libC.posixFallocate(file.getFD(), 0, length);
    }

    // then
    assertThat(path).hasSize(length);
  }

  @Test
  void shouldFailWithNegativeOffset() throws IOException {
    // given
    final var libC = new NativeFS(LibC.ofNativeLibrary());
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> libC.posixFallocate(file.getFD(), -10, 100))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void shouldFailWithNegativeLength() throws IOException {
    // given
    final var libC = new NativeFS(LibC.ofNativeLibrary());
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> libC.posixFallocate(file.getFD(), 0, -100))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @ParameterizedTest(name = "{0} => {1}")
  @MethodSource("provideErrorPairs")
  void shouldMapErrNoToException(final int errno, final Class<? extends Exception> exception)
      throws IOException {
    // given
    final var libC = new NativeFS(new FailingPosixFallocate(errno));
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> libC.posixFallocate(file.getFD(), 0, 1024 * 1024))
          .isInstanceOf(exception);
    }
  }

  @Test
  void shouldDisablePosixFallocateOnInvalidFS() throws IOException {
    // given
    final var libC = new NativeFS(new FailingPosixFallocate(ErrNo.EINVAL));
    final var path = tmpDir.resolve("file");

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
      assertThatCode(() -> libC.posixFallocate(file.getFD(), 0, 1024 * 1024))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    // then
    assertThat(libC.supportsPosixFallocate()).isFalse();
  }

  private static Stream<Arguments> provideErrorPairs() {
    return Stream.of(
        Arguments.of(ErrNo.EBADF, IOException.class),
        Arguments.of(ErrNo.EFBIG, IOException.class),
        Arguments.of(ErrNo.EINTR, InterruptedIOException.class),
        Arguments.of(ErrNo.EINVAL, UnsupportedOperationException.class),
        Arguments.of(ErrNo.ENODEV, IOException.class),
        Arguments.of(ErrNo.ESPIPE, IOException.class),
        Arguments.of(ErrNo.ENOSPC, OutOfDiskSpaceException.class));
  }

  private static final class FailingPosixFallocate extends InvalidLibC {
    private final int errno;

    private FailingPosixFallocate(final int errno) {
      this.errno = errno;
    }

    @Override
    public int posix_fallocate(final int fd, final long offset, final long len) {
      return errno;
    }
  }
}
