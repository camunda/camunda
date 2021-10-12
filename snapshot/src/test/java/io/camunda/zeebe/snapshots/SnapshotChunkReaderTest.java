/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.snapshots;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.testing.ActorSchedulerRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SnapshotChunkReaderTest {

  private static final Map<String, String> SNAPSHOT_CHUNK =
      Map.of("file3", "content", "file1", "this", "file2", "is");
  private static final int EXPECTED_CHUNK_COUNT = 3;
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public ActorSchedulerRule scheduler = new ActorSchedulerRule();
  private PersistedSnapshot persistedSnapshot;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory =
        new FileBasedSnapshotStoreFactory(scheduler.get(), 1);
    final int partitionId = 1;
    final Path snapshotDirectory = temporaryFolder.getRoot().toPath();

    factory.createReceivableSnapshotStore(snapshotDirectory, partitionId);
    final var persistedSnapshotStore = factory.getConstructableSnapshotStore(partitionId);

    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(1, 2, 3, 2).get();
    transientSnapshot.take(this::takeSnapshot);
    persistedSnapshot = transientSnapshot.persist().join();
  }

  @Test
  public void shouldReadSnapshotChunks() {
    // given
    final var expectedSnapshotChecksum = persistedSnapshot.getChecksum();

    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      for (int i = 0; i < EXPECTED_CHUNK_COUNT; i++) {
        assertThat(snapshotChunkReader.hasNext()).isTrue();
        // when
        final var nextId = snapshotChunkReader.nextId();
        final var chunk = snapshotChunkReader.next();

        // then
        assertThat(asByteBuffer(chunk.getChunkName())).isNotNull().isEqualTo(nextId);
        assertThat(chunk.getSnapshotId()).isEqualTo(persistedSnapshot.getId());
        assertThat(chunk.getTotalCount()).isEqualTo(EXPECTED_CHUNK_COUNT);
        assertThat(chunk.getSnapshotChecksum()).isEqualTo(expectedSnapshotChecksum);
        assertThat(chunk.getChecksum()).as("the chunk has a checksum").isNotNegative();
      }
    }
  }

  @Test
  public void shouldReadSnapshotChunksInOrder() {
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
    assertThat(snapshotChunkIds)
        .containsExactly(asByteBuffer("file1"), asByteBuffer("file2"), asByteBuffer("file3"));

    assertThat(snapshotChunks)
        .extracting(SnapshotChunk::getContent)
        .extracting(String::new)
        .containsExactly("this", "is", "content");
  }

  @Test
  public void shouldSeekToChunk() {
    // when
    final var snapshotChunkIds = new ArrayList<String>();
    try (final var snapshotChunkReader = persistedSnapshot.newChunkReader()) {
      snapshotChunkReader.seek(asByteBuffer("file2"));
      while (snapshotChunkReader.hasNext()) {
        snapshotChunkIds.add(snapshotChunkReader.next().getChunkName());
      }
    }

    // then
    assertThat(snapshotChunkIds).containsExactly("file2", "file3");
  }

  @Test
  public void shouldThrowExceptionOnReachingLimit() {
    // given
    final var snapshotChunkReader = persistedSnapshot.newChunkReader();
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

  private boolean takeSnapshot(final Path path) {
    try {
      FileUtil.ensureDirectoryExists(path);
      for (final Entry<String, String> entry : SNAPSHOT_CHUNK.entrySet()) {
        final String id = entry.getKey();
        final String content = entry.getValue();
        Files.write(path.resolve(id), content.getBytes(), CREATE_NEW, StandardOpenOption.WRITE);
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }
}
