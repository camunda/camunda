/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class UpgradeFrom31To32 extends UpgradeProcedure {
  public static final String FROM_VERSION = "3.1.0";
  public static final String TO_VERSION = "3.2.0";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  @Override
  public UpgradePlan buildUpgradePlan() {
    final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder = UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(migrateAlertEmailRecipientsField())
      .addUpgradeStep(addTableColumnSettingsToReportConfiguration(SINGLE_PROCESS_REPORT_INDEX_NAME))
      .addUpgradeStep(addTableColumnSettingsToReportConfiguration(SINGLE_DECISION_REPORT_INDEX_NAME));
    return upgradeBuilder.build();
  }

  private UpgradeStep migrateAlertEmailRecipientsField() {
    //@formatter:off
    final String script =
      "def emails = new ArrayList();\n" +
      "if (ctx._source.email != null) {\n" +
        "emails.add(ctx._source.email);\n" +
      "}\n" +
      "ctx._source.emails = emails;\n" +
      "ctx._source.remove(\"email\");"
      ;
    //@formatter:on
    return new UpdateIndexStep(
      new AlertIndex(),
      script
    );
  }

  private UpgradeStep addTableColumnSettingsToReportConfiguration(final String reportIndexName) {
    final String defaultTableColumnDto = "newTableColumnDto";

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("oldExcludeColumnsField", "excludedColumns")
        .put("oldIncludeColumnsField", "includedColumns")
        .put("tableColumnField", SingleReportConfigurationDto.Fields.tableColumns.name())
        .put("tableColumnExcludeField", TableColumnDto.Fields.excludedColumns.name())
        .put("tableColumnIncludeField", TableColumnDto.Fields.includedColumns.name())
        .put("defaultTableColumnDto", defaultTableColumnDto)
        .build()
    );
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put(defaultTableColumnDto, new TableColumnDto())
      .build();

    //@formatter:off
    final String script = substitutor.replace(
      "ctx._source.data.configuration.${tableColumnField} = params.${defaultTableColumnDto};\n" +
        "if(ctx._source.data.configuration.${oldExcludeColumnsField} != null) {\n" +
          "ctx._source.data.configuration.${tableColumnField}.${tableColumnExcludeField} = ctx._source.data.configuration.${oldExcludeColumnsField};\n" +
        "} else {\n" +
          "ctx._source.data.configuration.${tableColumnField}.${tableColumnExcludeField} = new ArrayList();\n" +
        "}\n" +
        "if(ctx._source.data.configuration.${oldIncludeColumnsField} != null) {\n" +
          "ctx._source.data.configuration.${tableColumnField}.${tableColumnIncludeField} = ctx._source.data.configuration.${oldIncludeColumnsField};\n" +
        "} else {\n" +
        "ctx._source.data.configuration.${tableColumnField}.${tableColumnIncludeField} = new ArrayList();\n" +
        "}\n" +
        "ctx._source.data.configuration.remove(\"${oldExcludeColumnsField}\");\n" +
        "ctx._source.data.configuration.remove(\"${oldIncludeColumnsField}\");\n"
    );
    //@formatter:on

    return new UpdateDataStep(
      reportIndexName,
      QueryBuilders.matchAllQuery(),
      script,
      params
    );
  }

}
