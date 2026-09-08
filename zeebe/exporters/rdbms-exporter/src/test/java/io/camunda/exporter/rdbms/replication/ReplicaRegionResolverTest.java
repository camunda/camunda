/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.RegionAwarenessConfiguration;
import io.camunda.exporter.rdbms.ExporterConfiguration.ReplicationConfiguration.RegionConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplicaRegionResolverTest {

  private static RegionConfiguration region(
      final String name, final String pattern, final int minReplicas) {
    final var region = new RegionConfiguration();
    region.setName(name);
    region.setPattern(pattern);
    region.setMinReplicas(minReplicas);
    return region;
  }

  @Test
  void shouldResolveLabelMatchingItsRegionsPattern() {
    // given
    final var config = new RegionAwarenessConfiguration();
    config.setRegions(List.of(region("us-east", "us-east-.*", 1)));
    final var resolver = new ReplicaRegionResolver(config);

    // when
    final var region = resolver.resolve("us-east-1a");

    // then
    assertThat(region).contains("us-east");
  }

  @Test
  void shouldReturnEmptyWhenLabelMatchesNoConfiguredRegion() {
    // given
    final var config = new RegionAwarenessConfiguration();
    config.setRegions(List.of(region("us-east", "us-east-.*", 1)));
    final var resolver = new ReplicaRegionResolver(config);

    // when
    final var region = resolver.resolve("eu-central-1a");

    // then
    assertThat(region).isEmpty();
  }

  @Test
  void shouldReturnEmptyForNullLabel() {
    // given
    final var config = new RegionAwarenessConfiguration();
    config.setRegions(List.of(region("us-east", "us-east-.*", 1)));
    final var resolver = new ReplicaRegionResolver(config);

    // when
    final var region = resolver.resolve(null);

    // then
    assertThat(region).isEmpty();
  }

  @Test
  void shouldResolveToTheFirstMatchingRegionWhenPatternsOverlap() {
    // given - both patterns match "us-east-1a"; the first declared region wins
    final var config = new RegionAwarenessConfiguration();
    config.setRegions(
        List.of(region("us-east-primary", "us-east-1.*", 1), region("us-east", "us-east-.*", 1)));
    final var resolver = new ReplicaRegionResolver(config);

    // when
    final var region = resolver.resolve("us-east-1a");

    // then
    assertThat(region).contains("us-east-primary");
  }

  @Test
  void shouldRequireAFullMatchNotJustASubstring() {
    // given - the pattern requires the label to be exactly "us-east", not merely contain it
    final var config = new RegionAwarenessConfiguration();
    config.setRegions(List.of(region("us-east", "us-east", 1)));
    final var resolver = new ReplicaRegionResolver(config);

    // when
    final var region = resolver.resolve("us-east-1a");

    // then
    assertThat(region).isEmpty();
  }
}
