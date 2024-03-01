/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.main;

import static org.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.service.UpgradeStepLogService;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpgradeProcedureFactory {
  public static UpgradeProcedure create(final UpgradeExecutionDependencies upgradeDependencies) {
    return new UpgradeProcedure(
        upgradeDependencies.esClient(),
        new UpgradeValidationService(),
        createSchemaUpgradeClient(upgradeDependencies),
        new UpgradeStepLogService());
  }
}
