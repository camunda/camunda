/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.main;

import com.vdurmont.semver4j.Semver;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.upgrade.es.SchemaUpgradeClient;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import org.camunda.optimize.upgrade.service.UpgradeStepLogService;
import org.camunda.optimize.upgrade.service.UpgradeValidationService;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.ReindexStep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.upgrade.steps.UpgradeStepType.REINDEX;

@Slf4j
public class UpgradeProcedure {

  protected final OptimizeElasticsearchClient esClient;
  protected final UpgradeValidationService upgradeValidationService;
  protected final SchemaUpgradeClient schemaUpgradeClient;
  protected final UpgradeStepLogService upgradeStepLogService;

  public UpgradeProcedure(final OptimizeElasticsearchClient esClient,
                          final UpgradeValidationService upgradeValidationService,
                          final SchemaUpgradeClient schemaUpgradeClient,
                          final UpgradeStepLogService upgradeStepLogService) {
    this.esClient = esClient;
    this.upgradeValidationService = upgradeValidationService;
    this.schemaUpgradeClient = schemaUpgradeClient;
    this.upgradeStepLogService = upgradeStepLogService;
  }

  public void performUpgrade(final UpgradePlan upgradePlan) {
    final Semver targetVersion = upgradePlan.getToVersion();
    final Optional<String> optionalSchemaVersion = schemaUpgradeClient.getSchemaVersion();

    if (optionalSchemaVersion.isPresent()) {
      final Semver schemaVersion = new Semver(optionalSchemaVersion.get());
      if (schemaVersion.isLowerThan(targetVersion)) {
        validateVersions(schemaVersion, upgradePlan);
        try {
          upgradeStepLogService.initializeOrUpdate(schemaUpgradeClient);
          executeUpgradePlan(upgradePlan);
        } catch (Exception e) {
          log.error(
            "Error while executing update from {} to {}", upgradePlan.getFromVersion(), targetVersion, e
          );
          throw new UpgradeRuntimeException("Upgrade failed.", e);
        }
      } else {
        if (!upgradePlan.isSilentUpgrade()) {
          log.info(
            "Target schemaVersion or a newer version is already present, no update to perform to {}.", targetVersion
          );
        }
      }
    } else {
      if (!upgradePlan.isSilentUpgrade()) {
        log.info("No Connection to elasticsearch or no Optimize Metadata index found, skipping update to {}.", targetVersion);
      }
    }
  }

  private void executeUpgradePlan(final UpgradePlan upgradePlan) {
    int currentStepCount = 1;
    final List<UpgradeStep> upgradeSteps = upgradePlan.getUpgradeSteps();
    for (UpgradeStep step : upgradeSteps) {
      final UpgradeStepLogEntryDto logEntryDto = UpgradeStepLogEntryDto.builder()
        .indexName(getIndexNameForStep(step))
        .optimizeVersion(upgradePlan.getToVersion().toString())
        .stepType(step.getType())
        .stepNumber(currentStepCount)
        .build();
      final Optional<Instant> stepAppliedDate = upgradeStepLogService.getStepAppliedDate(
        schemaUpgradeClient, logEntryDto
      );
      if (stepAppliedDate.isEmpty()) {
        log.info(
          "Starting step {}/{}: {} on index: {}",
          currentStepCount, upgradeSteps.size(), step.getClass().getSimpleName(), getIndexNameForStep(step)
        );
        try {
          step.execute(schemaUpgradeClient);
          upgradeStepLogService.recordAppliedStep(schemaUpgradeClient, logEntryDto);
        } catch (UpgradeRuntimeException e) {
          log.error("The upgrade will be aborted. Please investigate the cause and retry it..");
          throw e;
        }
        log.info(
          "Successfully finished step {}/{}: {} on index: {}",
          currentStepCount, upgradeSteps.size(), step.getClass().getSimpleName(), getIndexNameForStep(step)
        );
      } else {
        log.info(
          "Skipping Step {}/{}: {} on index: {} as it was found to be previously completed already at: {}.",
          currentStepCount,
          upgradeSteps.size(),
          step.getClass().getSimpleName(),
          getIndexNameForStep(step),
          stepAppliedDate.get()
        );
      }
      currentStepCount++;
    }
    schemaUpgradeClient.updateOptimizeVersion(upgradePlan);
  }

  private void validateVersions(final Semver schemaVersion, final UpgradePlan upgradePlan) {
    upgradeValidationService.validateESVersion(esClient, upgradePlan.getToVersion().toString());
    upgradeValidationService.validateSchemaVersions(
      schemaVersion.getValue(), upgradePlan.getFromVersion().getValue(), upgradePlan.getToVersion().getValue()
    );
  }

  private String getIndexNameForStep(final UpgradeStep step) {
    if (REINDEX.equals(step.getType())) {
      final ReindexStep reindexStep = (ReindexStep) step;
      return String.format(
        "%s-TO-%s",
        esClient.getIndexNameService().getOptimizeIndexNameWithVersion(reindexStep.getSourceIndex()),
        esClient.getIndexNameService().getOptimizeIndexNameWithVersion(reindexStep.getTargetIndex())
      );
    } else {
      return esClient.getIndexNameService().getOptimizeIndexNameWithVersion(step.getIndex());
    }
  }

}
