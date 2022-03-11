/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

public class Upgrade371To372PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.7.1")
      .toVersion("3.7.2")
      .addUpgradeSteps(migrateReportFiltersAndConfig())
      .build();
  }

  private static List<UpgradeStep> migrateReportFiltersAndConfig() {
    // This step has to be repeated from the 36To370 upgrade as it was not properly applied there because the index
    // was already at v8 from the 35To36 upgrade, meaning the UpdateIndexStep in 36To370 was never applied.

    // @formatter:off
    final String reportMigrationScript =
      "if (ctx._source.data != null) {\n" +
        // migrate report filters
        "def filters = ctx._source.data.filter;\n" +
        "if (filters != null) {\n" +
          "for (def filter : filters) {\n" +
            "if (\"startDate\".equals(filter.type)) {\n" +
              "filter.type = \"instanceStartDate\";\n" +
            "}\n" +
            "if (\"endDate\".equals(filter.type)) {\n" +
              "filter.type = \"instanceEndDate\";\n" +
            "}\n" +
          "}\n" +
        "}\n" +
        // add default logScale value
        "def config = ctx._source.data.configuration;\n" +
        "if (config != null && config.logScale == null) {\n" +
          "config.logScale = false;\n" +
        "}\n" +
      "}";
    // @formatter:on
    return Stream.of(new SingleProcessReportIndex(), new SingleDecisionReportIndex())
      .map(index -> new UpdateDataStep(index, matchAllQuery(), reportMigrationScript))
      .collect(Collectors.toList());
  }

}
