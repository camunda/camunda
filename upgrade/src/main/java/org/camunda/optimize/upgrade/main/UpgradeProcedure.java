/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;

import static org.camunda.optimize.upgrade.util.UpgradeUtil.createUpgradeDependencies;

@Slf4j
public abstract class UpgradeProcedure {

  protected final UpgradeExecutionDependencies upgradeDependencies = createUpgradeDependencies();
  protected final ConfigurationService configurationService = upgradeDependencies.getConfigurationService();
  protected final OptimizeElasticsearchClient prefixAwareClient = upgradeDependencies.getPrefixAwareClient();
  protected final ObjectMapper objectMapper = upgradeDependencies.getObjectMapper();
  private UpgradeValidationService upgradeValidationService =
    new UpgradeValidationService(upgradeDependencies.getMetadataService(), prefixAwareClient);

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
    upgradeValidationService.validateSchemaVersions(getInitialVersion(), getTargetVersion());
    upgradeValidationService.validateESVersion(prefixAwareClient, getTargetVersion());
  }

  void setUpgradeValidationService(final UpgradeValidationService upgradeValidationService) {
    this.upgradeValidationService = upgradeValidationService;
  }
}
