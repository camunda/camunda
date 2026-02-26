/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.util.VersionUtil;

/**
 * Creates a no-op upgrade plan from the previous minor version to the current version. The previous
 * minor version is computed dynamically from {@link Version#VERSION} via {@link
 * VersionUtil#previousMinorVersion(String)} by decrementing the minor component. For example, if
 * the current version is {@code 8.10.0}, the {@code fromVersion} will be {@code "8.9"}.
 *
 * <p>This factory does not support major version boundaries (i.e., when the minor version is {@code
 * 0}). An explicit {@link UpgradePlanFactory} implementation must be provided for such cases.
 */
public class CurrentVersionNoOperationUpgradePlanFactory implements UpgradePlanFactory {

  public UpgradePlan createUpgradePlan() {
    final var fromVersion =
        VersionUtil.previousMinorVersion(Version.VERSION)
            .orElseThrow(
                () ->
                    new UpgradeRuntimeException(
                        String.format(
                            "Cannot compute previous minor version from %s. "
                                + "An explicit UpgradePlanFactory is required.",
                            Version.VERSION)));
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(fromVersion)
        .toVersion(Version.getMajorAndMinor(Version.VERSION) + ".0")
        .build();
  }

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return createUpgradePlan();
  }
}
