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
package io.zeebe.logstreams.impl.snapshot.fs;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.spi.SnapshotMetadata;
import io.zeebe.logstreams.spi.SnapshotStorage;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class FsSnapshotStorage implements SnapshotStorage {
  public static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  protected final FsSnapshotStorageConfiguration cfg;

  public FsSnapshotStorage(FsSnapshotStorageConfiguration cfg) {
    this.cfg = cfg;
  }

  @Override
  public FsReadableSnapshot getLastSnapshot(final String name) {
    final File rootFile = new File(cfg.getRootPath());
    final List<File> snapshotFiles =
        Arrays.asList(rootFile.listFiles(file -> cfg.matchesSnapshotFileNamePattern(file, name)));
    FsReadableSnapshot snapshot = null;

    if (!snapshotFiles.isEmpty()) {
      final List<File> committedSortedSnapshotFiles =
          snapshotFiles
              .stream()
              .filter((f) -> isCommitted(f, name))
              .sorted(Comparator.comparingLong((f) -> position(f, name)))
              .collect(Collectors.toList());

      if (!committedSortedSnapshotFiles.isEmpty()) {
        final File snapshotFile = committedSortedSnapshotFiles.get(0);
        final long logPosition = position(snapshotFile, name);
        final String checksumFileName = cfg.checksumFileName(name, logPosition);
        final File checksumFile = new File(checksumFileName);

        snapshot = new FsReadableSnapshot(cfg, snapshotFile, checksumFile, logPosition);
      }
    }

    return snapshot;
  }

  @Override
  public FsSnapshotWriter createSnapshot(String name, long logPosition) throws Exception {
    final FsReadableSnapshot lastSnapshot = getLastSnapshot(name);

    final String snapshotFileName = cfg.snapshotFileName(name, logPosition);
    final String checksumFileName = cfg.checksumFileName(name, logPosition);

    final File snapshotFile = new File(snapshotFileName);
    final File checksumFile = new File(checksumFileName);

    if (snapshotFile.exists()) {
      throw new RuntimeException(
          String.format(
              "Cannot write snapshot %s, file already exists.", snapshotFile.getAbsolutePath()));
    }

    try {
      snapshotFile.createNewFile();
    } catch (IOException e) {
      FileUtil.deleteFile(snapshotFile);
      throw e;
    }

    return new FsSnapshotWriter(cfg, snapshotFile, checksumFile, lastSnapshot);
  }

  @Override
  public List<SnapshotMetadata> listSnapshots() {
    final File rootFile = new File(cfg.getRootPath());
    final File[] snapshotFiles = rootFile.listFiles(cfg::isSnapshotFile);
    final ArrayList<SnapshotMetadata> snapshots = new ArrayList<>();

    if (snapshotFiles != null) {
      snapshots.ensureCapacity(snapshotFiles.length);
      for (final File snapshotFile : snapshotFiles) {
        final String snapshotName = cfg.getSnapshotNameFromFileName(snapshotFile.getName());

        if (isCommitted(snapshotFile, snapshotName)) {
          final File checksumFile = getChecksumFile(snapshotFile, snapshotName);
          final long logPosition = position(snapshotFile, snapshotName);
          try {
            final SnapshotMetadata snapshotMetadata =
                new FsSnapshotMetadata(cfg, snapshotFile, checksumFile, logPosition);
            snapshots.add(snapshotMetadata);
          } catch (final Exception ex) {
            LOG.error("Failed to read snapshot metadata", ex);
          }
        }
      }
    }

    return snapshots;
  }

  @Override
  public boolean snapshotExists(String name, long logPosition) {
    final File snapshotFile = new File(cfg.snapshotFileName(name, logPosition));
    return Files.exists(snapshotFile.toPath()) && isCommitted(snapshotFile, name);
  }

  protected long position(File file, String snapshotName) {
    return cfg.getPositionOfSnapshotFile(file, snapshotName);
  }

  private File getChecksumFile(final File snapshotFile, final String snapshotName) {
    final long logPosition = position(snapshotFile, snapshotName);
    final String checksumFileName = cfg.checksumFileName(snapshotName, logPosition);
    return new File(checksumFileName);
  }

  private boolean isCommitted(final File snapshotFile, final String name) {
    final File checksumFile = getChecksumFile(snapshotFile, name);
    return checksumFile.exists();
  }
}
