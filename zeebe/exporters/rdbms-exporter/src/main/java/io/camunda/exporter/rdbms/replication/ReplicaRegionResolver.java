/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.RegionConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Maps a replica's self-declared label (see {@link
 * io.camunda.db.rdbms.read.replication.ReplicationStatus#replicaLabel()}) to the region it belongs
 * to, using the ordered regex patterns from the configured {@link RegionConfiguration} list.
 * Patterns are compiled once at construction time; the first matching region wins.
 *
 * <p>A {@code null} label is matched as the empty string, so a catch-all region (pattern {@code
 * ".*"}) still counts a replica that reports no label at all - this is what lets a flat {@code
 * minSyncReplicas} (converted into a single catch-all region, see {@code RdbmsAsyncReplication} in
 * the {@code configuration} module) behave exactly like the pre-region-awareness flat count.
 */
final class ReplicaRegionResolver {

  private final List<CompiledRegion> regions;

  ReplicaRegionResolver(final List<RegionConfiguration> regions) {
    this.regions = regions.stream().map(CompiledRegion::of).toList();
  }

  /**
   * @return the name of the first configured region whose pattern matches {@code replicaLabel}, or
   *     empty if it matches no configured region
   */
  Optional<String> resolve(final String replicaLabel) {
    final String label = replicaLabel == null ? "" : replicaLabel;
    return regions.stream()
        .filter(region -> region.pattern().matcher(label).matches())
        .map(CompiledRegion::name)
        .findFirst();
  }

  private record CompiledRegion(String name, Pattern pattern) {
    static CompiledRegion of(final RegionConfiguration config) {
      return new CompiledRegion(config.getName(), Pattern.compile(config.getPattern()));
    }
  }
}
