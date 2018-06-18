/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.raft;

import io.zeebe.broker.clustering.base.partitions.PartitionAlreadyExistsException;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.ByteValue;
import io.zeebe.util.FileUtil;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;

/**
 * Manages {@link RaftPersistentConfiguration} instances. When the broker is started, it loads the
 * stored files. Knows where to put new configuration files when a new raft is started.
 */
public class RaftPersistentConfigurationManager extends Actor {
  private static final String PARTITION_METAFILE_NAME = "partition.json";
  private static final String PARTITION_LOG_DIR = "segments";
  private static final String PARTITION_SNAPSHOTS_DIR = "snapshots";

  private final List<RaftPersistentConfiguration> configurations = new ArrayList<>();
  private final DataCfg dataConfiguration;

  private final int[] partitionCountPerDataDirectory;

  public RaftPersistentConfigurationManager(DataCfg dataConfiguration) {
    this.dataConfiguration = dataConfiguration;
    this.partitionCountPerDataDirectory = new int[dataConfiguration.getDirectories().length];
  }

  @Override
  protected void onActorStarting() {
    final String[] directories = dataConfiguration.getDirectories();

    for (int i = 0; i < directories.length; i++) {
      readConfigurations(directories[i], i);
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
        final File snapshotsDirectory = new File(partitionDirectory, PARTITION_SNAPSHOTS_DIR);
        configurations.add(
            new RaftPersistentConfiguration(configFile, logDirectory, snapshotsDirectory));
        partitionCountPerDataDirectory[offset]++;
      }
    }
  }

  public ActorFuture<List<RaftPersistentConfiguration>> getConfigurations() {
    return actor.call(() -> new ArrayList<>(configurations));
  }

  public ActorFuture<RaftPersistentConfiguration> createConfiguration(
      DirectBuffer topicName, int partitionId, int replicationFactor, List<SocketAddress> members) {
    final ActorFuture<RaftPersistentConfiguration> future = new CompletableActorFuture<>();

    actor.run(
        () -> {
          final boolean partitionExists =
              configurations.stream().anyMatch((config) -> config.getPartitionId() == partitionId);

          if (partitionExists) {
            future.completeExceptionally(new PartitionAlreadyExistsException(partitionId));
          } else {
            final String partitionName =
                String.format("%s-%d", BufferUtil.bufferAsString(topicName), partitionId);

            final int assignedDataDirOffset = assignDataDirectory();
            final String assignedDataDirectoryName =
                dataConfiguration.getDirectories()[assignedDataDirOffset];
            final File partitionDirectory = new File(assignedDataDirectoryName, partitionName);

            try {
              partitionDirectory.mkdir();

              final File metafile = new File(partitionDirectory, PARTITION_METAFILE_NAME);

              final File logDirectory = new File(partitionDirectory, PARTITION_LOG_DIR);
              logDirectory.mkdir();

              final File snapshotDirectory = new File(partitionDirectory, PARTITION_SNAPSHOTS_DIR);
              snapshotDirectory.mkdir();

              final RaftPersistentConfiguration storage =
                  new RaftPersistentConfiguration(metafile, logDirectory, snapshotDirectory);

              storage
                  .setTopicName(topicName)
                  .setPartitionId(partitionId)
                  .setReplicationFactor(replicationFactor)
                  .setMembers(members)
                  .setLogSegmentSize(
                      new ByteValue(dataConfiguration.getDefaultLogSegmentSize()).toBytes())
                  .save();

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

  public ActorFuture<Void> deleteConfiguration(RaftPersistentConfiguration configuration) {
    return actor.call(
        () -> {
          configurations.remove(configuration);
          configuration.delete();
        });
  }

  public ActorFuture<Void> close() {
    return actor.close();
  }
}
