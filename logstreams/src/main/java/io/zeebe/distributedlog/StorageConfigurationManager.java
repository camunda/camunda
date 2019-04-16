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
package io.zeebe.distributedlog;

import io.zeebe.util.ByteValue;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages {@link StorageConfiguration} instances. When the broker is started, it loads the stored
 * files. Knows where to put new configuration files when a new raft is started.
 */
public class StorageConfigurationManager extends Actor {
  private static final String PARTITION_METAFILE_NAME = "partition.json";
  private static final String PARTITION_LOG_DIR = "segments";
  private static final String PARTITION_STATES_DIR = "state";
  private static final String PARTITION_INDEX_ROOT_DIR = "index";
  private static final String PARTITION_INDEX_RUNTIME_DIR = "runtime";
  private static final String PARTITION_INDEX_SNAPSHOTS_DIR = "snapshots";

  private final List<StorageConfiguration> configurations = new ArrayList<>();

  private final int[] partitionCountPerDataDirectory;
  private final String indexBlockSize;
  private final List<String> directories;
  private final String segmentSize;

  public StorageConfigurationManager(
      List<String> dataDirectories, String segmentSize, final String indexBlockSize) {
    this.directories = dataDirectories;
    this.segmentSize = segmentSize;
    this.partitionCountPerDataDirectory = new int[dataDirectories.size()];
    this.indexBlockSize = indexBlockSize;
  }

  @Override
  protected void onActorStarting() {
    for (int i = 0; i < directories.size(); i++) {
      readConfigurations(directories.get(i), i);
    }
  }

  private void readConfigurations(String dataDirectoryName, int offset) {
    final File dataDirectory = new File(dataDirectoryName);

    final File[] partitionDirectories =
        dataDirectory.listFiles((d, f) -> new File(d, f).isDirectory());

    for (File partitionDirectory : partitionDirectories) {
      final File configFile = new File(partitionDirectory, PARTITION_METAFILE_NAME);

      if (configFile.exists()) {
        final File logDirectory = new File(partitionDirectory, PARTITION_LOG_DIR);
        final File statesDirectory = new File(partitionDirectory, PARTITION_STATES_DIR);
        final File indexDirectory = new File(partitionDirectory, PARTITION_INDEX_ROOT_DIR);
        final File indexRuntimeDirectory = new File(indexDirectory, PARTITION_INDEX_RUNTIME_DIR);
        final File indexSnapshotsDirectory =
            new File(indexDirectory, PARTITION_INDEX_SNAPSHOTS_DIR);

        configurations.add(
            new StorageConfiguration(
                configFile,
                logDirectory,
                indexSnapshotsDirectory,
                statesDirectory,
                indexRuntimeDirectory));
        partitionCountPerDataDirectory[offset]++;
      }
    }
  }

  public ActorFuture<List<StorageConfiguration>> getConfigurations() {
    return actor.call(() -> new ArrayList<>(configurations));
  }

  // get existing or create new
  public ActorFuture<StorageConfiguration> createConfiguration(int partitionId) {
    final ActorFuture<StorageConfiguration> future = new CompletableActorFuture<>();

    actor.run(
        () -> {
          final Optional<StorageConfiguration> partitionConfig =
              configurations.stream()
                  .filter((config) -> config.getPartitionId() == partitionId)
                  .findAny();
          if (partitionConfig.isPresent()) {
            future.complete(partitionConfig.get());
          } else {
            final String partitionName = String.format("partition-%d", partitionId);

            final int assignedDataDirOffset = assignDataDirectory();
            final String assignedDataDirectoryName = directories.get(assignedDataDirOffset);
            final File partitionDirectory = new File(assignedDataDirectoryName, partitionName);

            try {
              partitionDirectory.mkdir();

              final File metafile = new File(partitionDirectory, PARTITION_METAFILE_NAME);

              final File logDirectory = new File(partitionDirectory, PARTITION_LOG_DIR);
              logDirectory.mkdir();

              final File statesDirectory = new File(partitionDirectory, PARTITION_STATES_DIR);
              statesDirectory.mkdir();

              final File indexDirectory = new File(partitionDirectory, PARTITION_INDEX_ROOT_DIR);
              indexDirectory.mkdir();

              final File indexRuntimeDirectory =
                  new File(indexDirectory, PARTITION_INDEX_RUNTIME_DIR);
              indexRuntimeDirectory.mkdir();

              final File indexSnapshotsDirectory =
                  new File(indexDirectory, PARTITION_INDEX_SNAPSHOTS_DIR);
              indexSnapshotsDirectory.mkdir();

              final StorageConfiguration storage =
                  new StorageConfiguration(
                      metafile,
                      logDirectory,
                      indexSnapshotsDirectory,
                      statesDirectory,
                      indexRuntimeDirectory);

              storage
                  .setPartitionId(partitionId)
                  .setLogSegmentSize(new ByteValue(segmentSize).toBytes())
                  .setIndexBlockSize(new ByteValue(indexBlockSize).toBytes());

              configurations.add(storage);

              future.complete(storage);

              partitionCountPerDataDirectory[assignedDataDirOffset]++;
            } catch (Exception e) {
              try {
                // try to deleted partially created dirs / files
                FileUtil.deleteFolder(partitionDirectory.getAbsolutePath());
              } catch (IOException e1) {
                e1.printStackTrace();
              }

              future.completeExceptionally(e);
            }
          }
        });

    return future;
  }

  private int assignDataDirectory() {
    int min = Integer.MAX_VALUE;
    int minOffset = -1;

    for (int i = 0; i < partitionCountPerDataDirectory.length; i++) {
      final int partitionCount = partitionCountPerDataDirectory[i];

      if (partitionCount < min) {
        min = partitionCount;
        minOffset = i;
      }
    }

    return minOffset;
  }

  public ActorFuture<Void> deleteConfiguration(StorageConfiguration configuration) {
    return actor.call(
        () -> {
          configurations.remove(configuration);
        });
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }
}
