/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.InstantPreviewDashboardMetadataIndex;
import org.camunda.optimize.service.es.schema.index.index.PositionBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

public class Upgrade39To310PlanFactory implements UpgradePlanFactory {
  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies upgradeExecutionDependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.9")
      .toVersion("3.10.0")
      .addUpgradeStep(addZeebeRecordSequenceFieldToPositionBasedImportIndex())
      .addUpgradeStep(updateDashboardIndexWithNewField())
      .addUpgradeStep(new CreateIndexStep(new InstantPreviewDashboardMetadataIndex()))
      .addUpgradeStep(updateSingleProcessReportIndexWithNewField())
      .build();
  }

  private UpdateIndexStep addZeebeRecordSequenceFieldToPositionBasedImportIndex() {
    return new UpdateIndexStep(new PositionBasedImportIndex(), "ctx._source.sequenceOfLastEntity = 0;");
  }

  private UpgradeStep updateSingleProcessReportIndexWithNewField() {
    final String updateScript = "ctx._source.data.instantPreviewReport = false;";
    return new UpdateIndexStep(new SingleProcessReportIndex(), updateScript);
  }

  private UpdateIndexStep updateDashboardIndexWithNewField() {
    // @formatter:off
    final String updateScript =
      "ctx._source.instantPreviewDashboard = false;";
    // @formatter:on
    return new UpdateIndexStep(new DashboardIndex(), updateScript);
  }
}