/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClusterConfigurationCoordinatorSupplierTest {

  @Test
  void shouldSelectNodeZeroAsDefaultCoordinatorInNonZonedCluster() {
    // given
    final var members = Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2"));

    // when
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(members).getDefaultCoordinator();

    // then
    assertThat(coordinator).isEqualTo(MemberId.from("0"));
  }

  @Test
  void shouldSelectCoordinatorNumericallyNotLexicographically() {
    // given — "10" < "2" lexicographically, but node 2 has lower nodeIdx
    final var members = Set.of(MemberId.from("2"), MemberId.from("10"), MemberId.from("20"));

    // when
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(members).getDefaultCoordinator();

    // then
    assertThat(coordinator).isEqualTo(MemberId.from("2"));
  }

  @Test
  void shouldSelectLowestNodeIdxAcrossZonesInZoneAwareCluster() {
    // given — a two-zone cluster; us has nodeIdx=0, eu starts at nodeIdx=1
    final var usZone0 = MemberId.from("us", 0);
    final var euZone1 = MemberId.from("eu", 1);
    final var usZone1 = MemberId.from("us", 1);
    final var members = Set.of(euZone1, usZone0, usZone1);

    // when
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(members).getDefaultCoordinator();

    // then — us_0 wins because nodeIdx=0 is lower than nodeIdx=1 regardless of zone
    assertThat(coordinator).isEqualTo(usZone0);
  }

  @Test
  void shouldUseZoneToBreakTieWhenMultipleZonesHaveSameLowestNodeIdx() {
    // given — two-zone cluster where both zones have a node 0 (typical zone-aware deployment)
    final var euZone0 = MemberId.from("eu", 0);
    final var usZone0 = MemberId.from("us", 0);
    final var euZone1 = MemberId.from("eu", 1);
    final var usZone1 = MemberId.from("us", 1);
    final var members = Set.of(euZone0, usZone0, euZone1, usZone1);

    // when
    final var coordinator =
        ClusterConfigurationCoordinatorSupplier.ofMembers(members).getDefaultCoordinator();

    // then — eu_0 wins: nodeIdx=0 ties with us_0, "eu" < "us" alphabetically
    assertThat(coordinator).isEqualTo(euZone0);
  }

  @Test
  void shouldSelectNextCoordinatorFromZoneAwareClusterExcludingCurrent() {
    // given — two-zone cluster; eu_0 is the default coordinator
    final var euZone0 = MemberId.from("eu", 0);
    final var usZone0 = MemberId.from("us", 0);
    final var euZone1 = MemberId.from("eu", 1);
    final var members = Set.of(euZone0, usZone0, euZone1);
    final var supplier = ClusterConfigurationCoordinatorSupplier.ofMembers(members);

    // when — current coordinator (eu_0) is excluded (e.g. during reconfiguration)
    final var nextCoordinator = supplier.getNextCoordinatorExcluding(Set.of(euZone0));

    // then — us_0 is next: same nodeIdx=0, "us" is next after "eu"
    assertThat(nextCoordinator).isEqualTo(usZone0);
  }

  @Test
  void shouldSelectNextCoordinatorFromNonZonedClusterExcludingCurrent() {
    // given
    final var members = Set.of(MemberId.from("0"), MemberId.from("1"), MemberId.from("2"));
    final var supplier = ClusterConfigurationCoordinatorSupplier.ofMembers(members);

    // when
    final var nextCoordinator = supplier.getNextCoordinatorExcluding(Set.of(MemberId.from("0")));

    // then
    assertThat(nextCoordinator).isEqualTo(MemberId.from("1"));
  }
}
