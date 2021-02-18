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
      .addUpgradeStep(migrateSingleProcessReportV6())
      .addUpgradeStep(migrateEventMappingEventSources())
      .addUpgradeStep(migrateEventPublishStateEventSources())
      .build();
  }

  private static UpgradeStep migrateSingleProcessReportV6() {
    return new UpdateIndexStep(
      new SingleProcessReportIndex(),
      createMigrateFlowNodeStatusConfigToFiltersScript() + createProcessReportToMultiMeasureFieldsScript()
    );
  }

  private static String createMigrateFlowNodeStatusConfigToFiltersScript() {
    //@formatter:off
    return
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
  }

  private static String createProcessReportToMultiMeasureFieldsScript() {
    //@formatter:off
    return
      "def reportConfiguration = ctx._source.data.configuration;\n" +
      "reportConfiguration.aggregationTypes = [];\n" +
      "if (reportConfiguration.aggregationType != null) {\n" +
      "  reportConfiguration.aggregationTypes.add(reportConfiguration.aggregationType);\n" +
      "}\n" +
      "reportConfiguration.remove(\"aggregationType\");\n" +
      "reportConfiguration.userTaskDurationTimes = [];\n" +
      "if (reportConfiguration.userTaskDurationTime != null) {\n" +
      "  reportConfiguration.userTaskDurationTimes.add(reportConfiguration.userTaskDurationTime);\n" +
      "}\n" +
      "reportConfiguration.remove(\"userTaskDurationTime\");\n" +
      "def reportView = ctx._source.data.view;\n" +
      "reportView.properties = [];\n" +
      "if (reportView.property != null) {\n" +
      "  reportView.properties.add(reportView.property);\n" +
      "}\n" +
      "reportView.remove(\"property\");\n";
    //@formatter:on
  }

  private static UpgradeStep migrateEventMappingEventSources() {
    //@formatter:off
    final String script =
      "ctx._source.eventSources.forEach(eventSource -> {\n" +
      "  if (eventSource.type == 'external') {\n" +
      "    def sourceConfig = [\n" +
      "      'includeAllGroups': true,\n" +
      "      'group': null,\n" +
      "      'eventScope': eventSource.eventScope\n" +
      "     ];\n" +
      "    eventSource.configuration = sourceConfig;\n" +
      "  } else if (eventSource.type == 'camunda') {\n" +
      "    def sourceConfig = [\n" +
      "      'eventScope': eventSource.eventScope,\n" +
      "      'processDefinitionKey': eventSource.processDefinitionKey,\n" +
      "      'processDefinitionName': null,\n" +
      "      'versions': eventSource.versions,\n" +
      "      'tenants': eventSource.tenants,\n" +
      "      'tracedByBusinessKey': eventSource.tracedByBusinessKey,\n" +
      "      'traceVariable': eventSource.traceVariable\n" +
      "     ];\n" +
      "    eventSource.configuration = sourceConfig;\n" +
      "  }\n" +
      "  eventSource.remove(\"processDefinitionKey\");\n" +
      "  eventSource.remove(\"versions\");\n" +
      "  eventSource.remove(\"tenants\");\n" +
      "  eventSource.remove(\"tracedByBusinessKey\");\n" +
      "  eventSource.remove(\"traceVariable\");\n" +
      "  eventSource.remove(\"eventScope\");\n" +
      "})\n";
    //@formatter:on
    return new UpdateIndexStep(new EventProcessMappingIndex(), script);
  }

  private static UpgradeStep migrateEventPublishStateEventSources() {
    //@formatter:off
    final String script =
      "ctx._source.eventImportSources.forEach(eventImportSource -> {\n" +
      "  def existingEventSource = eventImportSource.eventSource;\n" +
      "  def eventSourceConfigs = new ArrayList();\n" +
      "  if (existingEventSource.type == 'external') {\n" +
      "    def sourceConfig = [\n" +
      "      'includeAllGroups': true,\n" +
      "      'group': null,\n" +
      "      'eventScope': existingEventSource.eventScope\n" +
      "     ];\n" +
      "     eventSourceConfigs.add(sourceConfig);\n" +
      "  } else if (existingEventSource.type == 'camunda') {\n" +
      "    def sourceConfig = [\n" +
      "      'eventScope': existingEventSource.eventScope,\n" +
      "      'processDefinitionKey': existingEventSource.processDefinitionKey,\n" +
      "      'processDefinitionName': null,\n" +
      "      'versions': existingEventSource.versions,\n" +
      "      'tenants': existingEventSource.tenants,\n" +
      "      'tracedByBusinessKey': existingEventSource.tracedByBusinessKey,\n" +
      "      'traceVariable': existingEventSource.traceVariable\n" +
      "     ];\n" +
      "     eventSourceConfigs.add(sourceConfig);\n" +
      "  }\n" +
      "  eventImportSource.eventImportSourceType = existingEventSource.type;\n" +
      "  eventImportSource.eventSourceConfigurations = eventSourceConfigs;\n" +
      "  eventImportSource.remove(\"eventSource\");\n" +
      "})\n";
    //@formatter:on
    return new UpdateIndexStep(new EventProcessPublishStateIndex(), script);
  }

}
