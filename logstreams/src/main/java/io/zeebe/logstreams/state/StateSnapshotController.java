/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
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
package io.zeebe.logstreams.state;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.processor.SnapshotChunk;
import io.zeebe.logstreams.processor.SnapshotReplication;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private final StateStorage storage;
  private final SnapshotReplication replication;
  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeDb db;

  public StateSnapshotController(final ZeebeDbFactory rocksDbFactory, final StateStorage storage) {
    this(rocksDbFactory, storage, new NoneSnapshotReplication());
  }

  public StateSnapshotController(
      ZeebeDbFactory zeebeDbFactory, StateStorage storage, SnapshotReplication replication) {
    this.storage = storage;
    this.replication = replication;
    this.zeebeDbFactory = zeebeDbFactory;
  }

  @Override
  public void takeSnapshot(long lowerBoundSnapshotPosition) {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(lowerBoundSnapshotPosition);
    db.createSnapshot(snapshotDir);
  }

  @Override
  public void takeTempSnapshot() {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    final File snapshotDir = storage.getTempSnapshotDirectory();
    LOG.debug("Take temporary snapshot and write into {}.", snapshotDir.getAbsolutePath());
    db.createSnapshot(snapshotDir);
  }

  @Override
  public void moveValidSnapshot(long lowerBoundSnapshotPosition) throws IOException {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    final File previousLocation = storage.getTempSnapshotDirectory();
    if (!previousLocation.exists()) {
      throw new IllegalStateException(
          String.format(
              "Temporary snapshot directory %s does not exist.",
              previousLocation.getAbsolutePath()));
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(lowerBoundSnapshotPosition);
    if (snapshotDir.exists()) {
      return;
    }

    LOG.debug(
        "Snapshot is valid. Move snapshot from {} to {}.",
        previousLocation.getAbsolutePath(),
        snapshotDir.getAbsolutePath());

    Files.move(previousLocation.toPath(), snapshotDir.toPath());
  }

  public void replicateLatestSnapshot(Consumer<Runnable> executor) {
    final List<File> snapshots = storage.listByPositionDesc();

    if (snapshots != null && !snapshots.isEmpty()) {
      final File latestSnapshotDirectory = snapshots.get(0);
      LOG.debug("Start replicating latest snapshot {}", latestSnapshotDirectory.toPath());
      final long snapshotPosition = Long.parseLong(latestSnapshotDirectory.getName());

      final File[] files = latestSnapshotDirectory.listFiles();
      for (File snapshotChunk : files) {
        executor.accept(
            () -> {
              try {
                LOG.debug("Replicate snapshot chunk {}", snapshotChunk.toPath());
                final byte[] content = Files.readAllBytes(snapshotChunk.toPath());
                replication.replicate(
                    new SnapshotChunkImpl(
                        snapshotPosition, files.length, snapshotChunk.getName(), content));
              } catch (IOException ioe) {
                LOG.error(
                    "Unexpected error on reading snapshot chunk from file '{}'.",
                    snapshotChunk,
                    ioe);
              }
            });
      }
    }
  }

  public void consumeReplicatedSnapshots() {
    replication.consume(
        (snapshotChunk -> {
          final String snapshotName = Long.toString(snapshotChunk.getSnapshotPosition());
          LOG.debug("Consume snapshot chunk {}", snapshotName);
          final File tmpSnapshotDirectory = storage.getTmpSnapshotDirectoryFor(snapshotName);

          if (!tmpSnapshotDirectory.exists()) {
            tmpSnapshotDirectory.mkdirs();
          }

          final File snapshotFile = new File(tmpSnapshotDirectory, snapshotChunk.getChunkName());
          if (!snapshotFile.exists()) {
            writeReceivedSnapshotChunk(snapshotChunk, tmpSnapshotDirectory, snapshotFile);
          } else {
            LOG.debug("Received a snapshot file which already exist '{}'.", snapshotFile);
          }
        }));
  }

  private void writeReceivedSnapshotChunk(
      SnapshotChunk snapshotChunk, File tmpSnapshotDirectory, File snapshotFile) {
    try {
      Files.write(
          snapshotFile.toPath(), snapshotChunk.getContent(), CREATE_NEW, StandardOpenOption.WRITE);
      LOG.debug("Wrote replicated snapshot chunk to file {}", snapshotFile.toPath());
    } catch (IOException ioe) {
      LOG.error(
          "Unexpected error occurred on writing an snapshot chunk to '{}'.", snapshotFile, ioe);
    }

    try {
      final int totalChunkCount = snapshotChunk.getTotalCount();
      final int currentChunks = tmpSnapshotDirectory.listFiles().length;

      if (currentChunks == totalChunkCount) {
        final File validSnapshotDirectory =
            storage.getSnapshotDirectoryFor(snapshotChunk.getSnapshotPosition());
        LOG.debug(
            "Received all snapshot chunks ({}/{}), snapshot is valid. Move to {}",
            currentChunks,
            totalChunkCount,
            validSnapshotDirectory.toPath());
        Files.move(tmpSnapshotDirectory.toPath(), validSnapshotDirectory.toPath());
      } else {
        LOG.debug(
            "Waiting for more snapshot chunks, currently have {}/{}.",
            currentChunks,
            totalChunkCount);
      }
    } catch (IOException ioe) {
      LOG.error(
          "Unexpected error occurred on moving replicated snapshot from '{}'.",
          tmpSnapshotDirectory.toPath(),
          ioe);
    }
  }

  @Override
  public long recover() throws Exception {
    final File runtimeDirectory = storage.getRuntimeDirectory();

    if (runtimeDirectory.exists()) {
      FileUtil.deleteFolder(runtimeDirectory.getAbsolutePath());
    }

    final List<File> snapshots = storage.listByPositionDesc();
    LOG.debug("Available snapshots: {}", snapshots);

    long lowerBoundSnapshotPosition = -1;

    final Iterator<File> snapshotIterator = snapshots.iterator();
    while (snapshotIterator.hasNext() && lowerBoundSnapshotPosition < 0) {
      final File snapshotDirectory = snapshotIterator.next();

      FileUtil.copySnapshot(runtimeDirectory, snapshotDirectory);

      try {
        // open database to verify that the snapshot is recoverable
        openDb();

        LOG.debug("Recovered state from snapshot '{}'", snapshotDirectory);

        lowerBoundSnapshotPosition = Long.parseLong(snapshotDirectory.getName());

      } catch (Exception e) {
        FileUtil.deleteFolder(runtimeDirectory.getAbsolutePath());

        if (snapshotIterator.hasNext()) {
          LOG.warn(
              "Failed to open snapshot '{}'. Delete this snapshot and try the previous one.",
              snapshotDirectory,
              e);
          FileUtil.deleteFolder(snapshotDirectory.getAbsolutePath());

        } else {
          LOG.error(
              "Failed to open snapshot '{}'. No snapshots available to recover from. Manual action is required.",
              snapshotDirectory,
              e);
          throw new RuntimeException("Failed to recover from snapshots", e);
        }
      }
    }

    return lowerBoundSnapshotPosition;
  }

  @Override
  public ZeebeDb openDb() {
    if (db == null) {
      final File runtimeDirectory = storage.getRuntimeDirectory();
      db = zeebeDbFactory.createDb(runtimeDirectory);
      LOG.debug("Opened database from '{}'.", runtimeDirectory.toPath());
    }

    return db;
  }

  @Override
  public void ensureMaxSnapshotCount(int maxSnapshotCount) throws Exception {
    final List<File> snapshots = storage.listByPositionAsc();
    if (snapshots.size() > maxSnapshotCount) {
      LOG.debug(
          "Ensure max snapshot count {}, will delete {} snapshot(s).",
          maxSnapshotCount,
          snapshots.size() - maxSnapshotCount);

      final List<File> snapshotsToRemove =
          snapshots.subList(0, snapshots.size() - maxSnapshotCount);

      for (final File snapshot : snapshotsToRemove) {
        FileUtil.deleteFolder(snapshot.toPath());
        LOG.debug("Purged snapshot {}", snapshot);
      }
    } else {
      LOG.debug(
          "Tried to ensure max snapshot count {}, nothing to do snapshot count is {}.",
          maxSnapshotCount,
          snapshots.size());
    }
  }

  @Override
  public void close() throws Exception {
    if (db != null) {
      db.close();
      final File runtimeDirectory = storage.getRuntimeDirectory();
      LOG.debug("Closed database from '{}'.", runtimeDirectory.toPath());
      db = null;
    }
  }

  public boolean isDbOpened() {
    return db != null;
  }

  private final class SnapshotChunkImpl implements SnapshotChunk {
    private final long snapshotPosition;
    private final int totalCount;
    private final String chunkName;
    private final byte[] content;

    SnapshotChunkImpl(long snapshotPosition, int totalCount, String chunkName, byte[] content) {
      this.snapshotPosition = snapshotPosition;
      this.totalCount = totalCount;
      this.chunkName = chunkName;
      this.content = content;
    }

    public long getSnapshotPosition() {
      return snapshotPosition;
    }

    @Override
    public String getChunkName() {
      return chunkName;
    }

    @Override
    public int getTotalCount() {
      return totalCount;
    }

    public byte[] getContent() {
      return content;
    }
  }
}
