/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import com.google.common.collect.ImmutableMap;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;

public class UpgradeCombinedReportSettingsStep extends AbstractReportConfigurationUpdateStep {
  public UpgradeCombinedReportSettingsStep(final Map defaultReportConfiguration,
                                           final Map<String, Map> singleReportsViewAndVisualization) {
    super(
      COMBINED_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      getDeepCopyMapScript() +
        "def reportData = ctx._source.data;\n" +
        "def singleReports = params.singleReports;\n" +
        // migrate reportId list to new list of reports
        "if (reportData.reportIds != null && reportData.reportIds.length > 0) {\n" +
        "  def reports = new ArrayList();\n" +
        "  def reportIds = reportData.reportIds;\n" +
        "  for(int i=0; i < reportIds.size(); i++) {\n" +
        "    def reportEntry = new HashMap();\n" +
        "    reportEntry.id =  reportIds.get(i);\n" +
        "    if (reportData.configuration.color != null && i < reportData.configuration.color.size()) {\n" +
        "      reportEntry.color = reportData.configuration.color.get(i);\n" +
        "    }\n" +
        "    reports.add(reportEntry);\n" +
        "  }" +
        "  reportData.reports = reports;\n" +
        "  reportData.remove(\"reportIds\");\n" +
        "}\n" +
        "reportData.configuration?.remove(\"color\");\n" +
        // config structure migration
        "def newConfig = deepCopyMap(params.defaultConfiguration);\n" +
        getMigrateCompatibleFieldsScript() +
        // incompatible field migration
        "if (reportData.reports != null\n" +
        "    && singleReports != null\n" +
        "    && reportData.reports.length > 0) {\n" +
        "  def firstReportId = reportData.reports[0].id;\n" +
        "  def singleReportData = singleReports.get(firstReportId);\n" +
        "  if (singleReportData != null) {\n" +
        "    reportData.visualization = singleReportData.visualization;" +
        "    if (reportData.configuration?.targetValue != null) {\n" +
        //     #1 IF visualization is line OR bar OR number
        "      if (singleReportData.visualization == \"line\" " +
        "          || singleReportData.visualization == \"bar\"\n" +
        "          || singleReportData.visualization == \"number\") {\n" +
        //       #1.1 AND view property is frequency
        "        if (singleReportData.view?.property == \"frequency\") {\n" +
        //         store target as value and isBelow in countChart
        "          if (reportData.configuration.targetValue.active != null) \n" +
        "            newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
        "          if (reportData.configuration.targetValue.values != null) {\n" +
        "            newConfig.targetValue.countChart.value = reportData.configuration.targetValue.values.target;\n" +
        "            if (!(newConfig.targetValue.countChart.value instanceof String)) {\n" +
        "              newConfig.targetValue.countChart.value = \n" +
        "                  String.valueOf(newConfig.targetValue.countChart.value);\n" +
        "            }\n" +
        "            newConfig.targetValue.countChart.isBelow = reportData.configuration.targetValue.values.isBelow;\n" +
        "          }\n" +
        "        }\n" +
        //       #1.2 AND view property is duration
        "        else if (singleReportData.view?.property == \"duration\") {\n" +
        //         store target as value, dateFormat as unit and isBelow as durationChart
        "          if (reportData.configuration.targetValue.active != null) \n" +
        "            newConfig.targetValue.active = reportData.configuration.targetValue.active;\n" +
        "          if (reportData.configuration.targetValue.values != null) {\n" +
        "            newConfig.targetValue.durationChart.value = reportData.configuration.targetValue.values.target;\n" +
        "            if (!(newConfig.targetValue.durationChart.value instanceof String)) {\n" +
        "              newConfig.targetValue.durationChart.value = \n" +
        "                  String.valueOf(newConfig.targetValue.durationChart.value);\n" +
        "            }\n" +
        "            if (reportData.configuration.targetValue.values.dateFormat != null\n" +
        "                && reportData.configuration.targetValue.values.dateFormat != \"\") {\n" +
        "              newConfig.targetValue.durationChart.unit = reportData.configuration.targetValue.values.dateFormat;\n" +
        "            }\n" +
        "            newConfig.targetValue.durationChart.isBelow = reportData.configuration.targetValue.values.isBelow;\n" +
        "          }\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "reportData.configuration = newConfig;\n",
      ImmutableMap.of(
        "defaultConfiguration", defaultReportConfiguration,
        "singleReports", singleReportsViewAndVisualization
      )
    );
  }

}
