/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import io.camunda.optimize.service.db.es.schema.index.AgentDefinitionIndexES;
import io.camunda.optimize.service.db.es.schema.index.AgentRunIndexES;
import io.camunda.optimize.service.db.es.schema.index.AuthorizationIndexES;
import io.camunda.optimize.service.db.es.schema.index.MappingRuleIndexES;
import io.camunda.optimize.service.db.es.schema.index.RoleIndexES;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.CreateIndexStep;

public class Upgrade810to811PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion("8.10")
        .toVersion("8.11.0")
        .addUpgradeStep(new CreateIndexStep(new AgentRunIndexES()))
        .addUpgradeStep(new CreateIndexStep(new AgentDefinitionIndexES()))
        .addUpgradeStep(new CreateIndexStep(new RoleIndexES()))
        .addUpgradeStep(new CreateIndexStep(new MappingRuleIndexES()))
        .addUpgradeStep(new CreateIndexStep(new AuthorizationIndexES()))
        .build();
  }
}
