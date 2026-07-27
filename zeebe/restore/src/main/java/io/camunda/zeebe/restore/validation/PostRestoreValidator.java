/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.validation;

import static org.slf4j.LoggerFactory.getLogger;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;

public class PostRestoreValidator {
  private static final Logger LOGGER = getLogger(PostRestoreValidator.class);

  private final MemberId memberId;
  private final Map<PartitionMetadata, Path> partitionsToRestore;
  private final Path rootDataDirectory;

  public PostRestoreValidator(
      final MemberId memberId,
      final Map<PartitionMetadata, Path> partitionsToRestore,
      final Path rootDataDirectory) {
    this.memberId = memberId;
    this.partitionsToRestore = partitionsToRestore;
    this.rootDataDirectory = rootDataDirectory;
  }

  /**
   * Validates that the restore was successful by checking that partition directories are not empty
   * and topology file is restored. This is not a comprehensive validation of the restore, for
   * example it cannot detect partial restores or whether the restored data is correct, but it can
   * be used as a basic sanity check after the restore process completes.
   */
  public boolean verifyRestore() {
    for (final var partitionEntry : partitionsToRestore.entrySet()) {
      if (!checkPartitionDirectoryIsNotEmpty(partitionEntry.getValue())) {
        LOGGER.error(
            "Expected to find restored partition {} on broker {}, but the partition directory {} is empty or does not exist.",
            partitionEntry.getKey().id(),
            memberId,
            partitionEntry.getValue());
        return false;
      }
    }

    if (!checkTopologyFileIsRestored()) {
      LOGGER.error(
          "Expected to find restored topology file on broker {}, but it is missing in the data directory {}.",
          memberId,
          rootDataDirectory);
      return false;
    }
    return true;
  }

  private boolean checkPartitionDirectoryIsNotEmpty(final Path partitionDirectory) {
    final var directory = partitionDirectory.toFile();
    final var files = directory.listFiles();
    return directory.exists() && directory.isDirectory() && files != null && files.length > 0;
  }

  private boolean checkTopologyFileIsRestored() {
    if (memberId.nodeIdx() == 0) {
      final var topologyFile =
          rootDataDirectory.resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
      return topologyFile.toFile().exists() && topologyFile.toFile().isFile();
    }
    return true;
  }
}
