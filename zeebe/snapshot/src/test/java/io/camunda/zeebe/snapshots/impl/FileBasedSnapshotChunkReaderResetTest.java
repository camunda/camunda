/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class FileBasedSnapshotChunkReaderResetTest {

  @TempDir Path directory;

  @BeforeEach
  void createSnapshot() throws IOException {
    Files.writeString(directory.resolve("first"), "abcd");
    Files.writeString(directory.resolve("second"), "0123456789");
  }

  @Test
  void shouldRetryFirstPartialChunkAfterReset() throws IOException {
    // given
    try (final var reader = new FileBasedSnapshotChunkReader(directory, 3)) {
      final var initialId = nextChunkId(reader);
      final var initialChunk = reader.next();

      // when
      reader.reset();

      // then
      assertThat(nextChunkId(reader)).isEqualTo(initialId);
      final var retriedChunk = reader.next();
      assertThat(retriedChunk.getChunkName()).isEqualTo(initialChunk.getChunkName());
      assertThat(retriedChunk.getFileBlockPosition()).isZero();
      assertThat(retriedChunk.getContent()).isEqualTo(initialChunk.getContent());
      assertThat(retriedChunk.getChecksum()).isEqualTo(initialChunk.getChecksum());
      assertThat(reader.next().getContent()).isEqualTo("d".getBytes(StandardCharsets.UTF_8));
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {3, 6})
  void shouldResetToFirstFileAfterReadingPartialLaterFile(final long offset) throws IOException {
    // given
    try (final var reader = new FileBasedSnapshotChunkReader(directory, 3)) {
      reader.seek(new SnapshotChunkId("second", offset).id());
      reader.next();

      // when
      reader.reset();

      // then
      assertThat(nextChunkId(reader)).isEqualTo("first__0");
      final var chunk = reader.next();
      assertThat(chunk.getChunkName()).isEqualTo("first");
      assertThat(chunk.getFileBlockPosition()).isZero();
      assertThat(chunk.getContent()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test
  void shouldResetAfterReadingCompleteFile() throws IOException {
    // given
    try (final var reader = new FileBasedSnapshotChunkReader(directory, 3)) {
      reader.next();
      reader.next();

      // when
      reader.reset();

      // then
      assertThat(nextChunkId(reader)).isEqualTo("first__0");
      assertThat(reader.next().getContent()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
    }
  }

  private static String nextChunkId(final FileBasedSnapshotChunkReader reader) {
    return new SnapshotChunkId(requireNonNull(reader.nextId(), "Expected a next chunk ID"))
        .toString();
  }
}
