/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.system.configuration.RocksdbCfg;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeValidator;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects a change that would give a broker more partitions than its RocksDB memory configuration
 * allows, since that broker could no longer restart. Brokers are assumed to share the coordinator's
 * memory and RocksDB configuration.
 */
@NullMarked
public final class RocksDbMemoryChangeValidator implements ClusterConfigurationChangeValidator {

  private final RocksdbCfg rocksdbCfg;

  public RocksDbMemoryChangeValidator(final RocksdbCfg rocksdbCfg) {
    this.rocksdbCfg = rocksdbCfg;
  }

  @Override
  public Optional<String> validate(
      final CurrentClusterConfiguration current, final CurrentClusterConfiguration proposed) {
    final var partitionsBefore = partitionsPerMember(current);
    return partitionsPerMember(proposed).entrySet().stream()
        .filter(e -> e.getValue() > partitionsBefore.getOrDefault(e.getKey(), 0))
        .max(Entry.comparingByValue())
        .flatMap(e -> validatePartitionCount(e.getKey(), e.getValue()));
  }

  private Optional<String> validatePartitionCount(
      final MemberId memberId, final int partitionCount) {
    try {
      rocksdbCfg.validateRocksDbMemory(partitionCount);
      return Optional.empty();
    } catch (final IllegalArgumentException e) {
      return Optional.of(
          String.format(
              "Expected broker %s to be able to restart with %d partitions after this change,"
                  + " but its RocksDB memory configuration does not allow it: %s Increase the"
                  + " broker memory or the RocksDB memory configuration before applying this"
                  + " change.",
              memberId.id(), partitionCount, e.getMessage()));
    }
  }

  /** Counts partitions the same way a broker does when it restarts. */
  private static Map<MemberId, Integer> partitionsPerMember(
      final CurrentClusterConfiguration configuration) {
    return ConfigurationUtil.getPartitionDistributionPerPhysicalTenant(configuration)
        .values()
        .stream()
        .flatMap(partitions -> partitions.stream())
        .flatMap(partition -> partition.members().stream())
        .collect(Collectors.toMap(member -> member, member -> 1, Integer::sum));
  }
}
