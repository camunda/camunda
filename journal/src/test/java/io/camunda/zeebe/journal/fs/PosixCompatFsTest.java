/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.journal.fs;

import io.camunda.zeebe.journal.util.PosixPathAssert;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
final class PosixCompatFsTest {
  @Test
  void shouldPreallocateFile(final @TempDir Path tmpDir) throws IOException {
    // given
    final var path = tmpDir.resolve("file");
    final var length = 1024 * 1024L;
    final var posixFs = new PosixCompatFs();

    // when
    try (final RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
        final FileChannel channel =
            FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      posixFs.preallocate(file.getFD(), channel, length);
    }

    // then
    PosixPathAssert.assertThat(path).hasRealSize(length);
  }
}
