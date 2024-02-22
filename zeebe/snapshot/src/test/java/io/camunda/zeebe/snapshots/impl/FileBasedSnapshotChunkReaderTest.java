/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileBasedSnapshotChunkReaderTest {

  private static final long SNAPSHOT_CHECKSUM = 1L;
  private static final Map<String, String> SNAPSHOT_CHUNK =
      Map.of("file3", "content", "file1", "this", "file2", "is");

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();
  private Path snapshotDirectory;

  @Test
  public void shouldAssignChunkIdsFromFileNames() throws IOException {
    // given
    final var reader = newReader();

    // when - then
    assertThat(reader.next().getChunkName()).isEqualTo("file1");
    assertThat(reader.next().getChunkName()).isEqualTo("file2");
    assertThat(reader.next().getChunkName()).isEqualTo("file3");
  }

  @Test
  public void shouldThrowExceptionWhenChunkFileDoesNotExist() throws IOException {
    // given
    final var reader = newReader();

    // when
    Files.delete(snapshotDirectory.resolve("file1"));

    // then
    assertThatThrownBy(reader::next).hasCauseInstanceOf(NoSuchFileException.class);
  }

  @Test
  public void shouldThrowExceptionWhenNoDirectoryExist() throws IOException {
    // given
    final var reader = newReader();

    // when
    FileUtil.deleteFolder(snapshotDirectory);

    // then
    assertThatThrownBy(reader::next).hasCauseInstanceOf(NoSuchFileException.class);
  }

  @Test
  public void shouldReadSnapshotChunks() throws IOException {
    // given
    try (final var snapshotChunkReader = newReader()) {
      for (int i = 0; i < SNAPSHOT_CHUNK.size(); i++) {
        assertThat(snapshotChunkReader.hasNext()).isTrue();
        // when
        final var nextId = snapshotChunkReader.nextId();
        final var chunk = snapshotChunkReader.next();

        // then

        assertThat(ByteBuffer.wrap(chunk.getChunkName().getBytes(StandardCharsets.UTF_8)))
            .isNotNull()
            .isEqualTo(nextId);
        assertThat(chunk.getSnapshotId()).isEqualTo(snapshotDirectory.getFileName().toString());
        assertThat(chunk.getTotalCount()).isEqualTo(SNAPSHOT_CHUNK.size());
        assertThat(chunk.getSnapshotChecksum()).isEqualTo(SNAPSHOT_CHECKSUM);
        assertThat(chunk.getChecksum())
            .isEqualTo(SnapshotChunkUtil.createChecksum(chunk.getContent()));
        assertThat(snapshotDirectory.resolve(chunk.getChunkName()))
            .hasBinaryContent(chunk.getContent());
      }
    }
  }

  @Test
  public void shouldReadSnapshotChunksInOrder() throws IOException {
    // when
    final var snapshotChunks = new ArrayList<SnapshotChunk>();
    final var snapshotChunkIds = new ArrayList<ByteBuffer>();
    try (final var snapshotChunkReader = newReader()) {
      while (snapshotChunkReader.hasNext()) {
        snapshotChunkIds.add(snapshotChunkReader.nextId());
        snapshotChunks.add(snapshotChunkReader.next());
      }
    }

    // then
    assertThat(snapshotChunkIds)
        .containsExactly(asByteBuffer("file1"), asByteBuffer("file2"), asByteBuffer("file3"));

    assertThat(snapshotChunks)
        .extracting(SnapshotChunk::getContent)
        .extracting(String::new)
        .containsExactly("this", "is", "content");
  }

  @Test
  public void shouldSeekToChunk() throws IOException {
    // when
    final var snapshotChunkIds = new ArrayList<String>();
    try (final var snapshotChunkReader = newReader()) {
      snapshotChunkReader.seek(asByteBuffer("file2"));
      while (snapshotChunkReader.hasNext()) {
        snapshotChunkIds.add(snapshotChunkReader.next().getChunkName());
      }
    }

    // then
    assertThat(snapshotChunkIds).containsExactly("file2", "file3");
  }

  @Test
  public void shouldThrowExceptionOnReachingLimit() throws IOException {
    // given
    final var snapshotChunkReader = newReader();
    while (snapshotChunkReader.hasNext()) {
      snapshotChunkReader.next();
    }

    assertThat(snapshotChunkReader.nextId()).isNull();

    // when - then
    assertThatThrownBy(snapshotChunkReader::next).isInstanceOf(NoSuchElementException.class);
  }

  private ByteBuffer asByteBuffer(final String string) {
    return ByteBuffer.wrap(string.getBytes()).order(Protocol.ENDIANNESS);
  }

  private FileBasedSnapshotChunkReader newReader() throws IOException {
    snapshotDirectory = temporaryFolder.getRoot().toPath();

    for (final var chunk : SNAPSHOT_CHUNK.keySet()) {
      final var path = snapshotDirectory.resolve(chunk);
      Files.createFile(path);
      Files.writeString(path, SNAPSHOT_CHUNK.get(chunk));
    }

    return new FileBasedSnapshotChunkReader(snapshotDirectory, SNAPSHOT_CHECKSUM);
  }
}
