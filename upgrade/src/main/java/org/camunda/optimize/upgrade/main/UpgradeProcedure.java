/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.camunda.optimize.upgrade.util.UpgradeUtil;

@Slf4j
public abstract class UpgradeProcedure {

  @Getter
  protected final UpgradeExecutionDependencies upgradeDependencies;
  protected final OptimizeElasticsearchClient esClient;
  private UpgradeValidationService upgradeValidationService;

  public UpgradeProcedure() {
    this(UpgradeUtil.createUpgradeDependencies());
  }

  public UpgradeProcedure(UpgradeExecutionDependencies upgradeDependencies) {
    this.upgradeDependencies = upgradeDependencies;
    this.esClient = upgradeDependencies.getEsClient();
    this.upgradeValidationService = new UpgradeValidationService(upgradeDependencies.getMetadataService(), esClient);
  }

  public abstract String getInitialVersion();

  public abstract String getTargetVersion();

  public void performUpgrade() {
    validateVersions();

    try {
      UpgradePlan upgradePlan = buildUpgradePlan();
      upgradePlan.execute();
    } catch (Exception e) {
      log.error("Error while executing upgrade from {} to {}", getInitialVersion(), getTargetVersion(), e);
      System.exit(2);
    }
  }

  protected abstract UpgradePlan buildUpgradePlan();

  private void validateVersions() {
    upgradeValidationService.validateESVersion(esClient, getTargetVersion());
    upgradeValidationService.validateSchemaVersions(getInitialVersion(), getTargetVersion());
  }

  void setUpgradeValidationService(final UpgradeValidationService upgradeValidationService) {
    this.upgradeValidationService = upgradeValidationService;
  }
}
