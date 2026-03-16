/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan;

import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.factories.UpgradePlanFactory;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Supplier of {@link UpgradePlan}s that explicitly instantiates all implementations of {@link
 * UpgradePlanFactory} found on the classpath and invokes them to create the corresponding {@link
 * UpgradePlan}s.
 */
public class ExplicitUpgradePlansSupplier implements Supplier<List<UpgradePlan>> {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ExplicitUpgradePlansSupplier.class);

  private final UpgradeExecutionDependencies upgradeExecutionDependencies;

  public ExplicitUpgradePlansSupplier(
      final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    this.upgradeExecutionDependencies = upgradeExecutionDependencies;
  }

  @Override
  public List<UpgradePlan> get() {
    final List<UpgradePlan> upgradePlans = new ArrayList<>();
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
                  upgradePlans.add(upgradePlan);
                } catch (final InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException
                    | NoSuchMethodException e) {
                  LOG.error(
                      "Could not instantiate {}, aborting upgrade.",
                      upgradePlanFactoryClass.getName());
                  throw new UpgradeRuntimeException(
                      "Failed to instantiate upgrade plan: " + upgradePlanFactoryClass.getName());
                }
              });
      return upgradePlans;
    }
  }
}
