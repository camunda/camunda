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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NavigableSet;
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
      return new DbSnapshotChunkReader(directory, collectChunks(directory));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private NavigableSet<CharSequence> collectChunks(final Path directory) throws IOException {
    final var set = new TreeSet<>(CharSequence::compare);
    try (var stream = Files.list(directory)) {
      stream.map(directory::relativize).map(Path::toString).forEach(set::add);
    }
    return set;
  }

  private static final class Singleton {
    private static final DbSnapshotChunkReaderFactory INSTANCE = new DbSnapshotChunkReaderFactory();
  }
}
