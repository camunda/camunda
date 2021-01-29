/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan.factories;

import org.camunda.optimize.service.es.schema.index.events.EventProcessMappingIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessPublishStateIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

public class Upgrade33To34PlanFactory implements UpgradePlanFactory {

  @Override
  public UpgradePlan createUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.3.0")
      .toVersion("3.4.0")
      .addUpgradeStep(migrateFlowNodeStatusConfigToFilters())
      .addUpgradeStep(migrateEventMappingEventSources())
      .addUpgradeStep(migrateEventPublishStateEventSources())
      .build();
  }

  private static UpgradeStep migrateFlowNodeStatusConfigToFilters() {
    //@formatter:off
    final String script =
      "def reportEntityType = ctx._source.data.view.entity;\n" +
      "def currentFilters = ctx._source.data.filter;\n" +
      "if (reportEntityType == 'userTask' || reportEntityType == 'flowNode') {\n" +
      "  def executionState = ctx._source.data.configuration.flowNodeExecutionState;\n" +
      "  if (executionState == 'completed') {\n" +
      "    def newFilter = [\n" +
      "      'type': 'completedOrCanceledFlowNodesOnly',\n" +
      "      'filterLevel': 'view'\n" +
      "     ];" +
      "    newFilter.data = null;" +
      "    currentFilters.add(newFilter);\n" +
      "  } else if (executionState == 'running') {\n" +
      "    def newFilter = [\n" +
      "      'type': 'runningFlowNodesOnly',\n" +
      "      'filterLevel': 'view'\n" +
      "     ];" +
      "    newFilter.data = null;" +
      "    currentFilters.add(newFilter);\n" +
      "  } else if (executionState == 'canceled') {\n" +
      "    def newFilter = [\n" +
      "      'type': 'canceledFlowNodesOnly',\n" +
      "      'filterLevel': 'view'\n" +
      "     ];" +
      "    newFilter.data = null;" +
      "    currentFilters.add(newFilter);\n" +
      "  }\n" +
      "}\n" +
      "ctx._source.data.configuration.remove(\"flowNodeExecutionState\");\n";
    //@formatter:on
    return new UpdateIndexStep(new SingleProcessReportIndex(), script);
  }

  private static UpgradeStep migrateEventMappingEventSources() {
    //@formatter:off
    final String script =
      "ctx._source.eventSources.forEach(eventSource -> {\n" +
      updateEventSourceScript() +
      "})\n";
    //@formatter:on
    return new UpdateIndexStep(new EventProcessMappingIndex(), script);
  }

  private static UpgradeStep migrateEventPublishStateEventSources() {
    //@formatter:off
    final String script =
      "ctx._source.eventImportSources.forEach(eventImportSource -> {\n" +
      "  def eventSource = eventImportSource.eventSource;\n" +
      updateEventSourceScript() +
      "})\n";
    //@formatter:on
    return new UpdateIndexStep(new EventProcessPublishStateIndex(), script);
  }

  private static String updateEventSourceScript() {
    //@formatter:off
    return
      "  if (eventSource.type == 'external') {\n" +
      "    def sourceConfig = [\n" +
      "      'includeAllGroups': true,\n" +
      "      'eventScope': eventSource.eventScope\n" +
      "     ];\n" +
      "    sourceConfig.group = null;" +
      "    eventSource.configuration = sourceConfig;\n" +
      "  } else if (eventSource.type == 'camunda') {\n" +
      "    def sourceConfig = [\n" +
      "      'eventScope': eventSource.eventScope,\n" +
      "      'processDefinitionKey': eventSource.processDefinitionKey,\n" +
      "      'versions': eventSource.versions,\n" +
      "      'tenants': eventSource.tenants,\n" +
      "      'tracedByBusinessKey': eventSource.tracedByBusinessKey,\n" +
      "      'traceVariable': eventSource.traceVariable\n" +
      "     ];\n" +
      "    sourceConfig.processDefinitionName = null;\n" +
      "    eventSource.configuration = sourceConfig;\n" +
      "  }\n" +
      "  eventSource.remove(\"processDefinitionKey\");\n" +
      "  eventSource.remove(\"versions\");\n" +
      "  eventSource.remove(\"tenants\");\n" +
      "  eventSource.remove(\"tracedByBusinessKey\");\n" +
      "  eventSource.remove(\"traceVariable\");\n" +
      "  eventSource.remove(\"eventScope\");\n";
    //@formatter:on
  }

}
