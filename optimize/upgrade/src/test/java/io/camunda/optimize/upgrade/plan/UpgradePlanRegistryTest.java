/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
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
    final var explicitPlan882to883 = createUpgradePlan("8.8.2", "8.8.3");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(explicitPlan882to883.getToVersion(), explicitPlan882to883);

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
            plan882to883 -> assertThat(plan882to883).isSameAs(explicitPlan882to883),
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
  void crossMinorPlanIsNotOverwrittenWhenExplicitPlanExists() {
    // given: a cross-minor plan 8.7 -> 8.8.0 already in the map
    final var targetVersion = "8.8.3";
    final var crossMinorPlan87to880 = createUpgradePlan("8.7", "8.8.0");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(crossMinorPlan87to880.getToVersion(), crossMinorPlan87to880);

    final var registry = new UpgradePlanRegistry(existingPlans);

    // when
    registry.generateMissingCrossMinorUpgradePlan(targetVersion);
    registry.generateMissingPatchUpgradePlans(targetVersion);

    // then: explicit cross-minor plan is preserved, plus 3 auto-generated patch plans
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as(
            "Should preserve the existing cross-minor plan from 8.7 to 8.8.0, and generate patch plans from 8.8.0 to 8.8.3")
        .satisfiesExactly(
            plan87to880 -> assertThat(plan87to880).isSameAs(crossMinorPlan87to880),
            plan880to881 -> assertNoOpPlan(plan880to881, "8.8.0", "8.8.1"),
            plan881to882 -> assertNoOpPlan(plan881to882, "8.8.1", "8.8.2"),
            plan882to883 -> assertNoOpPlan(plan882to883, "8.8.2", "8.8.3"));
  }

  @Test
  void crossMinorPlanIsAutoGeneratedWhenMissing() {
    // given: no explicit plan targeting 8.10.0
    final var registry = new UpgradePlanRegistry(new HashMap<>());
    final var targetVersion = "8.10.3";

    // when
    registry.generateMissingCrossMinorUpgradePlan(targetVersion);

    // then: a no-op plan from 8.9 -> 8.10.0 is generated
    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as("Should auto-generate a no-op cross-minor plan from 8.9 to 8.10.0")
        .first()
        .satisfies(plan89to8100 -> assertNoOpPlan(plan89to8100, "8.9", "8.10.0"));
  }

  @Test
  void crossMinorPlanOnMajorVersionBoundaryThrowsWhenNoExplicitPlanExists() {
    // given: minor is 0 — major version boundary, no explicit plan for 9.0.0
    final var registry = new UpgradePlanRegistry(new HashMap<>());

    // when / then: previous minor cannot be computed — an explicit factory is required
    assertThatThrownBy(() -> registry.generateMissingCrossMinorUpgradePlan("9.0.2"))
        .isInstanceOf(UpgradeRuntimeException.class)
        .hasMessage(
            "Cannot compute previous minor version from 9.0.2. An explicit UpgradePlanFactory targeting 9.0.0 is required.");
  }

  @Test
  void crossMinorPlanOnMajorVersionBoundaryIsSkippedWhenExplicitPlanExists() {
    // given: an explicit plan already covers 9.0.0 (the major boundary)
    final String targetVersion = "9.0.0";
    final var explicitPlan89to900 = createUpgradePlan("8.9", targetVersion);
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(explicitPlan89to900.getToVersion(), explicitPlan89to900);
    final var registry = new UpgradePlanRegistry(existingPlans);

    // when - then: no exception — the existing plan is preserved
    assertThatCode(() -> registry.generateMissingCrossMinorUpgradePlan(targetVersion))
        .as("Should not throw when an explicit plan already covers the major version boundary")
        .doesNotThrowAnyException();

    final var plans = registry.getSequentialUpgradePlansToTargetVersion(targetVersion);
    assertThat(plans)
        .as("Explicit major-boundary plan must be preserved without throwing")
        .satisfiesExactly(plan89to900 -> assertThat(plan89to900).isSameAs(explicitPlan89to900));
  }

  @Test
  void versionJumpSkipsUnreleasedPatches() {
    // given: jump from 8.8.2->8.8.10
    final var targetVersion = "8.8.12";
    final var jumpPlan882to8810 = createUpgradePlan("8.8.2", "8.8.10");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(jumpPlan882to8810.getToVersion(), jumpPlan882to8810);
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
            plan882to8810 -> assertThat(plan882to8810).isSameAs(jumpPlan882to8810),
            plan8810to8811 -> assertNoOpPlan(plan8810to8811, "8.8.10", "8.8.11"),
            plan8811to8812 -> assertNoOpPlan(plan8811to8812, "8.8.11", "8.8.12"));
  }

  @Test
  void jumpToCurrentVersionGeneratesNoPlansBeyondJump() {
    // given: jump plan from 8.8.3->8.8.7
    final var targetVersion = "8.8.7";
    final var jumpPlan883to887 = createUpgradePlan("8.8.3", targetVersion);
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(jumpPlan883to887.getToVersion(), jumpPlan883to887);

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
            plan883to887 -> assertThat(plan883to887).isSameAs(jumpPlan883to887));
  }

  @Test
  void jumpFromPatchZeroGeneratesNoPlanBeforeJump() {
    // given: jump plan from 8.8.0->8.8.5
    final var targetVersion = "8.8.7";
    final var jumpPlan880to885 = createUpgradePlan("8.8.0", "8.8.5");
    final var existingPlans = new HashMap<Semver, UpgradePlan>();
    existingPlans.put(jumpPlan880to885.getToVersion(), jumpPlan880to885);

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
              assertThat(plan880to885).isSameAs(jumpPlan880to885);
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
