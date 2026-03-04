/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan;

import com.google.common.annotations.VisibleForTesting;
import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.factories.UpgradePlanFactory;
import io.camunda.optimize.upgrade.util.VersionUtil;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class UpgradePlanRegistry {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UpgradePlanRegistry.class);
  private final Map<Semver, UpgradePlan> upgradePlans;

  public UpgradePlanRegistry(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    upgradePlans = new HashMap<>();
    try (final ScanResult scanResult =
        new ClassGraph()
            .enableClassInfo()
            .acceptPackages(UpgradePlanFactory.class.getPackage().getName())
            .scan()) {
      scanResult
          .getClassesImplementing(UpgradePlanFactory.class.getName())
          .forEach(
              upgradePlanFactoryClass -> {
                try {
                  final UpgradePlanFactory planFactory =
                      (UpgradePlanFactory)
                          upgradePlanFactoryClass.loadClass().getConstructor().newInstance();
                  final UpgradePlan upgradePlan =
                      planFactory.createUpgradePlan(upgradeExecutionDependencies);
                  upgradePlans.put(upgradePlan.getToVersion(), upgradePlan);
                } catch (final InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException
                    | NoSuchMethodException e) {
                  LOG.error(
                      "Could not instantiate {}, will skip this factory.",
                      upgradePlanFactoryClass.getName());
                  throw new UpgradeRuntimeException(
                      "Failed to instantiate upgrade plan: " + upgradePlanFactoryClass.getName());
                }
              });
    }

    // Generate the cross-minor no-op plan (e.g. 8.9 -> 8.10.0) if no explicit factory already
    // targets X.Y.0, then fill any missing patch-to-patch gaps within the current minor.
    // Plans beyond the actual target version are harmlessly filtered by
    // `getSequentialUpgradePlansToTargetVersion`.
    generateMissingCrossMinorUpgradePlan(Version.VERSION);
    generateMissingPatchUpgradePlans(Version.VERSION);
  }

  @VisibleForTesting
  UpgradePlanRegistry(final Map<Semver, UpgradePlan> upgradePlans) {
    this.upgradePlans = upgradePlans;
  }

  /**
   * Auto-generates a no-op cross-minor upgrade plan from the previous minor version to {@code
   * X.Y.0} if no explicit {@link UpgradePlanFactory} already targets that version.
   *
   * <p>For example, if {@code currentVersion} is {@code "8.10.3"} this method checks whether a plan
   * targeting {@code 8.10.0} is already registered. If not, it inserts a no-op plan from {@code
   * "8.9"} to {@code "8.10.0"}.
   *
   * <p>Throws {@link UpgradeRuntimeException} when the previous minor version cannot be computed
   * (e.g. minor is {@code 0}, a major version boundary) and no explicit plan already covers {@code
   * X.Y.0} — in that case an explicit {@link UpgradePlanFactory} implementation is required.
   *
   * @param currentVersion the current application version string (e.g. {@code "8.10.3"})
   * @throws UpgradeRuntimeException if the previous minor version cannot be computed and no
   *     explicit plan targets {@code X.Y.0}
   */
  @VisibleForTesting
  void generateMissingCrossMinorUpgradePlan(final String currentVersion) {
    final var toVersion = new Semver(Version.getMajorAndMinor(currentVersion) + ".0");

    if (upgradePlans.containsKey(toVersion)) {
      LOG.debug(
          "Explicit cross-minor plan already targets {}; skipping auto-generation.",
          toVersion.getValue());
      return;
    }

    final var fromVersion =
        VersionUtil.previousMinorVersion(currentVersion)
            .orElseThrow(
                () ->
                    new UpgradeRuntimeException(
                        String.format(
                            "Cannot compute previous minor version from %s. "
                                + "An explicit UpgradePlanFactory targeting %s is required.",
                            currentVersion, toVersion.getValue())));

    upgradePlans.put(
        toVersion,
        UpgradePlanBuilder.createUpgradePlan()
            .fromVersion(fromVersion)
            .toVersion(toVersion.getValue())
            .build());
    LOG.debug(
        "Auto-generated no-op cross-minor upgrade plan from {} to {}.",
        fromVersion,
        toVersion.getValue());
  }

  /**
   * Auto-generates no-op upgrade plans for missing patch-to-patch transitions within the current
   * minor version, while respecting explicit version jumps.
   *
   * <p>The algorithm walks backwards from {@code currentPatch} down to {@code 1}. At each step it
   * looks up whether an explicit plan already targets {@code major.minor.patch}. If one is found,
   * the loop jumps directly to {@code fromVersion.patch}, skipping the entire covered range without
   * generating any intermediate plans. If no plan is found, a no-op plan is inserted for that
   * single step and the counter decrements by one.
   *
   * <p>For example, given an explicit jump plan {@code 8.8.2 -> 8.8.10} with current version {@code
   * 8.8.12}, the backward walk proceeds as follows:
   *
   * <ul>
   *   <li>patch 12 - no plan -> auto-generate {@code 8.8.11->8.8.12}, decrement to 11
   *   <li>patch 11 - no plan -> auto-generate {@code 8.8.10->8.8.11}, decrement to 10
   *   <li>patch 10 - explicit plan {@code 8.8.2->8.8.10} found → jump to patch 2
   *   <li>patch 2 - no plan -> auto-generate {@code 8.8.1->8.8.2}, decrement to 1
   *   <li>patch 1 - no plan -> auto-generate {@code 8.8.0->8.8.1}, decrement to 0
   *   <li>patch 0 - loop ends
   * </ul>
   *
   * <p>Patches {@code 8.8.3} through {@code 8.8.9} are never visited and no plans are generated for
   * them.
   *
   * <p>Plans already registered by explicit {@link UpgradePlanFactory} implementations are never
   * overwritten - only missing gaps are filled with no-op plans.
   *
   * <p>This eliminates the need to manually create boilerplate per-patch factory classes that
   * contain no real migration logic.
   *
   * @param currentVersion the current application version string (e.g. {@code "8.9.3"})
   */
  @VisibleForTesting
  void generateMissingPatchUpgradePlans(final String currentVersion) {
    final var version = new Semver(currentVersion);
    final int major = version.getMajor();
    final int minor = version.getMinor();
    final int currentPatch = version.getPatch();

    if (currentPatch == 0) {
      return;
    }

    int patch = currentPatch;
    while (patch > 0) {
      final var toVersion = new Semver(major + "." + minor + "." + patch);
      final var plan = upgradePlans.get(toVersion);
      if (plan != null) {
        // Explicit plan found - jump to its fromVersion patch, skipping the covered range
        LOG.debug(
            "Explicit plan spans {}.{}.{} -> {}.{}.{}, skipping auto-generation for this range.",
            major,
            minor,
            plan.getFromVersion().getPatch(),
            major,
            minor,
            patch);
        patch = plan.getFromVersion().getPatch();
      } else {
        // No plan for this patch - generate a no-op step and move one patch down
        final var from = major + "." + minor + "." + (patch - 1);
        final var to = major + "." + minor + "." + patch;
        final var noOpPlan =
            UpgradePlanBuilder.createUpgradePlan().fromVersion(from).toVersion(to).build();
        upgradePlans.put(toVersion, noOpPlan);
        LOG.debug(
            "Auto-generated no-op patch upgrade plan from {} to {} (no explicit factory found).",
            from,
            to);
        patch--;
      }
    }
  }

  public List<UpgradePlan> getSequentialUpgradePlansToTargetVersion(final String targetVersion) {
    return upgradePlans.entrySet().stream()
        .filter(entry -> entry.getKey().isLowerThanOrEqualTo(new Semver(targetVersion)))
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .collect(Collectors.toList());
  }
}
