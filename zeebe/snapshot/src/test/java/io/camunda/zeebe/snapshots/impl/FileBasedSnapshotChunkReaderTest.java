/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.snapshots.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.snapshots.SnapshotChunk;
import io.camunda.zeebe.util.FileUtil;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class FileBasedSnapshotChunkReaderTest {

  private static final long SNAPSHOT_CHECKSUM = 1L;
  private static final Map<String, String> SNAPSHOT_CHUNK =
      Map.of("file3", "content", "file1", "this", "file2", "is");

  private static final List<Entry<String, String>> SORTED_CHUNKS =
      SNAPSHOT_CHUNK.entrySet().stream()
          .sorted(Entry.comparingByKey())
          .collect(Collectors.toUnmodifiableList());
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
    assertThatThrownBy(reader::next).hasCauseInstanceOf(FileNotFoundException.class);
  }

  @Test
  public void shouldThrowExceptionWhenNoDirectoryExist() throws IOException {
    // given
    final var reader = newReader();

    // when
    FileUtil.deleteFolder(snapshotDirectory);

    // then
    assertThatThrownBy(reader::next).hasCauseInstanceOf(FileNotFoundException.class);
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

        final var chunkId = chunk.getChunkName() + "__" + chunk.getFileBlockPosition();
        assertThat(ByteBuffer.wrap(chunkId.getBytes(StandardCharsets.UTF_8)))
            .isNotNull()
            .isEqualTo(nextId);
        assertThat(chunk.getSnapshotId()).isEqualTo(snapshotDirectory.getFileName().toString());
        assertThat(chunk.getTotalCount()).isEqualTo(SNAPSHOT_CHUNK.size());
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
        .containsExactly(
            asByteBuffer("file1__0"), asByteBuffer("file2__0"), asByteBuffer("file3__0"));

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
      snapshotChunkReader.seek(asByteBuffer("file2__0"));
      while (snapshotChunkReader.hasNext()) {
        snapshotChunkIds.add(snapshotChunkReader.next().getChunkName());
      }
    }

    // then
    assertThat(snapshotChunkIds).containsExactly("file2", "file3");
  }

  @Test
  public void shouldResetToInitialChunk() throws IOException {
    // given
    final var snapshotChunkIds = new ArrayList<String>();
    try (final var snapshotChunkReader = newReader()) {
      snapshotChunkReader.seek(asByteBuffer("file2__0"));
      snapshotChunkReader.next();

      // when
      snapshotChunkReader.reset();

      while (snapshotChunkReader.hasNext()) {
        snapshotChunkIds.add(snapshotChunkReader.next().getChunkName());
      }
    }

    // then
    assertThat(snapshotChunkIds).containsExactly("file1", "file2", "file3");
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

  @Test
  public void shouldSplitFileContentsIntoChunks() throws IOException {
    // given
    final int maxChunkSize = 3;
    final var snapshotChunkReader = newReader(maxChunkSize);
    final var snapshotChunks = getAllChunks(snapshotChunkReader);

    // when - then
    final var fileNameBytesMap = new HashMap<String, ByteBuffer>();
    for (final var entry : SNAPSHOT_CHUNK.entrySet()) {
      final var contentBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
      fileNameBytesMap.put(entry.getKey(), ByteBuffer.allocate(contentBytes.length));
    }

    for (final var chunk : snapshotChunks) {
      fileNameBytesMap.get(chunk.getChunkName()).put(chunk.getContent());
    }

    for (final var entry : SNAPSHOT_CHUNK.entrySet()) {
      final var contentBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
      assertThat(fileNameBytesMap.get(entry.getKey()).array()).isEqualTo(contentBytes);
    }
  }

  @Test
  public void shouldHaveCorrectFileBlockFieldsForSplitFiles() throws IOException { // given final
    // given
    final int maxChunkSize = 3;
    final var snapshotChunkReader = newReader(maxChunkSize);
    final var snapshotChunks = getAllChunks(snapshotChunkReader);

    // when - then
    final var fileChunksGroupedByFileName =
        snapshotChunks.stream().collect(Collectors.groupingBy(SnapshotChunk::getChunkName));

    for (final var entry : fileChunksGroupedByFileName.entrySet()) {
      final var fileName = entry.getKey();
      final var fileContentsBytesLength =
          SNAPSHOT_CHUNK.get(fileName).getBytes(StandardCharsets.UTF_8).length;
      final var expectedFileBlocksCount = Math.ceilDiv(fileContentsBytesLength, maxChunkSize);

      final var fileBlocks = entry.getValue();
      assertThat(fileBlocks.size()).isEqualTo(expectedFileBlocksCount);
      assertThat(fileBlocks.stream().map(SnapshotChunk::getFileBlockPosition).toList())
          .isEqualTo(
              LongStream.range(0, expectedFileBlocksCount)
                  .map(l -> l * maxChunkSize)
                  .boxed()
                  .toList());
    }
  }

  @Test
  public void shouldHaveCorrectNextChunkIdForSplitFiles() throws IOException {
    // given
    final int maxChunkSize = 3;
    final var snapshotChunkReader = newReader(maxChunkSize);

    // when
    final var snapshotChunkIds = new ArrayList<ByteBuffer>();

    while (snapshotChunkReader.hasNext()) {
      snapshotChunkIds.add(snapshotChunkReader.nextId());
      snapshotChunkReader.next();
    }

    // then
    final var expectedChunkIds = new ArrayList<ByteBuffer>();
    Collections.addAll(
        expectedChunkIds,
        ByteBuffer.wrap("file1__0".getBytes()),
        ByteBuffer.wrap("file1__3".getBytes()),
        ByteBuffer.wrap("file2__0".getBytes()),
        ByteBuffer.wrap("file3__0".getBytes()),
        ByteBuffer.wrap("file3__3".getBytes()),
        ByteBuffer.wrap("file3__6".getBytes()));

    assertThat(snapshotChunkIds).isEqualTo(expectedChunkIds);
  }

  @Test
  public void shouldStartSplittingFilesOnceChunkSizeIsSet() throws IOException {
    final int maxChunkSize = 3;
    final var snapshotChunkReader = newReader();
    final var snapshotChunks = new ArrayList<SnapshotChunk>();

    // when
    for (int i = 0; i < 2; i++) {
      snapshotChunks.add(snapshotChunkReader.next());
    }

    snapshotChunkReader.setMaximumChunkSize(maxChunkSize);
    while (snapshotChunkReader.hasNext()) {
      snapshotChunks.add(snapshotChunkReader.next());
    }

    // then
    final var fileChunksGroupedByFileName =
        snapshotChunks.stream().collect(Collectors.groupingBy(SnapshotChunk::getChunkName));

    assertThat(fileChunksGroupedByFileName.get("file1").size()).isEqualTo(1);
    assertThat(fileChunksGroupedByFileName.get("file2").size()).isEqualTo(1);
    assertThat(fileChunksGroupedByFileName.get("file3").size()).isEqualTo(3);
  }

  @Test
  public void shouldSeekCorrectlyWhenFileChunkingEnabled() throws IOException {
    final int maxChunkSize = 3;
    final var snapshotChunkReader = newReader(maxChunkSize);
    final var snapshotChunks = new ArrayList<SnapshotChunk>();
    final var firstId = snapshotChunkReader.nextId();

    // when
    while (snapshotChunkReader.hasNext()) {
      snapshotChunks.add(snapshotChunkReader.next());
    }

    snapshotChunkReader.seek(firstId);
    final var snapshotChunksAfterSeek = new ArrayList<SnapshotChunk>();

    while (snapshotChunkReader.hasNext()) {
      snapshotChunksAfterSeek.add(snapshotChunkReader.next());
    }

    assertThat(
            snapshotChunksAfterSeek.stream()
                .map(SnapshotChunk::getContent)
                .collect(Collectors.toUnmodifiableList()))
        .containsExactlyElementsOf(
            snapshotChunks.stream()
                .map(SnapshotChunk::getContent)
                .collect(Collectors.toUnmodifiableList()));
  }

  @Test
  public void shouldSeekSameByteBufferTwiceWithNoError() throws IOException {
    // given

    final var buffer = ByteBuffer.wrap("file3__0".getBytes(StandardCharsets.UTF_8));
    final var snapshotChunkReader = newReader();

    // when
    snapshotChunkReader.seek(buffer);
    final var chunkFromFirstSeek = snapshotChunkReader.next();

    snapshotChunkReader.seek(buffer);
    final var chunkFromSecondSeek = snapshotChunkReader.next();

    // then
    assertThat(chunkFromFirstSeek.getChunkName()).isEqualTo(chunkFromSecondSeek.getChunkName());
    assertThat(chunkFromFirstSeek.getContent()).isEqualTo(chunkFromSecondSeek.getContent());
    assertThat(chunkFromFirstSeek.getChecksum()).isEqualTo(chunkFromSecondSeek.getChecksum());
  }

  private List<SnapshotChunk> getAllChunks(final FileBasedSnapshotChunkReader reader) {
    final var snapshotChunks = new ArrayList<SnapshotChunk>();

    while (reader.hasNext()) {
      snapshotChunks.add(reader.next());
    }

    return snapshotChunks;
  }

  private ByteBuffer asByteBuffer(final String string) {
    return ByteBuffer.wrap(string.getBytes()).order(Protocol.ENDIANNESS);
  }

  private FileBasedSnapshotChunkReader newReader(final long chunkSize) throws IOException {
    snapshotDirectory = temporaryFolder.getRoot().toPath();

    for (final var chunk : SNAPSHOT_CHUNK.keySet()) {
      final var path = snapshotDirectory.resolve(chunk);
      Files.createFile(path);
      Files.writeString(path, SNAPSHOT_CHUNK.get(chunk));
    }

    return new FileBasedSnapshotChunkReader(snapshotDirectory, chunkSize);
  }

  private FileBasedSnapshotChunkReader newReader() throws IOException {
    return newReader(Long.MAX_VALUE);
  }
}
