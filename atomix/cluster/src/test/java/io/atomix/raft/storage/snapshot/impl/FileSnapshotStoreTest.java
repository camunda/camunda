/*
 * Copyright 2015-present Open Networking Foundation
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
package io.atomix.raft.storage.snapshot.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import io.atomix.raft.storage.RaftStorage;
import io.atomix.raft.storage.snapshot.Snapshot;
import io.atomix.raft.storage.snapshot.SnapshotStore;
import io.atomix.storage.StorageLevel;
import io.atomix.utils.time.WallClockTimestamp;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Snapshot store test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class FileSnapshotStoreTest {

  private String testId;

  /** Tests storing and loading snapshots. */
  @Test
  public void testStoreLoadSnapshot() {
    SnapshotStore store = createSnapshotStore();

    final Snapshot snapshot = store.newSnapshot(2, 3, new WallClockTimestamp());
    try (final SnapshotWriter writer = snapshot.openWriter()) {
      writer.writeLong(10);
    }
    snapshot.complete();
    assertNotNull(store.getSnapshot(2));
    store.close();

    store = createSnapshotStore();
    assertNotNull(store.getSnapshot(2));
    assertEquals(2, store.getSnapshot(2).index());
    assertEquals(3, store.getSnapshot(2).term());

    try (final SnapshotReader reader = snapshot.openReader()) {
      assertEquals(10, reader.readLong());
    }
  }

  /** Returns a new snapshot store. */
  protected SnapshotStore createSnapshotStore() {
    final File directory = new File(String.format("target/test-logs/%s", testId));
    final DefaultSnapshotStore store = new DefaultSnapshotStore(directory.toPath(), "test");
    final RaftStorage storage =
        RaftStorage.builder()
            .withPrefix("test")
            .withDirectory(directory)
            .withSnapshotStore(store)
            .withStorageLevel(StorageLevel.DISK)
            .build();
    return new DefaultSnapshotStore(directory.toPath(), "test");
  }

  /** Tests persisting and loading snapshots. */
  @Test
  public void testPersistLoadSnapshot() {
    SnapshotStore store = createSnapshotStore();

    Snapshot snapshot = store.newSnapshot(2, 3, new WallClockTimestamp());
    try (final SnapshotWriter writer = snapshot.openWriter()) {
      writer.writeLong(10);
    }

    assertNull(store.getSnapshot(2));
    snapshot.complete();
    assertNotNull(store.getSnapshot(2));

    try (final SnapshotReader reader = snapshot.openReader()) {
      assertEquals(10, reader.readLong());
    }

    store.close();

    store = createSnapshotStore();
    assertNotNull(store.getSnapshot(2));
    assertEquals(2, store.getSnapshot(2).index());

    snapshot = store.getSnapshot(2);
    try (final SnapshotReader reader = snapshot.openReader()) {
      assertEquals(10, reader.readLong());
    }
  }

  /**
   * Tests writing multiple times to a snapshot designed to mimic chunked snapshots from leaders.
   */
  @Test
  public void testStreamSnapshot() {
    final SnapshotStore store = createSnapshotStore();

    Snapshot snapshot = store.newSnapshot(1, 1, new WallClockTimestamp());
    for (long i = 1; i <= 10; i++) {
      try (final SnapshotWriter writer = snapshot.openWriter()) {
        writer.writeLong(i);
      }
    }
    snapshot.complete();

    snapshot = store.getSnapshot(1);
    try (final SnapshotReader reader = snapshot.openReader()) {
      for (long i = 1; i <= 10; i++) {
        assertEquals(i, reader.readLong());
      }
    }
  }

  /** Tests case where two {@link FileSnapshot} instances are trying to write the same snapshot */
  @Test
  public void testConcurrentSnapshotWriters() {
    final SnapshotStore store = createSnapshotStore();
    final WallClockTimestamp timestamp = new WallClockTimestamp();
    final Snapshot first = store.newSnapshot(1, 1, timestamp);
    final Snapshot second = store.newSnapshot(1, 1, timestamp);

    try (final SnapshotWriter firstWriter = first.openWriter()) {
      firstWriter.writeLong(1);
    }

    try (final SnapshotWriter secondWriter = second.openWriter()) {
      secondWriter.writeLong(1);
    }

    first.complete();
    second.complete();

    final Snapshot completed = store.getSnapshot(first.index());
    assertNotNull(completed);
    long result = 0;
    try (final SnapshotReader reader = completed.openReader()) {
      while (reader.hasRemaining()) {
        result += reader.readLong();
      }
    }

    assertEquals(result, 1);
  }

  @Before
  @After
  public void cleanupStorage() throws IOException {
    final Path directory = Paths.get("target/test-logs/");
    if (Files.exists(directory)) {
      Files.walkFileTree(
          directory,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                throws IOException {
              Files.delete(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException {
              Files.delete(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }
    testId = UUID.randomUUID().toString();
  }

  /** Tests writing a snapshot. */
  @Test
  public void testWriteSnapshotChunks() {
    final SnapshotStore store = createSnapshotStore();
    final WallClockTimestamp timestamp = new WallClockTimestamp();
    final Snapshot snapshot = store.newSnapshot(2, 1, timestamp);
    assertEquals(2, snapshot.index());
    assertEquals(timestamp, snapshot.timestamp());

    assertNull(store.getSnapshot(2));

    try (final SnapshotWriter writer = snapshot.openWriter()) {
      writer.writeLong(10);
    }

    assertNull(store.getSnapshot(2));

    try (final SnapshotWriter writer = snapshot.openWriter()) {
      writer.writeLong(11);
    }

    assertNull(store.getSnapshot(2));

    try (final SnapshotWriter writer = snapshot.openWriter()) {
      writer.writeLong(12);
    }

    assertNull(store.getSnapshot(2));
    snapshot.complete();

    assertEquals(2, store.getSnapshot(2).index());

    try (final SnapshotReader reader = store.getSnapshot(2).openReader()) {
      assertEquals(10, reader.readLong());
      assertEquals(11, reader.readLong());
      assertEquals(12, reader.readLong());
    }
  }
}
