/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.atomix.protocols.raft.storage.snapshot.SnapshotChunk;
import io.atomix.protocols.raft.storage.snapshot.SnapshotChunkReader;
import io.zeebe.protocol.Protocol;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;
import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.LoggerFactory;

/**
 * Implements a chunk reader where each chunk is a single file in a root directory. Chunks are then
 * ordered lexicographically, and the files are assumed to be immutable, i.e. no more are added to
 * the directory once this is created.
 */
public class DbSnapshotChunkReader implements SnapshotChunkReader {
  private static final Charset ID_CHARSET = StandardCharsets.US_ASCII;
  private final Path directory;
  private final NavigableSet<CharSequence> chunks;
  private final CharSequenceView chunkIdView;

  private NavigableSet<CharSequence> chunksView;

  public DbSnapshotChunkReader(final Path directory, final NavigableSet<CharSequence> chunks) {
    this.directory = directory;
    this.chunks = chunks;
    this.chunksView = this.chunks;
    this.chunkIdView = new CharSequenceView();
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
    // nothing to do
  }

  @Override
  public boolean hasNext() {
    return !chunksView.isEmpty();
  }

  @Override
  public SnapshotChunk next() {
    final var id = chunksView.pollFirst();
    if (id == null) {
      throw new NoSuchElementException();
    }

    final var path = directory.resolve(id.toString());

    try {
      final var data = ByteBuffer.wrap(Files.readAllBytes(path)).order(Protocol.ENDIANNESS);
      return new DbSnapshotChunk(encodeChunkId(id), data);
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
