/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan;

import com.vdurmont.semver4j.Semver;
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

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(UpgradePlanRegistry.class);
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
                  log.error(
                      "Could not instantiate {}, will skip this factory.",
                      upgradePlanFactoryClass.getName());
                  throw new UpgradeRuntimeException(
                      "Failed to instantiate upgrade plan: " + upgradePlanFactoryClass.getName());
                }
              });
    }
  }

  public UpgradePlanRegistry(final Map<Semver, UpgradePlan> upgradePlans) {
    this.upgradePlans = upgradePlans;
  }

  public List<UpgradePlan> getSequentialUpgradePlansToTargetVersion(final String targetVersion) {
    return upgradePlans.entrySet().stream()
        .filter(entry -> entry.getKey().isLowerThanOrEqualTo(new Semver(targetVersion)))
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .collect(Collectors.toList());
  }
}
