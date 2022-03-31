/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.SettingsIndex;
import org.camunda.optimize.service.es.schema.index.index.PositionBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

public class Upgrade37To380PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.7")
      .toVersion("3.8.0")
      .addUpgradeStep(addLastEntityTimestampToPositionBasedImportIndices())
      .addUpgradeStep(addLastEntityTimestampAndRenameDatasourceFieldInPositionBasedImportIndices())
      .addUpgradeSteps(migrateAggregationTypeFields())
      .addUpgradeStep(updateSettingsIndexWithNewField())
      .build();
  }

  private UpdateIndexStep updateSettingsIndexWithNewField() {
    // @formatter:off
    final String updateScript =
      "ctx._source.sharingEnabled = null;";
    // @formatter:on
    return new UpdateIndexStep(new SettingsIndex(), updateScript);
  }

  private static UpdateIndexStep addLastEntityTimestampAndRenameDatasourceFieldInPositionBasedImportIndices() {
    // @formatter:off
    final String updateScript =
      "def dataSourceType;\n" +
      "if (ctx._source.esTypeIndexRefersTo.equals(\"externalVariableUpdateImportIndex\")) {\n" +
      "  dataSourceType = \"ingested\"\n" +
      "} else if (ctx._source.esTypeIndexRefersTo.startsWith(\"eventStateProcessing-\")) {\n" +
      "  dataSourceType = \"events\"\n" +
      "} else {\n" +
      "  dataSourceType = \"engine\"\n" +
      "}\n" +
      "ctx._source.dataSource = [\n" +
      "  'name': ctx._source.engine,\n" +
      "  'type': dataSourceType\n" +
      "];\n" +
      "ctx._source.remove(\"engine\");\n";
    // @formatter:on
    return new UpdateIndexStep(
      new TimestampBasedImportIndex(),
      updateScript
    );
  }

  private static UpdateIndexStep addLastEntityTimestampToPositionBasedImportIndices() {
    // @formatter:off
    final String updateScript =
      "ctx._source.dataSource = ctx._source.dataSourceDto;\n" +
      "ctx._source.timestampOfLastEntity = params.beginningOfTime;\n" +
      "ctx._source.remove(\"dataSourceDto\");\n";
    // @formatter:on
    return new UpdateIndexStep(
      new PositionBasedImportIndex(),
      updateScript,
      Map.of("beginningOfTime", DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT)
        .format(ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()))),
      Collections.emptySet()
    );
  }

  private static List<UpgradeStep> migrateAggregationTypeFields() {
    // @formatter:off
    final String updateScript =
        "def newAggTypes = new ArrayList();\n" +
        "def reportData = ctx._source.data;\n" +
        "if (reportData != null && reportData.configuration != null " +
        "      && reportData.configuration.aggregationTypes != null && !reportData.configuration.aggregationTypes.isEmpty()) {\n" +
        "  def currentAggTypes = ctx._source.data.configuration.aggregationTypes;\n" +
        "  for (def aggType : currentAggTypes) {\n" +
        "    def isMedian = aggType.equals(\"median\");\n" +
        "    def newAgg = \n" +
        "      [\n" +
        "        'type': isMedian ? 'percentile' : aggType,\n" +
        "        'value': isMedian ? '50.0' : null\n" +
        "      ];\n" +
        "    newAggTypes.add(newAgg);\n" +
        "  }\n" +
        "  ctx._source.data.configuration.aggregationTypes = newAggTypes;\n" +
        "}";
    // @formatter:on
    return List.of(
      new UpdateIndexStep(new SingleProcessReportIndex(), updateScript),
      new UpdateIndexStep(new SingleDecisionReportIndex(), updateScript)
    );
  }

}
