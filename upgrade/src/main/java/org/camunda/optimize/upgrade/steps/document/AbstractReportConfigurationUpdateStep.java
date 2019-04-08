/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import org.elasticsearch.index.query.QueryBuilder;

import java.util.Map;

public class AbstractReportConfigurationUpdateStep extends UpdateDataStep {

  public AbstractReportConfigurationUpdateStep(final String typeName,
                                               final QueryBuilder query,
                                               final String updateScript,
                                               final Map<String, Object> parameters) {
    super(typeName, query, updateScript, parameters);
  }

  protected static String getMigrateCompatibleFieldsScript() {
    // @formatter:off
    return
      "if (reportData.configuration != null) {\n" +
      "  if (reportData.configuration.showInstanceCount != null)" +
      "      newConfig.showInstanceCount = reportData.configuration.showInstanceCount;\n" +
      "  if (reportData.configuration.pointMarkers != null)" +
      "      newConfig.pointMarkers = reportData.configuration.pointMarkers;\n" +
      "  if (reportData.configuration.precision != null)" +
      "      newConfig.precision = reportData.configuration.precision;\n" +
      "  if (reportData.configuration.hideAbsoluteValue != null)" +
      "      newConfig.hideAbsoluteValue = reportData.configuration.hideAbsoluteValue;\n" +
      "  if (reportData.configuration.hideRelativeValue != null)" +
      "      newConfig.hideRelativeValue = reportData.configuration.hideRelativeValue;\n" +
      "  if (reportData.configuration.xLabel != null)" +
      "      newConfig.xLabel = reportData.configuration.xLabel;\n" +
      "  if (reportData.configuration.yLabel != null)" +
      "      newConfig.yLabel = reportData.configuration.yLabel;\n" +
      "  if (reportData.configuration.xml != null)" +
      "      newConfig.xml = reportData.configuration.xml;\n" +
      "  if (reportData.configuration.excludedColumns != null)" +
      "      newConfig.excludedColumns = reportData.configuration.excludedColumns;\n" +
      "  if (reportData.configuration.columnOrder != null)" +
      "      newConfig.columnOrder = reportData.configuration.columnOrder;\n" +
      "}\n";
    // @formatter:on
  }

  // needed in order to allow modifications of map parameters (e.g. the default config passed into script as parameter)
  protected static String getDeepCopyMapScript() {
    // @formatter:off
    return
      "Map deepCopyMap(Map original) {\n" +
      "  Map copy = new HashMap();\n" +
      "  for (def key : original.keySet()) {\n" +
      "    def originalValue = original.get(key);\n" +
      "    def copyValue = originalValue instanceof Map ? deepCopyMap(originalValue) : originalValue;\n" +
      "    copy.put(key, copyValue);\n" +
      "  }" +
      "  return copy;\n" +
      "}\n";
    // @formatter:on
  }
}
