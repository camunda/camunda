/*
 * Copyright © 2020  camunda services GmbH (info@camunda.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package io.atomix.raft.snapshot.impl;

import io.atomix.raft.snapshot.SnapshotChunk;
import io.atomix.raft.snapshot.SnapshotChunkReader;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.ChecksumUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Implements a chunk reader where each chunk is a single file in a root directory. Chunks are then
 * ordered lexicographically, and the files are assumed to be immutable, i.e. no more are added to
 * the directory once this is created.
 */
public final class FileBasedSnapshotChunkReader implements SnapshotChunkReader {
  static final Charset ID_CHARSET = StandardCharsets.US_ASCII;

  private final Path directory;
  private final NavigableSet<CharSequence> chunks;
  private final CharSequenceView chunkIdView;

  private NavigableSet<CharSequence> chunksView;
  private final int totalCount;
  private final long snapshotChecksum;
  private final String snapshotID;

  FileBasedSnapshotChunkReader(final Path directory) throws IOException {
    this.directory = directory;
    this.chunks = collectChunks(directory);
    this.totalCount = chunks.size();
    this.chunksView = this.chunks;
    this.chunkIdView = new CharSequenceView();

    try (final var fileStream = Files.list(directory).sorted()) {
      this.snapshotChecksum =
          ChecksumUtil.createCombinedChecksum(fileStream.collect(Collectors.toList()));
    }

    this.snapshotID = directory.getFileName().toString();
  }

  private NavigableSet<CharSequence> collectChunks(final Path directory) throws IOException {
    final var set = new TreeSet<>(CharSequence::compare);
    try (final var stream = Files.list(directory).sorted()) {
      stream.map(directory::relativize).map(Path::toString).forEach(set::add);
    }
    return set;
  }

  @Override
  public void seek(final ByteBuffer id) {
    if (id == null) {
      return;
    }

    final var path = decodeChunkId(id);
    chunksView = chunks.tailSet(path, true);
  }

  @Override
  public ByteBuffer nextId() {
    if (chunksView.isEmpty()) {
      return null;
    }

    return encodeChunkId(chunksView.first());
  }

  @Override
  public void close() {
    chunks.clear();
    chunksView.clear();
  }

  @Override
  public boolean hasNext() {
    return !chunksView.isEmpty();
  }

  @Override
  public SnapshotChunk next() {
    final var chunkName = chunksView.pollFirst();
    if (chunkName == null) {
      throw new NoSuchElementException();
    }

    final var path = directory.resolve(chunkName.toString());

    try {
      return SnapshotChunkUtil.createSnapshotChunkFromFile(
          path.toFile(), snapshotID, totalCount, snapshotChecksum);
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ByteBuffer encodeChunkId(final CharSequence path) {
    return ByteBuffer.wrap(path.toString().getBytes(ID_CHARSET)).order(Protocol.ENDIANNESS);
  }

  private CharSequence decodeChunkId(final ByteBuffer id) {
    return chunkIdView.wrap(id);
  }

  private static final class CharSequenceView {
    private final DirectBuffer wrapper = new UnsafeBuffer();
    private final AsciiSequenceView view = new AsciiSequenceView();

    private CharSequence wrap(final ByteBuffer buffer) {
      wrapper.wrap(buffer);
      return view.wrap(wrapper, 0, wrapper.capacity());
    }
  }
}
