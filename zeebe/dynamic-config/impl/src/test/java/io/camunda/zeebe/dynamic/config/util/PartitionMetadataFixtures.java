/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Shared test fixtures for building {@link PartitionMetadata} instances in reassigner tests. */
final class PartitionMetadataFixtures {

  private PartitionMetadataFixtures() {}

  /**
   * Builds a partition with a real, non-tied priority ladder (primary gets {@code members.size()},
   * every other member gets a strictly lower, distinct priority) so round-tripping through {@link
   * ConfigurationUtil} preserves the given primary exactly.
   */
  static PartitionMetadata partition(
      final String group, final int number, final Set<MemberId> members, final MemberId primary) {
    final Map<MemberId, Integer> priorities = new HashMap<>();
    priorities.put(primary, members.size());
    int nextPriority = members.size() - 1;
    for (final var member : members.stream().sorted().toList()) {
      if (!member.equals(primary)) {
        priorities.put(member, nextPriority--);
      }
    }
    return new PartitionMetadata(
        new PartitionId(group, number), members, priorities, priorities.get(primary), primary);
  }
}
