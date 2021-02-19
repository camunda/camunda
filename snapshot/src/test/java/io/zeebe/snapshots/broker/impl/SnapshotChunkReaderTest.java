/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.snapshots.broker.impl;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.protocol.Protocol;
import io.zeebe.snapshots.broker.ConstructableSnapshotStore;
import io.zeebe.snapshots.raft.SnapshotChunk;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.ActorScheduler;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SnapshotChunkReaderTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private ConstructableSnapshotStore persistedSnapshotStore;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(createActorScheduler(), 1);
    final int partitionId = 1;
    final File root = temporaryFolder.getRoot();

    factory.createReceivableSnapshotStore(root.toPath(), partitionId);
    persistedSnapshotStore = factory.getConstructableSnapshotStore(partitionId);
  }

  private ActorScheduler createActorScheduler() {
    final var actorScheduler = ActorScheduler.newActorScheduler().build();
    actorScheduler.start();
    return actorScheduler;
  }

  @Test
  public void shouldReadSnapshotInChunks() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var snapshotChunks = new ArrayList<SnapshotChunk>();
    final var snapshotChunkIds = new ArrayList<ByteBuffer>();
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      while (snapshotChunkReader.hasNext()) {
        snapshotChunkIds.add(snapshotChunkReader.nextId());
        snapshotChunks.add(snapshotChunkReader.next());
      }
    }

    // then
    assertThat(snapshotChunkIds).hasSize(4); // 3 snapshot files + 1 checksum
    assertThat(snapshotChunks).hasSize(4);

    assertThat(snapshotChunkIds)
        .containsExactly(
            asByteBuffer("CHECKSUM"),
            asByteBuffer("file1"),
            asByteBuffer("file2"),
            asByteBuffer("file3"));

    final var expectedSnapshotChecksum = SnapshotChecksum.calculate(persistedSnapshot.getPath());

    // chunks should always read in order
    assertSnapshotChunk(
        expectedSnapshotChecksum,
        snapshotChunks.get(1),
        "file1",
        "this",
        persistedSnapshot.getId());
    assertSnapshotChunk(
        expectedSnapshotChecksum, snapshotChunks.get(2), "file2", "is", persistedSnapshot.getId());
    assertSnapshotChunk(
        expectedSnapshotChecksum,
        snapshotChunks.get(3),
        "file3",
        "content",
        persistedSnapshot.getId());
  }

  @Test
  public void shouldSeekToChunk() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var snapshotChunks = new ArrayList<SnapshotChunk>();
    final var snapshotChunkIds = new ArrayList<ByteBuffer>();
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      snapshotChunkReader.seek(asByteBuffer("file2"));
      while (snapshotChunkReader.hasNext()) {
        snapshotChunkIds.add(snapshotChunkReader.nextId());
        snapshotChunks.add(snapshotChunkReader.next());
      }
    }

    // then
    assertThat(snapshotChunkIds).hasSize(2);
    assertThat(snapshotChunks).hasSize(2);

    assertThat(snapshotChunkIds).containsExactly(asByteBuffer("file2"), asByteBuffer("file3"));

    final var expectedSnapshotChecksum = SnapshotChecksum.calculate(persistedSnapshot.getPath());

    // chunks should always read in order
    assertSnapshotChunk(
        expectedSnapshotChecksum, snapshotChunks.get(0), "file2", "is", persistedSnapshot.getId());
    assertSnapshotChunk(
        expectedSnapshotChecksum,
        snapshotChunks.get(1),
        "file3",
        "content",
        persistedSnapshot.getId());
  }

  @Test
  public void shouldThrowExceptionOnReachingLimit() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot =
        persistedSnapshotStore.newTransientSnapshot(index, term, 1, 0).get();
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist().join();

    // when
    final var snapshotChunkReader = persistedSnapshot.newChunkReader();
    while (snapshotChunkReader.hasNext()) {
      snapshotChunkReader.next();
    }

    // then
    assertThat(snapshotChunkReader.hasNext()).isFalse();
    assertThat(snapshotChunkReader.nextId()).isNull();

    assertThatThrownBy(snapshotChunkReader::next).isInstanceOf(NoSuchElementException.class);
  }

  private void assertSnapshotChunk(
      final long expectedSnapshotChecksum,
      final SnapshotChunk snapshotChunk,
      final String fileName,
      final String chunkContent,
      final String snapshotId) {
    assertThat(snapshotChunk.getSnapshotId()).isEqualTo(snapshotId);
    assertThat(snapshotChunk.getChunkName()).isEqualTo(fileName);
    assertThat(snapshotChunk.getContent()).isEqualTo(chunkContent.getBytes());
    assertThat(snapshotChunk.getTotalCount()).isEqualTo(4);
    final var crc32 = new CRC32();
    crc32.update(asByteBuffer(chunkContent));
    assertThat(snapshotChunk.getChecksum()).isEqualTo(crc32.getValue());

    assertThat(snapshotChunk.getSnapshotChecksum()).isEqualTo(expectedSnapshotChecksum);
  }

  private ByteBuffer asByteBuffer(final String string) {
    return ByteBuffer.wrap(string.getBytes()).order(Protocol.ENDIANNESS);
  }

  private boolean takeSnapshot(
      final Path path, final List<String> fileNames, final List<String> fileContents) {
    assertThat(fileNames).hasSize(fileContents.size());

    try {
      FileUtil.ensureDirectoryExists(path);

      for (int i = 0; i < fileNames.size(); i++) {
        final var fileName = fileNames.get(i);
        final var fileContent = fileContents.get(i);
        Files.write(
            path.resolve(fileName), fileContent.getBytes(), CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }
}
