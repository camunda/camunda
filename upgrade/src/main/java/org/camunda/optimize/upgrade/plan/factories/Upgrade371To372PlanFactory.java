/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class Upgrade371To372PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.7.1")
      .toVersion("3.7.2")
      .addUpgradeSteps(migrateReportFiltersAndConfig())
      .addUpgradeStep(deleteInvalidDecisionVariables())
      .build();
  }

  private static UpgradeStep deleteInvalidDecisionVariables() {
    final List<String> supportedTypes = ReportConstants.ALL_SUPPORTED_DECISION_VARIABLE_TYPES.stream()
      .map(type -> type.getId().toLowerCase())
      .collect(Collectors.toList());
    // @formatter:off
    final String decisionVarDeleteScript =
      "ctx._source.inputs.removeIf(input -> {\n" +
      "  !params.supportedTypes.contains(input.type.toLowerCase())\n" +
      "});\n" +
      "ctx._source.outputs.removeIf(output -> {\n" +
      "  !params.supportedTypes.contains(output.type.toLowerCase())\n" +
      "});\n";
    // @formatter:on
    return new UpdateDataStep(
      new DecisionInstanceIndex("*"),
      boolQuery()
        .should(
          boolQuery().mustNot(nestedQuery(
            DecisionInstanceIndex.INPUTS,
            termsQuery(DecisionInstanceIndex.INPUTS + "." + DecisionInstanceIndex.VARIABLE_VALUE_TYPE, supportedTypes),
            ScoreMode.None
          )))
        .should(
          boolQuery().mustNot(nestedQuery(
            DecisionInstanceIndex.OUTPUTS,
            termsQuery(DecisionInstanceIndex.OUTPUTS + "." + DecisionInstanceIndex.VARIABLE_VALUE_TYPE, supportedTypes),
            ScoreMode.None
          ))
        ),
      decisionVarDeleteScript,
      Map.of("supportedTypes", supportedTypes)
    );
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
