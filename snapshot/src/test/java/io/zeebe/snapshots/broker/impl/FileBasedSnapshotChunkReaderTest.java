/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots.broker.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.snapshots.raft.SnapshotChunk;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileBasedSnapshotChunkReaderTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void shouldDoNothingIfSeekNull() {
    // given
    final var reader = newReader(chunksOf("foo", "bar"));

    // when
    reader.seek(null);

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next().getChunkName()).isEqualTo("bar");
    assertThat(reader.nextId()).isEqualTo(asBuffer("foo"));
  }

  @Test
  public void shouldSeekToChunk() {
    // given
    final var reader = newReader(chunksOf("foo", "bar"));

    // when
    reader.seek(asBuffer("foo"));

    // then
    assertThat(reader).hasNext();
    assertThat(reader.next().getChunkName()).isEqualTo("foo");
    assertThat(reader.nextId()).isEqualTo(null);
  }

  @Test
  public void shouldGetNextChunkInOrder() {
    // given
    final var reader = newReader(chunksOf("c", "a", "b"));

    // when - then
    final var chunks = new ArrayList<SnapshotChunk>();
    while (reader.hasNext()) {
      chunks.add(reader.next());
    }

    // then
    assertThat(chunks).extracting(SnapshotChunk::getChunkName).containsExactly("a", "b", "c");
    assertThat(reader.nextId()).isEqualTo(null);
    assertThat(reader.hasNext()).isFalse();
  }

  private ByteBuffer asBuffer(final CharSequence chunk) {
    return ByteBuffer.wrap(chunk.toString().getBytes(FileBasedSnapshotChunkReader.ID_CHARSET));
  }

  private NavigableSet<CharSequence> chunksOf(final CharSequence... chunks) {
    final var set = new TreeSet<>(CharSequence::compare);
    set.addAll(Arrays.asList(chunks));
    return set;
  }

  private FileBasedSnapshotChunkReader newReader(final NavigableSet<CharSequence> chunks) {
    final var directory = temporaryFolder.getRoot().toPath();
    for (final var chunk : chunks) {
      final var path = directory.resolve(chunk.toString());
      try {
        Files.createFile(path);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    try {
      return new FileBasedSnapshotChunkReader(directory, 1);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
