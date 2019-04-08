/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import org.elasticsearch.index.query.QueryBuilders;

import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;

public class UpgradeSingleProcessReportSettingsStep extends AbstractReportConfigurationUpdateStep {
  public UpgradeSingleProcessReportSettingsStep(final Map defaultReportConfiguration) {
    super(
      SINGLE_PROCESS_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      getDeepCopyMapScript() +
        "def reportData = ctx._source.data;\n" +
        "def newConfig = deepCopyMap(params.defaultConfiguration);\n" +
        getMigrateCompatibleFieldsScript() +
        getMigrate21IncompatibleFieldScript() +
        getMigrate22IncompatibleFieldsScript() +
        getMigrate23IncompatibleFieldsScript() +
        getMigrateReportViewStructureScript() +
        "reportData.configuration = newConfig;\n",
        Collections.singletonMap("defaultConfiguration", defaultReportConfiguration)
    );
  }

  private static String getMigrate21IncompatibleFieldScript() {
    // @formatter:off
    return
      "if (reportData.configuration != null) {\n" +
      // #4 IF view.operation is rawData AND columnOrder is set
      "  if (reportData.view?.operation == \"rawData\"\n" +
      "      && reportData.configuration.columnOrder != null) {\n" +
      "      newConfig.columnOrder = reportData.configuration.columnOrder;\n" +
      //     2.1 Incompatibility
      //     #4.1 AND IF columnOrder.processInstanceProps is set
      "      if (newConfig.columnOrder.processInstanceProps != null) {\n" +
      //       save processInstanceProps as instanceProps and clear processInstanceProps
      //       we modify the reportData here as columnOrder
      "        newConfig.columnOrder.instanceProps = newConfig.columnOrder.processInstanceProps;\n" +
      "        newConfig.columnOrder.processInstanceProps = null;\n" +
      "      }\n" +
      "  }\n" +
      "}\n";
    // @formatter:on
  }

  private static String getMigrate22IncompatibleFieldsScript() {
    // @formatter:off
    return
      "if (reportData.configuration != null) {\n" +
      // #3 IF visualization is heatmap AND alwaysShowTooltips is present
      "  if (reportData.visualization == \"heat\"\n" +
      "      && reportData.configuration.alwaysShowTooltips != null) {\n" +
      //     store alwaysShowTooltips value as alwaysShowRelative
      "      newConfig.alwaysShowRelative = reportData.configuration.alwaysShowTooltips;\n" +
      //     and as alwaysShowAbsolute
      "      newConfig.alwaysShowAbsolute = reportData.configuration.alwaysShowTooltips;\n" +
      //     AND set the inverted value as {{hideRelativeValue}}
      "      newConfig.hideRelativeValue = !reportData.configuration.alwaysShowTooltips;\n" +
      //     and as {{hideAbsoluteValue}}
      "      newConfig.hideAbsoluteValue = !reportData.configuration.alwaysShowTooltips;\n" +
      "  }\n" +
      "}\n";
    // @formatter:on
  }

  private static String getMigrate23IncompatibleFieldsScript() {
    // @formatter:off
    return
      "if (reportData.configuration != null) {\n" +
      // #1 convert color array to single value
      "  if (reportData.configuration.color?.get(0) != null) {\n" +
      "    newConfig.color = reportData.configuration.color.get(0);\n" +
      "  }\n" +
      // #2.1 IF visualization is heatmap
      "  if (reportData.visualization == \"heat\") {\n" +
      //   store inverted hideRelativeValue value as alwaysShowRelative
      "    if (reportData.configuration.hideRelativeValue != null) " +
      "      newConfig.alwaysShowRelative = !reportData.configuration.hideRelativeValue;\n" +
      //   store inverted hideAbsoluteValue value as alwaysShowAbsolute
      "    if (reportData.configuration.hideAbsoluteValue != null) " +
      "      newConfig.alwaysShowAbsolute = !reportData.configuration.hideAbsoluteValue;\n" +
      "  }\n" +
      "  if (reportData.configuration.targetValue != null) {\n" +
      //   #2.1.1 IF visualization is heatmap AND view is flow node duration
      "    if (reportData.visualization == \"heat\"\n" +
      "        && reportData.view?.entity == \"flowNode\"\n" +
      "        && reportData.view?.property == \"duration\" ) {\n" +
      //     store targetValue.values as heatmapTargetValue.values
      //     use targetValue.active as heatmapTargetValue.active
      "      if (reportData.configuration.targetValue.active != null) \n" +
      "        newConfig.heatmapTargetValue.active = reportData.configuration.targetValue.active;\n" +
      "      if (reportData.configuration.targetValue.values != null) {\n" +
      "        newConfig.heatmapTargetValue.values = reportData.configuration.targetValue.values;\n" +
      "        if(newConfig.heatmapTargetValue.values instanceof Map) { \n" +
      "          for (key in newConfig.heatmapTargetValue.values.keySet()) {\n" +
      "            def entry = newConfig.heatmapTargetValue.values[key];\n" +
      "            if (!(entry.value instanceof String)) {\n" +
      "              entry.value = String.valueOf(entry.value);\n" +
      "            }\n" +
      "          }\n" +
      "        }\n" +
      "      }\n" +
      "    }\n" +
      //   #2.2 IF visualization is line OR bar
      "    else if (reportData.visualization == \"line\" || reportData.visualization == \"bar\") {\n" +
      //     #2.2.1 AND view property is frequency
      "      if (reportData.view?.property == \"frequency\") {\n" +
      //       store target as value and isBelow in countChart
      "        if (reportData.configuration.targetValue.active != null) \n" +
      "          newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
      "        if (reportData.configuration.targetValue.values != null) {\n" +
      "          newConfig.targetValue.countChart.value = reportData.configuration.targetValue.values.target;\n" +
      "          if (!(newConfig.targetValue.countChart.value instanceof String)) {\n" +
      "            newConfig.targetValue.countChart.value = \n" +
      "                String.valueOf(newConfig.targetValue.countChart.value);\n" +
      "          }\n" +
      "          newConfig.targetValue.countChart.isBelow = reportData.configuration.targetValue.values.isBelow;\n" +
      "        }\n" +
      "      }\n" +
      //     #2.2.2 AND view property is duration
      "      else if (reportData.view?.property == \"duration\") {\n" +
      //       store target as value, dateFormat as unit and isBelow as durationChart
      "        if (reportData.configuration.targetValue.active != null) \n" +
      "          newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
      "        if (reportData.configuration.targetValue.values != null) {\n" +
      "          newConfig.targetValue.durationChart.value = reportData.configuration.targetValue.values.target;\n" +
      "          if (!(newConfig.targetValue.durationChart.value instanceof String)) {\n" +
      "            newConfig.targetValue.durationChart.value = \n" +
      "                String.valueOf(newConfig.targetValue.durationChart.value);\n" +
      "          }\n" +
      "          if (reportData.configuration.targetValue.values.dateFormat != null\n" +
      "              && reportData.configuration.targetValue.values.dateFormat != \"\") {\n" +
      "            newConfig.targetValue.durationChart.unit = reportData.configuration.targetValue.values.dateFormat;\n" +
      "          }\n" +
      "          newConfig.targetValue.durationChart.isBelow = reportData.configuration.targetValue.values.isBelow;\n" +
      "        }\n" +
      "      }\n" +
      "    }\n" +
      //   #2.3 IF report is Count PI frequency grouped by none:
      "    else if (reportData.view?.operation == \"count\"\n" +
      "          && reportData.view?.entity == \"processInstance\"\n" +
      "          && reportData.view?.property == \"frequency\"\n" +
      "          && reportData.groupBy?.type == \"none\") {\n" +
      //     store baseline and target in countProgress
      "      if (reportData.configuration.targetValue.active != null) \n" +
      "        newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
      "      if (reportData.configuration.targetValue.values != null) {\n" +
      "        newConfig.targetValue.countProgress.baseline = reportData.configuration.targetValue.values.baseline;\n" +
      "        if (!(newConfig.targetValue.countProgress.baseline instanceof String)) {\n" +
      "          newConfig.targetValue.countProgress.baseline = \n" +
      "              String.valueOf(newConfig.targetValue.countProgress.baseline);\n" +
      "        }\n" +
      "        newConfig.targetValue.countProgress.target = reportData.configuration.targetValue.values.target;\n" +
      "        if (!(newConfig.targetValue.countProgress.target instanceof String)) {\n" +
      "          newConfig.targetValue.countProgress.target = \n" +
      "              String.valueOf(newConfig.targetValue.countProgress.target);\n" +
      "        }\n" +
      "      }\n" +
      "    }\n" +
      //   #2.4 IF report is PI duration and groupByNone
      "    else if (reportData.view?.entity == \"processInstance\"\n" +
      "          && reportData.view?.property == \"duration\"\n" +
      "          && reportData.groupBy?.type == \"none\") {\n" +
      //     store baseline and target in durationProgress
      "      if (reportData.configuration.targetValue.active != null) \n" +
      "        newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
      "      if (reportData.configuration.targetValue.values != null) {\n" +
      "        newConfig.targetValue.durationProgress.baseline = \n" +
      "            reportData.configuration.targetValue.values.baseline;\n" +
      "        if (!(newConfig.targetValue.durationProgress.baseline.value instanceof String)) {\n" +
      "          newConfig.targetValue.durationProgress.baseline.value = \n" +
      "              String.valueOf(newConfig.targetValue.durationProgress.baseline.value);\n" +
      "        }\n" +
      "        newConfig.targetValue.durationProgress.target = reportData.configuration.targetValue.values.target;\n" +
      "        if (!(newConfig.targetValue.durationProgress.target.value instanceof String)) {\n" +
      "          newConfig.targetValue.durationProgress.target.value = \n" +
      "              String.valueOf(newConfig.targetValue.durationProgress.target.value);\n" +
      "        }\n" +
      "      }\n" +
      "    }\n" +
      "  }\n" +
      "}\n";
    // @formatter:on
  }

  private static String getMigrateReportViewStructureScript() {
    // @formatter:off
    return
      "if (reportData.view != null) {\n" +
      "  if (reportData.view.operation != null) {\n" +
      "    if (reportData.view.operation == \"rawData\") {\n" +
      "      reportData.view.property = \"rawData\";\n" +
      "    } else if (reportData.view?.property == \"duration\") {\n" +
      "      newConfig.aggregationType = reportData.view.operation;\n" +
      "    }\n" +
      "  }\n" +
      "  reportData.view.remove('operation');\n"+
      "}\n";
    // @formatter:on
  }

}
