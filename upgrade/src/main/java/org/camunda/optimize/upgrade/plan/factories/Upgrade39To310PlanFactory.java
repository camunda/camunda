/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DashboardShareIndex;
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
      .addUpgradeStep(updateDashboardShareIndexWithNewField())
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
    final String updateScript =
      getUpdateReportTileScript("reports", DashboardIndex.TILES) +
        "ctx._source.instantPreviewDashboard = false;";
    return new UpdateIndexStep(new DashboardIndex(), updateScript);
  }

  private UpdateIndexStep updateDashboardShareIndexWithNewField() {
    final String updateScript = getUpdateReportTileScript("reportShares", DashboardShareIndex.TILE_SHARES);
    return new UpdateIndexStep(new DashboardShareIndex(), updateScript);
  }

  private static String getUpdateReportTileScript(final String currentReportProperty, final String newTileProperty) {
    return
      // @formatter:off
      "List tiles = new ArrayList();" +
      "ctx._source." + currentReportProperty + ".forEach(report -> {" +
      "  Map config = report.configuration;" +
      "  if (config != null) {" +
      "    if (config.get('external') != null) {" +
      "      report.type = 'external_url'" +
      "    } else {" +
      "      report.type = 'optimize_report'" +
      "    }" +
      "  } else {" +
      "    report.type = 'optimize_report'" +
      "  }" +
      "  tiles.add(report);" +
      "});" +
      "ctx._source." + newTileProperty + " = tiles;\n" +
      "ctx._source.remove('" + currentReportProperty + "');\n";
      // @formatter:on
  }
}
