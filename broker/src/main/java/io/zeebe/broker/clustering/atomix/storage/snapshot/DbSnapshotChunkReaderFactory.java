/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix.storage.snapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.SortedSet;
import java.util.TreeSet;

/** Creates {@link DbSnapshotChunkReader} from a {@link DbSnapshot}. */
final class DbSnapshotChunkReaderFactory {
  private DbSnapshotChunkReaderFactory() {}

  /** @return a static, stateless instance of the factory */
  static DbSnapshotChunkReaderFactory factory() {
    return Singleton.INSTANCE;
  }

  /**
   * Creates a new {@link DbSnapshotChunkReader} by walking the snapshot's directory recursively,
   * collecting the files using their name relative to the directory, and ordering them
   * lexicographically.
   *
   * @param snapshot the snapshot to create a reader for
   * @return a new {@link DbSnapshotChunkReader} for this {@code snapshot}
   * @throws java.io.UncheckedIOException if any file cannot be visited (e.g. read, opened, etc.)
   */
  DbSnapshotChunkReader ofSnapshot(final DbSnapshot snapshot) {
    final var directory = snapshot.getDirectory();

    try {
      return new DbSnapshotChunkReader(directory, visitChunks(directory));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private SortedSet<CharSequence> visitChunks(final Path directory) throws IOException {
    final var set = new TreeSet<>(CharSequence::compare);
    Files.walkFileTree(directory, new ChunkVisitor(directory, set));
    return set;
  }

  private static final class ChunkVisitor extends SimpleFileVisitor<Path> {
    private final Path root;
    private final SortedSet<CharSequence> collector;

    ChunkVisitor(final Path root, final SortedSet<CharSequence> collector) {
      this.root = root;
      this.collector = collector;
    }

    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
      collector.add(file.relativize(root).toString());
      return FileVisitResult.CONTINUE;
    }
  }

  private static final class Singleton {
    private static final DbSnapshotChunkReaderFactory INSTANCE = new DbSnapshotChunkReaderFactory();
  }
}
