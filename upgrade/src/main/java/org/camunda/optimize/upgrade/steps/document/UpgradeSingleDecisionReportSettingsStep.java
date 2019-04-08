/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.steps.document;

import com.google.common.collect.ImmutableMap;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;

public class UpgradeSingleDecisionReportSettingsStep extends AbstractReportConfigurationUpdateStep {

  public UpgradeSingleDecisionReportSettingsStep(final Map defaultReportConfiguration,
                                                 final Map<String, Map<String, String>> decisionDefinitionXmlByKeyAndVersion) {
    super(
      SINGLE_DECISION_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      getDeepCopyMapScript() +
        "def reportData = ctx._source.data;\n" +
        "def newConfig = deepCopyMap(params.defaultConfiguration);\n" +
        "def decisionDefinitionXmlByKeyAndVersion = params.decisionDefinitionXmlByKeyAndVersion;\n" +
        // compatible field migration
        getMigrateCompatibleFieldsScript() +
        "if (reportData.decisionDefinitionKey != null\n" +
        "    && decisionDefinitionXmlByKeyAndVersion.containsKey(reportData.decisionDefinitionKey)) {\n" +
        "  def decisionDefinitionXmlForKeyByVersion = decisionDefinitionXmlByKeyAndVersion.get(\n" +
        "    reportData.decisionDefinitionKey\n" +
        "  );\n" +
        "  if (reportData.decisionDefinitionVersion != null) {\n" +
        "    if (reportData.decisionDefinitionVersion.equals(\"ALL\")) { \n" +
        "      decisionDefinitionXmlForKeyByVersion.keySet().stream().map(Integer::parseInt)\n" +
        "        .max(Integer::compareTo)\n" +
        "        .ifPresent(maxKey -> {" +
        "          newConfig.xml = decisionDefinitionXmlForKeyByVersion.get(String.valueOf(maxKey));\n" +
        "         });" +
        "    } else if (decisionDefinitionXmlForKeyByVersion.containsKey(reportData.decisionDefinitionVersion)) { \n" +
        "      newConfig.xml = decisionDefinitionXmlForKeyByVersion.get(reportData.decisionDefinitionVersion);\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "reportData.configuration = newConfig;\n",
      ImmutableMap.of(
        "defaultConfiguration", defaultReportConfiguration,
        "decisionDefinitionXmlByKeyAndVersion", decisionDefinitionXmlByKeyAndVersion
      )
    );
  }

}
