/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.vdurmont.semver4j.Semver;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class UpgradePlanRegistryTest {

  @Test
  void upgradePlansAreSortedAsExpected() {
    // given
    final List<Pair<String, String>> upgradePlanVersions =
        List.of(Pair.of("3.8", "3.8.1"), Pair.of("3.7", "3.8.0"));
    final UpgradePlanRegistry registry =
        new UpgradePlanRegistry(
            upgradePlanVersions.stream()
                .map(fromAndTo -> createUpgradePlan(fromAndTo.getKey(), fromAndTo.getValue()))
                .collect(Collectors.toMap(UpgradePlan::getToVersion, Function.identity())));

    // when/then
    assertThat(registry.getSequentialUpgradePlansToTargetVersion("3.8.1"))
        .extracting(UpgradePlan::getToVersion)
        .map(Semver::getOriginalValue)
        .containsExactly("3.8.0", "3.8.1");
  }

  private UpgradePlan createUpgradePlan(final String fromVersion, final String toVersion) {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(fromVersion)
        .toVersion(toVersion)
        .build();
  }
}
