/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static io.camunda.zeebe.broker.partitioning.startup.RaftPartitionFactory.*;
import static org.slf4j.LoggerFactory.getLogger;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.broker.partitioning.topology.PartitionDistribution;
import io.camunda.zeebe.broker.partitioning.topology.StaticConfigurationGenerator;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Provides a simple validator that verifies that data for partitions to be restored are not empty
 * and topology file is restored.
 */
public class RestoreValidator {

  private static final Logger LOGGER = getLogger(RestoreValidator.class);

  /**
   * Validates that the restore was successful by checking that partition directories are not empty
   * and topology file is restored. This is not a comprehensive validation of the restore, for
   * example it cannot detect partial restores or whether the restored data is correct, but it can
   * be used as a basic sanity check after the restore process completes.
   */
  public static boolean validate(final BrokerCfg configuration) {
    final var localBrokerId = configuration.getCluster().getNodeId();
    final var localMember = MemberId.from(String.valueOf(localBrokerId));
    final var clusterTopology =
        new PartitionDistribution(
            StaticConfigurationGenerator.getStaticConfiguration(configuration, localMember)
                .generatePartitionDistribution());

    final var partitionsToRestore =
        clusterTopology.partitions().stream()
            .filter(partitionMetadata -> partitionMetadata.members().contains(localMember))
            .collect(Collectors.toSet());
    for (final var partitionMetadata : partitionsToRestore) {
      if (!checkPartitionDirectoryIsNotEmpty(
          partitionMetadata, configuration.getData().getDirectory())) {
        LOGGER.error(
            "Expected to find restored partition {} on broker {}, but the partition directory {} is empty or does not exist.",
            partitionMetadata.id(),
            localBrokerId,
            getPartitionDirectory(partitionMetadata.id(), configuration.getData().getDirectory()));
        return false;
      }
    }

    if (!checkTopologyFileIsRestored(configuration)) {
      LOGGER.error(
          "Expected to find restored topology file on broker {}, but it is missing in the data directory {}.",
          localBrokerId,
          configuration.getData().getDirectory());
      return false;
    }
    return true;
  }

  private static boolean checkPartitionDirectoryIsNotEmpty(
      final PartitionMetadata partitionMetadata, final String rootDataDirectory) {
    final var partitionDirectory = getPartitionDirectory(partitionMetadata.id(), rootDataDirectory);
    final var directory = partitionDirectory.toFile();
    final var files = directory.listFiles();
    return directory.exists() && directory.isDirectory() && files != null && files.length > 0;
  }

  private static boolean checkTopologyFileIsRestored(final BrokerCfg configuration) {
    if (configuration.getCluster().getNodeId() == 0) {
      final var topologyFile =
          Path.of(configuration.getData().getDirectory())
              .resolve(ClusterConfigurationManagerService.TOPOLOGY_FILE_NAME);
      return topologyFile.toFile().exists() && topologyFile.toFile().isFile();
    }
    return true;
  }
}
