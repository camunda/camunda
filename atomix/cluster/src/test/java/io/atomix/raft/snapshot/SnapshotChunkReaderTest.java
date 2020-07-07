/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.snapshot;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.raft.snapshot.impl.FileBasedSnapshotStoreFactory;
import io.atomix.utils.time.WallClockTimestamp;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.ChecksumUtil;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SnapshotChunkReaderTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  private PersistedSnapshotStore persistedSnapshotStore;

  @Before
  public void before() {
    final FileBasedSnapshotStoreFactory factory = new FileBasedSnapshotStoreFactory();
    final String partitionName = "1";
    final File root = temporaryFolder.getRoot();

    persistedSnapshotStore = factory.createSnapshotStore(root.toPath(), partitionName);
  }

  @Test
  public void shouldReadSnapshotInChunks() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

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
    assertThat(snapshotChunkIds).hasSize(3);
    assertThat(snapshotChunks).hasSize(3);

    assertThat(snapshotChunkIds)
        .containsExactly(asByteBuffer("file1"), asByteBuffer("file2"), asByteBuffer("file3"));

    final var path = persistedSnapshot.getPath();
    final var paths =
        Arrays.stream(Objects.requireNonNull(path.toFile().listFiles()))
            .sorted()
            .map(File::toPath)
            .collect(Collectors.toList());
    final var expectedSnapshotChecksum = ChecksumUtil.createCombinedChecksum(paths);

    // chunks should always read in order
    assertSnapshotChunk(expectedSnapshotChecksum, snapshotChunks.get(0), "file1", "this");
    assertSnapshotChunk(expectedSnapshotChecksum, snapshotChunks.get(1), "file2", "is");
    assertSnapshotChunk(expectedSnapshotChecksum, snapshotChunks.get(2), "file3", "content");
  }

  @Test
  public void shouldSeekToChunk() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

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

    final var path = persistedSnapshot.getPath();
    final var paths =
        Arrays.stream(Objects.requireNonNull(path.toFile().listFiles()))
            .sorted()
            .map(File::toPath)
            .collect(Collectors.toList());
    final var expectedSnapshotChecksum = ChecksumUtil.createCombinedChecksum(paths);

    // chunks should always read in order
    assertSnapshotChunk(expectedSnapshotChecksum, snapshotChunks.get(0), "file2", "is");
    assertSnapshotChunk(expectedSnapshotChecksum, snapshotChunks.get(1), "file3", "content");
  }

  @Test
  public void shouldThrowExceptionOnReachingLimit() throws Exception {
    // given
    final var index = 1L;
    final var term = 0L;
    final var time = WallClockTimestamp.from(123);
    final var transientSnapshot = persistedSnapshotStore.newTransientSnapshot(index, term, time);
    transientSnapshot.take(
        p -> takeSnapshot(p, List.of("file3", "file1", "file2"), List.of("content", "this", "is")));
    final var persistedSnapshot = transientSnapshot.persist();

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
      final String chunkContent) {
    assertThat(snapshotChunk.getSnapshotId()).isEqualTo("1-0-123");
    assertThat(snapshotChunk.getChunkName()).isEqualTo(fileName);
    assertThat(snapshotChunk.getContent()).isEqualTo(chunkContent.getBytes());
    assertThat(snapshotChunk.getTotalCount()).isEqualTo(3);
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
