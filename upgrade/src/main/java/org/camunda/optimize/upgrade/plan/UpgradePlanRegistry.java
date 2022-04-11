/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan;

import com.vdurmont.semver4j.Semver;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.factories.CurrentVersionNoOperationUpgradePlanFactory;
import org.camunda.optimize.upgrade.plan.factories.UpgradePlanFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class UpgradePlanRegistry {

  private final Map<Semver, UpgradePlan> upgradePlans = new HashMap<>();

  public UpgradePlanRegistry(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages(UpgradePlanFactory.class.getPackage().getName())
      .scan()) {
      scanResult.getClassesImplementing(UpgradePlanFactory.class.getName())
        .forEach(upgradePlanFactoryClass -> {
          try {
            final UpgradePlanFactory planFactory = (UpgradePlanFactory) upgradePlanFactoryClass.loadClass()
              .getConstructor().newInstance();
            final UpgradePlan upgradePlan = planFactory.createUpgradePlan(upgradeExecutionDependencies);
            if (planFactory instanceof CurrentVersionNoOperationUpgradePlanFactory) {
              // The no operation  upgrade plan will only get added if there is not a custom plan yet
              upgradePlans.putIfAbsent(upgradePlan.getToVersion(), upgradePlan);
            } else {
              // specific upgrade plans always overwrite any preexisting entries
              // (e.g. if no operation default upgrade plan was added first)
              upgradePlans.put(upgradePlan.getToVersion(), upgradePlan);
            }
          } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            log.error("Could not instantiate {}, will skip this factory.", upgradePlanFactoryClass.getName());
            throw new UpgradeRuntimeException("Failed to instantiate upgrade plan: " + upgradePlanFactoryClass.getName());
          }
        });
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
