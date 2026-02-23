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
import io.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import io.camunda.optimize.upgrade.plan.factories.UpgradePlanFactory;
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
                  if (planFactory instanceof CurrentVersionNoOperationUpgradePlanFactory) {
                    // The no operation upgrade plan will only get added if there is not a custom
                    // plan yet
                    upgradePlans.putIfAbsent(upgradePlan.getToVersion(), upgradePlan);
                  } else {
                    // specific upgrade plans always overwrite any preexisting entries
                    // (e.g. if no operation default upgrade plan was added first)
                    upgradePlans.put(upgradePlan.getToVersion(), upgradePlan);
                  }
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

    // Generate patch plans up to the compiled-in version. Plans beyond the actual target
    // version (if different) are harmlessly filtered by `getSequentialUpgradePlansToTargetVersion`.
    generateMissingPatchUpgradePlans(Version.VERSION);
  }

  @VisibleForTesting
  UpgradePlanRegistry(final Map<Semver, UpgradePlan> upgradePlans) {
    this.upgradePlans = upgradePlans;
  }

  /**
   * Auto-generates no-op upgrade plans for missing patch-to-patch transitions within the current
   * minor version. For example, if the current version is {@code 8.9.3}, this method ensures that
   * plans for {@code 8.9.0 -> 8.9.1}, {@code 8.9.1 -> 8.9.2}, and {@code 8.9.2 -> 8.9.3} exist.
   * Plans already registered by explicit {@link UpgradePlanFactory} implementations are never
   * overwritten — only missing gaps are filled with no-op plans.
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

    for (int patch = 1; patch <= currentPatch; patch++) {
      final var toVersion = new Semver(major + "." + minor + "." + patch);
      if (!upgradePlans.containsKey(toVersion)) {
        final var from = major + "." + minor + "." + (patch - 1);
        final var to = major + "." + minor + "." + patch;
        final var noOpPlan =
            UpgradePlanBuilder.createUpgradePlan().fromVersion(from).toVersion(to).build();
        upgradePlans.put(toVersion, noOpPlan);
        LOG.debug(
            "Auto-generated no-op patch upgrade plan from {} to {} (no explicit factory found).",
            from,
            to);
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
