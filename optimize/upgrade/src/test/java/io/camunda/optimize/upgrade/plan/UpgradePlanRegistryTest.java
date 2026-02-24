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
import java.util.HashMap;
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

  @Test
  void noExplicitPatchPlansGeneratesFullChain() {
    // given: no explicit patch plans
    final var targetVersion = "8.8.3";
    final var registry = new UpgradePlanRegistry(new HashMap<>());

    // when
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as("Should generate a full chain of patch upgrade plans from 8.8.0 to 8.8.3")
        .satisfiesExactly(
            plan880to881 -> assertNoOpPlan(plan880to881, "8.8.0", "8.8.1"),
            plan881to882 -> assertNoOpPlan(plan881to882, "8.8.1", "8.8.2"),
            plan882to883 -> assertNoOpPlan(plan882to883, "8.8.2", "8.8.3"));
  }

  @Test
  void explicitPlanInMiddleIsPreserved() {
    // given: explicit factory for 8.8.2 -> 8.8.3
    final var targetVersion = "8.8.5";
    final var explicitPlan = createUpgradePlan("8.8.2", "8.8.3");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(explicitPlan.getToVersion(), explicitPlan);

    final var registry = new UpgradePlanRegistry(existingPlans);

    // when
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as("Should generate patch plans for missing versions, but preserve the explicit plan")
        .satisfiesExactly(
            plan880to881 -> assertNoOpPlan(plan880to881, "8.8.0", "8.8.1"),
            plan881to882 -> assertNoOpPlan(plan881to882, "8.8.1", "8.8.2"),
            explicitPlan882to883 -> {
              assertNoOpPlan(explicitPlan882to883, "8.8.2", "8.8.3");
              assertThat(explicitPlan882to883).isSameAs(explicitPlan);
            },
            plan883to884 -> assertNoOpPlan(plan883to884, "8.8.3", "8.8.4"),
            plan884to885 -> assertNoOpPlan(plan884to885, "8.8.4", "8.8.5"));
  }

  @Test
  void patchZeroGeneratesNoPatchPlans() {
    // given
    final var targetVersion = "8.9.0";
    final var registry = new UpgradePlanRegistry(new HashMap<>());

    // when
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans).as("No patch plans should be generated for patch version 0").isEmpty();
  }

  @Test
  void crossMinorPlanInMapIsNotAffected() {
    // given: a cross-minor plan 8.7 -> 8.8.0 already in the map
    final var targetVersion = "8.8.3";
    final var crossMinorPlan = createUpgradePlan("8.7", "8.8.0");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(crossMinorPlan.getToVersion(), crossMinorPlan);

    final var registry = new UpgradePlanRegistry(existingPlans);

    // when: auto-generating for version 8.8.3
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then: cross-minor plan is preserved, plus 3 auto-generated patch plans
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as(
            "Should preserve the existing cross-minor plan from 8.7 to 8.8.0, and generate patch plans from 8.8.0 to 8.8.3")
        .satisfiesExactly(
            plan87to880 -> {
              assertNoOpPlan(plan87to880, "8.7", "8.8.0");
              assertThat(plan87to880).isSameAs(crossMinorPlan);
            },
            plan880to881 -> assertNoOpPlan(plan880to881, "8.8.0", "8.8.1"),
            plan881to882 -> assertNoOpPlan(plan881to882, "8.8.1", "8.8.2"),
            plan882to883 -> assertNoOpPlan(plan882to883, "8.8.2", "8.8.3"));
  }

  @Test
  void versionJumpSkipsUnreleasedPatches() {
    // given: jump from 8.8.2->8.8.10
    final var targetVersion = "8.8.12";
    final var jumpPlan = createUpgradePlan("8.8.2", "8.8.10");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(jumpPlan.getToVersion(), jumpPlan);
    final var registry = new UpgradePlanRegistry(existingPlans);

    // when
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then: no plans for 8.8.3 through 8.8.9
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as("Should generate chain with jump: auto 0->1->2, jump 2->10, auto 10->11->12")
        .satisfiesExactly(
            plan880to881 -> assertNoOpPlan(plan880to881, "8.8.0", "8.8.1"),
            plan881to882 -> assertNoOpPlan(plan881to882, "8.8.1", "8.8.2"),
            plan882to8810 -> {
              assertThat(plan882to8810.getFromVersion().getOriginalValue()).isEqualTo("8.8.2");
              assertThat(plan882to8810.getToVersion().getOriginalValue()).isEqualTo("8.8.10");
              assertThat(plan882to8810).isSameAs(jumpPlan);
            },
            plan8810to8811 -> assertNoOpPlan(plan8810to8811, "8.8.10", "8.8.11"),
            plan8811to8812 -> assertNoOpPlan(plan8811to8812, "8.8.11", "8.8.12"));
  }

  @Test
  void jumpToCurrentVersionGeneratesNoPlansBeyondJump() {
    // given: jump plan from 8.8.3->8.8.7
    final var targetVersion = "8.8.7";
    final var jumpPlan = createUpgradePlan("8.8.3", targetVersion);
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(jumpPlan.getToVersion(), jumpPlan);

    final var registry = new UpgradePlanRegistry(existingPlans);

    // when
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then: auto 0->1->2->3, jump 3->7, no 8.8.4/5/6
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as("Should generate chain ending with jump: auto 0->1->2->3, then jump 3->7")
        .satisfiesExactly(
            plan880to881 -> assertNoOpPlan(plan880to881, "8.8.0", "8.8.1"),
            plan881to882 -> assertNoOpPlan(plan881to882, "8.8.1", "8.8.2"),
            plan882to883 -> assertNoOpPlan(plan882to883, "8.8.2", "8.8.3"),
            plan883to887 -> {
              assertThat(plan883to887.getFromVersion().getOriginalValue()).isEqualTo("8.8.3");
              assertThat(plan883to887.getToVersion().getOriginalValue()).isEqualTo("8.8.7");
              assertThat(plan883to887).isSameAs(jumpPlan);
            });
  }

  @Test
  void jumpFromPatchZeroGeneratesNoPlanBeforeJump() {
    // given: jump plan from 8.8.0->8.8.5
    final var targetVersion = "8.8.7";
    final var jumpPlan = createUpgradePlan("8.8.0", "8.8.5");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(jumpPlan.getToVersion(), jumpPlan);

    final var registry = new UpgradePlanRegistry(existingPlans);

    // when
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then: jump 0->5, auto 5->6->7, no 8.8.1/2/3/4
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as("Should generate chain starting with jump: jump 0->5, then auto 5->6->7")
        .satisfiesExactly(
            plan880to885 -> {
              assertThat(plan880to885.getFromVersion().getOriginalValue()).isEqualTo("8.8.0");
              assertThat(plan880to885.getToVersion().getOriginalValue()).isEqualTo("8.8.5");
              assertThat(plan880to885).isSameAs(jumpPlan);
            },
            plan885to886 -> assertNoOpPlan(plan885to886, "8.8.5", "8.8.6"),
            plan886to887 -> assertNoOpPlan(plan886to887, "8.8.6", "8.8.7"));
  }

  private UpgradePlan createUpgradePlan(final String fromVersion, final String toVersion) {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(fromVersion)
        .toVersion(toVersion)
        .build();
  }

  private void assertNoOpPlan(
      final UpgradePlan plan, final String fromVersion, final String toVersion) {
    assertThat(plan.getFromVersion().getOriginalValue()).isEqualTo(fromVersion);
    assertThat(plan.getToVersion().getOriginalValue()).isEqualTo(toVersion);
    assertThat(plan.getUpgradeSteps()).isEmpty();
  }
}
