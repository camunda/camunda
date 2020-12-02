/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.plan;

import com.google.common.collect.ImmutableMap;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.events.EventIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UpgradeFrom32To33Factory {

  private static final SingleDecisionReportIndex DECISION_REPORT_INDEX = new SingleDecisionReportIndex();
  private static final SingleProcessReportIndex PROCESS_REPORT_INDEX = new SingleProcessReportIndex();
  private static final ProcessDefinitionIndex PROCESS_DEFINITION_INDEX = new ProcessDefinitionIndex();
  private static final DecisionDefinitionIndex DECISION_DEFINITION_INDEX = new DecisionDefinitionIndex();
  private static final EventProcessDefinitionIndex EVENT_PROCESS_DEFINITION_INDEX = new EventProcessDefinitionIndex();

  public static UpgradePlan createUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion("3.2.0")
      .toVersion("3.3.0")
      .addUpgradeSteps(markExistingDefinitionsAsNotDeleted())
      .addUpgradeStep(migrateProcessReports())
      .addUpgradeStep(migrateDistributedByFieldForDecisionReports())
      .addUpgradeStep(migrateDashboardAvailableFilters())
      .addUpgradeStep(new UpdateIndexStep(new EventIndex(), null))
      .build();
  }

  private static UpgradeStep migrateDashboardAvailableFilters() {
    //@formatter:off
    final String script =
      "def currentFilters = ctx._source.availableFilters;\n" +
      "for (def currentFilter : currentFilters) {\n" +
      "  if (currentFilter.type == \'variable\' && currentFilter.data != null) {\n" +
      "    if (currentFilter.data.type == \'Boolean\' || currentFilter.data.type == \'Date\') {\n" +
      "      currentFilter.data.data = null;\n" +
      "    } else {\n" +
      "      currentFilter.data.data.allowCustomValues = false;\n" +
      "    }\n" +
      "  }" +
      "}\n";
    //@formatter:on
    return new UpdateIndexStep(new DashboardIndex(), script);
  }

  private static List<UpgradeStep> markExistingDefinitionsAsNotDeleted() {
    final String script = "ctx._source.deleted = false;";
    return Arrays.asList(
      new UpdateIndexStep(PROCESS_DEFINITION_INDEX, script),
      new UpdateIndexStep(DECISION_DEFINITION_INDEX, script),
      new UpdateIndexStep(EVENT_PROCESS_DEFINITION_INDEX, script)
    );
  }

  private static UpgradeStep migrateProcessReports() {
    final String updateScript = buildDistributedByFieldMigrationScript() + buildFilterLevelMigrationScript();
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put("distributeByNoneDto", new NoneDistributedByDto())
      .build();
    return new UpdateIndexStep(PROCESS_REPORT_INDEX, updateScript, params);
  }

  private static UpgradeStep migrateDistributedByFieldForDecisionReports() {
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put("distributeByNoneDto", new NoneDistributedByDto())
      .build();
    return new UpdateIndexStep(DECISION_REPORT_INDEX, buildDistributedByFieldMigrationScript(), params);
  }

  private static String buildDistributedByFieldMigrationScript() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("distributeByField", "distributedBy")
        .build()
    );
    //@formatter:off
    return substitutor.replace(
      "def data = ctx._source.data;\n" +
        "def configuration = data.configuration;\n" +
        "if(configuration.${distributeByField} == null) {\n" +
          "data.${distributeByField} = params.distributeByNoneDto;\n" +
        "} else {\n" +
          "data.${distributeByField} = configuration.${distributeByField};\n" +
        "}\n" +
        "configuration.remove(\"${distributeByField}\");\n"
    );
    //@formatter:on
  }

  private static String buildFilterLevelMigrationScript() {
    //@formatter:off
    return
      "def reportData = ctx._source.data;\n" +
      "def view = reportData.view;\n" +
      "boolean isUserTaskView = false;\n" +
      "if (view != null) {\n" +
      "  isUserTaskView = view.entity == 'userTask';\n" +
      "}\n" +
      "data.filter.stream().forEach(filterEntry -> {\n" +
      "  if ((filterEntry.type == 'candidateGroup' || filterEntry.type == 'assignee') && isUserTaskView) {\n" +
      "    filterEntry.filterLevel = 'FLOWNODE';\n" +
      "  } else {\n" +
      "    filterEntry.filterLevel = 'INSTANCE';\n" +
      "  }\n" +
      "})\n";
    //@formatter:on
  }

}
