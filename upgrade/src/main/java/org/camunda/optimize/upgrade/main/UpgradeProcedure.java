/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.camunda.optimize.upgrade.util.UpgradeUtil;

@Slf4j
public abstract class UpgradeProcedure {

  @Getter
  protected final UpgradeExecutionDependencies upgradeDependencies;
  protected final OptimizeElasticsearchClient esClient;
  protected final ElasticsearchMetadataService elasticsearchMetadataService;
  protected final UpgradeValidationService upgradeValidationService;

  public UpgradeProcedure() {
    this(UpgradeUtil.createUpgradeDependencies(), new UpgradeValidationService());
  }

  public UpgradeProcedure(final UpgradeExecutionDependencies upgradeDependencies,
                          final UpgradeValidationService upgradeValidationService) {
    this.upgradeDependencies = upgradeDependencies;
    this.esClient = upgradeDependencies.getEsClient();
    this.elasticsearchMetadataService = upgradeDependencies.getMetadataService();
    this.upgradeValidationService = upgradeValidationService;
  }

  public abstract String getInitialVersion();

  public abstract String getTargetVersion();

  public void performUpgrade() {
    final String schemaVersion = elasticsearchMetadataService.getSchemaVersion(esClient)
      .orElseThrow(() -> new UpgradeRuntimeException("Can't determine current metadata version!"));
    validateVersions(schemaVersion);

    if (!getTargetVersion().equals(schemaVersion)) {
      try {
        UpgradePlan upgradePlan = buildUpgradePlan();
        upgradePlan.execute();
      } catch (Exception e) {
        log.error("Error while executing upgrade from {} to {}", getInitialVersion(), getTargetVersion(), e);
        throw new UpgradeRuntimeException("Upgrade failed.", e);
      }
    } else {
      log.info("Target schemaVersion is already present, no upgrade to perform.");
    }
  }

  protected abstract UpgradePlan buildUpgradePlan();

  private void validateVersions(final String schemaVersion) {
    upgradeValidationService.validateESVersion(esClient, getTargetVersion());
    upgradeValidationService.validateSchemaVersions(schemaVersion, getInitialVersion(), getTargetVersion());
  }

}
