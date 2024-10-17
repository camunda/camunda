/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import java.io.IOException;

public interface UpgradePlanFactory {
  UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies);

  void logErrorMessage(final String message);

  default boolean isC7LicenseDataPresent(
      final DatabaseClient databaseClient, final OptimizeIndexNameService indexNameService) {
    try {
      return databaseClient.countWithoutPrefixWithExistsCheck(
              indexNameService.getOptimizeIndexAliasForIndex("license"))
          > 0;
    } catch (IOException e) {
      final String reason = "Was not able to determine existence of C7 data.";
      logErrorMessage(reason);
      throw new UpgradeRuntimeException(reason, e);
    }
  }

  default void validateOptimizeModeAndFailIfC7DataPresent(
      final DatabaseClient databaseClient, final OptimizeIndexNameService indexNameService) {
    if (isC7LicenseDataPresent(databaseClient, indexNameService)) {
      final String msg =
          "Detected Camunda 7 Optimize data in database. The upgrade to Optimize 8.6 is only applicable to "
              + "Optimize instances running with Camunda 8. For Camunda 7 Optimize, please apply the upgrade to "
              + "Optimize 3.14 instead. "
              + "Please refer to the Camunda Optimize documentation for more details on the upgrade to 8.6: "
              + "https://docs.camunda.io/optimize/self-managed/optimize-deployment/migration-update/camunda-8/3.13_8.5-to-8.6.md/";
      logErrorMessage(msg);
      throw new UpgradeRuntimeException(msg);
    }
  }
}
