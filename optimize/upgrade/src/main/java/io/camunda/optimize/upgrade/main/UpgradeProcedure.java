/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.main;

import static io.camunda.optimize.upgrade.steps.UpgradeStepType.REINDEX;
import static io.camunda.optimize.upgrade.steps.UpgradeStepType.SCHEMA_DELETE_INDEX;
import static io.camunda.optimize.upgrade.steps.UpgradeStepType.SCHEMA_DELETE_TEMPLATE;

import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.upgrade.db.SchemaUpgradeClient;
import io.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.service.UpgradeStepLogEntryDto;
import io.camunda.optimize.upgrade.service.UpgradeStepLogService;
import io.camunda.optimize.upgrade.service.UpgradeValidationService;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.DeleteIndexTemplateIfExistsStep;
import io.camunda.optimize.upgrade.steps.schema.ReindexStep;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

public class UpgradeProcedure {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(UpgradeProcedure.class);
  protected final DatabaseClient dbClient;
  protected final UpgradeValidationService upgradeValidationService;
  protected final SchemaUpgradeClient schemaUpgradeClient;
  protected final UpgradeStepLogService upgradeStepLogService;

  public UpgradeProcedure(
      final DatabaseClient dbClient,
      final UpgradeValidationService upgradeValidationService,
      final SchemaUpgradeClient schemaUpgradeClient,
      final UpgradeStepLogService upgradeStepLogService) {
    this.dbClient = dbClient;
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
        } catch (final Exception e) {
          LOG.error(
              "Error while executing update from {} to {}",
              upgradePlan.getFromVersion(),
              targetVersion,
              e);
          throw new UpgradeRuntimeException("Upgrade failed.", e);
        }
      } else {
        LOG.info(
            "Target schemaVersion or a newer version is already present, no update to perform to {}.",
            targetVersion);
      }
    } else {
      LOG.info(
          "No Connection to database or no Optimize Metadata index found, skipping update to {}.",
          targetVersion);
    }
  }

  private void executeUpgradePlan(final UpgradePlan upgradePlan) {
    int currentStepCount = 1;
    final List<UpgradeStep> upgradeSteps = upgradePlan.getUpgradeSteps();
    final Map<String, UpgradeStepLogEntryDto> appliedStepsById =
        upgradeStepLogService.getAllAppliedStepsForUpdateToById(
            schemaUpgradeClient, upgradePlan.getToVersion().toString());
    for (final UpgradeStep step : upgradeSteps) {
      final UpgradeStepLogEntryDto logEntryDto =
          UpgradeStepLogEntryDto.builder()
              .indexName(getIndexNameForStep(step))
              .optimizeVersion(upgradePlan.getToVersion().toString())
              .stepType(step.getType())
              .stepNumber(currentStepCount)
              .build();
      final Optional<Instant> stepAppliedDate =
          Optional.ofNullable(appliedStepsById.get(logEntryDto.getId()))
              .map(UpgradeStepLogEntryDto::getAppliedDate);
      if (stepAppliedDate.isEmpty()) {
        LOG.info(
            "Starting step {}/{}: {} on index: {}",
            currentStepCount,
            upgradeSteps.size(),
            step.getClass().getSimpleName(),
            getIndexNameForStep(step));
        try {
          step.execute(schemaUpgradeClient);
          upgradeStepLogService.recordAppliedStep(schemaUpgradeClient, logEntryDto);
        } catch (final UpgradeRuntimeException e) {
          LOG.error("The upgrade will be aborted. Please investigate the cause and retry it..");
          throw e;
        }
        LOG.info(
            "Successfully finished step {}/{}: {} on index: {}",
            currentStepCount,
            upgradeSteps.size(),
            step.getClass().getSimpleName(),
            getIndexNameForStep(step));
      } else {
        LOG.info(
            "Skipping Step {}/{}: {} on index: {} as it was found to be previously completed already at: {}.",
            currentStepCount,
            upgradeSteps.size(),
            step.getClass().getSimpleName(),
            getIndexNameForStep(step),
            stepAppliedDate.get());
      }
      currentStepCount++;
    }
    schemaUpgradeClient.updateOptimizeVersion(upgradePlan);
  }

  private void validateVersions(final Semver schemaVersion, final UpgradePlan upgradePlan) {
    upgradeValidationService.validateSchemaVersions(
        schemaVersion.getValue(),
        upgradePlan.getFromVersion().getValue(),
        upgradePlan.getToVersion().getValue());
  }

  private String getIndexNameForStep(final UpgradeStep step) {
    if (REINDEX.equals(step.getType())) {
      final ReindexStep reindexStep = (ReindexStep) step;
      return String.format(
          "%s-TO-%s",
          dbClient
              .getIndexNameService()
              .getOptimizeIndexNameWithVersion(reindexStep.getSourceIndex()),
          dbClient
              .getIndexNameService()
              .getOptimizeIndexNameWithVersion(reindexStep.getTargetIndex()));
    } else if (SCHEMA_DELETE_INDEX.equals(step.getType())) {
      return ((DeleteIndexIfExistsStep) step).getVersionedIndexName();
    } else if (SCHEMA_DELETE_TEMPLATE.equals(step.getType())) {
      return ((DeleteIndexTemplateIfExistsStep) step).getVersionedTemplateNameWithTemplateSuffix();
    } else {
      return dbClient.getIndexNameService().getOptimizeIndexNameWithVersion(step.getIndex());
    }
  }
}
