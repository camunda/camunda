/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import io.camunda.optimize.service.db.es.schema.index.SettingsIndexES;
import io.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexTemplateIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

public class Upgrade313To86PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
        .fromVersion("3.13")
        .toVersion("8.6.0")
        .addUpgradeStep(new DeleteIndexIfExistsStep("onboarding-state", 2))
        .addUpgradeStep(new DeleteIndexIfExistsStep("event", 4))
        .addUpgradeStep(new DeleteIndexTemplateIfExistsStep("event", 4))
        .addUpgradeStep(new DeleteIndexIfExistsStep("event-process-definition", 5))
        .addUpgradeStep(new DeleteIndexIfExistsStep("event-process-mapping", 4))
        .addUpgradeStep(new DeleteIndexIfExistsStep("event-process-publish-state", 4))
        .addUpgradeStep(new DeleteIndexIfExistsStep("event-sequence-count-external", 4))
        .addUpgradeStep(new DeleteIndexIfExistsStep("event-trace-state-external", 2))
        .addUpgradeStep(deleteLastModifierAndTelemetryInitializedSettingFields())
        .build();
  }

  private static UpdateIndexStep deleteLastModifierAndTelemetryInitializedSettingFields() {
    return new UpdateIndexStep(
        new SettingsIndexES(),
        "ctx._source.remove(\"metadataTelemetryEnabled\"); ctx._source.remove(\"lastModifier\");");
  }
}
