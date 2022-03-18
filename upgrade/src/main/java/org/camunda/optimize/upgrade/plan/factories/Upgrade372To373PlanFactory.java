/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

public class Upgrade372To373PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.7.2")
      .toVersion("3.7.3")
      .addUpgradeStep(deleteInvalidDecisionVariables())
      .build();
  }

  private static UpgradeStep deleteInvalidDecisionVariables() {
    final List<String> supportedTypes = ReportConstants.ALL_SUPPORTED_DECISION_VARIABLE_TYPES.stream()
      .map(variableType -> variableType.getId().toLowerCase())
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
          boolQuery().must(nestedQuery(
            DecisionInstanceIndex.INPUTS,
            termsQuery(
              DecisionInstanceIndex.INPUTS + "." + DecisionInstanceIndex.VARIABLE_VALUE_TYPE,
              "Json",
              "Object"
            ),
            ScoreMode.None
          )))
        .should(
          boolQuery().must(nestedQuery(
            DecisionInstanceIndex.OUTPUTS,
            termsQuery(
              DecisionInstanceIndex.OUTPUTS + "." + DecisionInstanceIndex.VARIABLE_VALUE_TYPE,
              "Json",
              "Object"
            ),
            ScoreMode.None
          ))
        ),
      decisionVarDeleteScript,
      Map.of("supportedTypes", supportedTypes)
    );
  }

}
