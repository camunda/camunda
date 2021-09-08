/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.MappingMetadataUtil;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradeExecutionDependencies;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Upgrade35To36PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan(final UpgradeExecutionDependencies dependencies) {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.5.0")
      .toVersion("3.6.0")
      .addUpgradeSteps(migrateProcessInstances(dependencies, false))
      .addUpgradeSteps(migrateProcessInstances(dependencies, true))
      .addUpgradeSteps(migrateReports())
      .build();
  }

  private static List<UpgradeStep> migrateProcessInstances(final UpgradeExecutionDependencies dependencies,
                                                           final boolean eventBased) {
    final List<String> indexIdentifiers = MappingMetadataUtil.retrieveProcessInstanceIndexIdentifiers(
      dependencies.getEsClient(), eventBased
    );

    return indexIdentifiers.stream()
      .map(indexIdentifier -> new UpdateIndexStep(
        eventBased ? new EventProcessInstanceIndex(indexIdentifier) : new ProcessInstanceIndex(indexIdentifier),
        getDuplicateDefinitionDataToFlowNodesScript()
      ))
      .collect(Collectors.toList());
  }

  private static String getDuplicateDefinitionDataToFlowNodesScript() {
    // @formatter:off
    return "" +
      "def processInstance = ctx._source;" +
      "def flowNodeInstances = ctx._source.flowNodeInstances;" +
      "for (flowNode in flowNodeInstances) {" +
        "flowNode.definitionKey = processInstance.processDefinitionKey;" +
        "flowNode.definitionVersion = processInstance.processDefinitionVersion;" +
        "flowNode.tenantId = processInstance.tenantId;" +
      "}";
    // @formatter:on
  }

  private static List<UpgradeStep> migrateReports() {
    return Stream.of(new SingleProcessReportIndex(), new SingleDecisionReportIndex())
      .map(index -> new UpdateIndexStep(index, getReportMigrationScript()))
      .collect(Collectors.toList());
  }

  private static String getReportMigrationScript() {
    // @formatter:off
    return
      "if (ctx._source.data != null) {" +
        "def configuration = ctx._source.data.configuration;" +
        "if (configuration != null) {" +
          "configuration.measureVisualizations = [\"frequency\": \"bar\", \"duration\": \"line\"];" +
          "configuration.stackedBar = false;" +
        "}" +
      "}"
      ;
    // @formatter:on
  }

}
