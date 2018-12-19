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

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.SKIP_SUBTREE;

import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateSnapshotController implements SnapshotController {
  private static final Logger LOG = Loggers.ROCKSDB_LOGGER;

  private final StateStorage storage;
  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeDb db;

  public StateSnapshotController(final ZeebeDbFactory rocksDbFactory, final StateStorage storage) {
    zeebeDbFactory = rocksDbFactory;
    this.storage = storage;
  }

  @Override
  public void takeSnapshot(final StateSnapshotMetadata metadata) {
    if (db == null) {
      throw new IllegalStateException("Cannot create snapshot of not open database.");
    }

    if (exists(metadata)) {
      return;
    }

    final File snapshotDir = storage.getSnapshotDirectoryFor(metadata);
    db.createSnapshot(snapshotDir);
  }

  @Override
  public StateSnapshotMetadata recover(
      long commitPosition, int term, Predicate<StateSnapshotMetadata> filter) throws Exception {
    final File runtimeDirectory = storage.getRuntimeDirectory();
    final List<StateSnapshotMetadata> snapshots = storage.listRecoverable(commitPosition);
    StateSnapshotMetadata recoveredMetadata = null;

    if (!snapshots.isEmpty()) {
      recoveredMetadata =
          snapshots
              .stream()
              .sorted(Comparator.reverseOrder())
              .filter(filter)
              .findFirst()
              .orElse(null);
    }

    if (runtimeDirectory.exists()) {
      FileUtil.deleteFolder(runtimeDirectory.getAbsolutePath());
    }

    if (recoveredMetadata != null) {
      final File snapshotPath = storage.getSnapshotDirectoryFor(recoveredMetadata);
      copySnapshot(runtimeDirectory, snapshotPath);
    } else {
      recoveredMetadata = StateSnapshotMetadata.createInitial(term);
    }

    return recoveredMetadata;
  }

  @Override
  public ZeebeDb openDb() {
    db = zeebeDbFactory.createDb(storage.getRuntimeDirectory());
    return db;
  }

  @Override
  public void purgeAll(Predicate<StateSnapshotMetadata> matcher) throws Exception {
    final List<StateSnapshotMetadata> others = storage.list(matcher);

    for (final StateSnapshotMetadata other : others) {
      FileUtil.deleteFolder(storage.getSnapshotDirectoryFor(other).getAbsolutePath());
      LOG.trace("Purged snapshot {}", other);
    }
  }

  private boolean exists(final StateSnapshotMetadata metadata) {
    return storage.getSnapshotDirectoryFor(metadata).exists();
  }

  private void copySnapshot(File runtimeDirectory, File snapshotPath) throws Exception {
    final Path targetPath = runtimeDirectory.toPath();
    final Path sourcePath = snapshotPath.toPath();
    Files.walkFileTree(sourcePath, new SnapshotCopier(sourcePath, targetPath));
  }

  @Override
  public void close() throws Exception {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  public boolean isDbOpened() {
    return db != null;
  }

  private static final class SnapshotCopier extends SimpleFileVisitor<Path> {

    private final Path targetPath;
    private final Path sourcePath;

    SnapshotCopier(Path sourcePath, Path targetPath) {
      this.sourcePath = sourcePath;
      this.targetPath = targetPath;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      final Path newDirectory = targetPath.resolve(sourcePath.relativize(dir));
      try {
        Files.copy(dir, newDirectory);
      } catch (FileAlreadyExistsException ioException) {
        LOG.error("Problem on copying snapshot to runtime.", ioException);
        return SKIP_SUBTREE; // skip processing
      }

      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      final Path newFile = targetPath.resolve(sourcePath.relativize(file));

      try {
        Files.copy(file, newFile);
      } catch (IOException ioException) {
        LOG.error("Problem on copying snapshot to runtime.", ioException);
      }

      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
      return CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      LOG.error("Problem on copying snapshot to runtime.", exc);
      return CONTINUE;
    }
  }
}
