/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_PROCESS_VARIABLE_INDEX_NAME;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.index.ExternalProcessVariableIndex;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexTemplateIfExistsStep;

public class Upgrade87to88PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion("8.7")
        .toVersion("8.8.0")
        .addUpgradeStep(
            new DeleteIndexIfExistsStep(DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME, 2))
        .addUpgradeStep(
            new DeleteIndexTemplateIfExistsStep(
                DatabaseConstants.VARIABLE_UPDATE_INSTANCE_INDEX_NAME, 2))
        .addUpgradeStep(
            new DeleteIndexTemplateIfExistsStep(
                EXTERNAL_PROCESS_VARIABLE_INDEX_NAME, ExternalProcessVariableIndex.VERSION))
        .build();
  }
}
