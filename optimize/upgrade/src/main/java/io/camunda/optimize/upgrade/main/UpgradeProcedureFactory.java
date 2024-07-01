/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.main;

import static io.camunda.optimize.upgrade.es.SchemaUpgradeClientFactory.createSchemaUpgradeClient;

import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.service.UpgradeStepLogService;
import io.camunda.optimize.upgrade.service.UpgradeValidationService;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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
