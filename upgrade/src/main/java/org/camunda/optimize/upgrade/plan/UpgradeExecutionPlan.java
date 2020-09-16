/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class UpgradeExecutionPlan implements UpgradePlan {
  private final List<UpgradeStep> upgradeSteps = new ArrayList<>();

  private String toVersion;
  private String fromVersion;
  private SchemaUpgradeClient schemaUpgradeClient;

  /**
   * Package only constructor prevents from building this upgrade execution plan manually.
   * Use {@link org.camunda.optimize.upgrade.plan.UpgradePlanBuilder} instead.
   */
  UpgradeExecutionPlan() {
  }

  public void addUpgradeDependencies(final UpgradeExecutionDependencies upgradeDependencies) {
    schemaUpgradeClient = new SchemaUpgradeClient(upgradeDependencies);
  }

  @Override
  public void execute() {
    int currentStepCount = 1;
    for (UpgradeStep step : upgradeSteps) {
      log.info(
        "Starting step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );

      try {
        step.execute(schemaUpgradeClient);
      } catch (UpgradeRuntimeException e) {
        log.error("The upgrade will be aborted. Please restore your Elasticsearch backup and try again.");
        throw e;
      }

      log.info(
        "Successfully finished step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );
      currentStepCount++;
    }

    schemaUpgradeClient.initializeSchema();
    schemaUpgradeClient.updateOptimizeVersion(fromVersion, toVersion);
  }

  public void addUpgradeStep(UpgradeStep upgradeStep) {
    this.upgradeSteps.add(upgradeStep);
  }

  public void addUpgradeSteps(List<UpgradeStep> upgradeSteps) {
    this.upgradeSteps.addAll(upgradeSteps);
  }

  public void setSchemaUpgradeClient(final SchemaUpgradeClient schemaUpgradeClient) {
    this.schemaUpgradeClient = schemaUpgradeClient;
  }

  public void setFromVersion(String fromVersion) {
    this.fromVersion = fromVersion;
  }

  public void setToVersion(String toVersion) {
    this.toVersion = toVersion;
  }

}
