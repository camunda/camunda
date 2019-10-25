/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.service.es.schema.index.CollectionIndex;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DashboardShareIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.camunda.optimize.service.es.schema.index.LicenseIndex;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.ReportShareIndex;
import org.camunda.optimize.service.es.schema.index.TenantIndex;
import org.camunda.optimize.service.es.schema.index.TerminatedUserSessionIndex;
import org.camunda.optimize.service.es.schema.index.index.ImportIndexIndex;
import org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.service.util.EsHelper;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.DeleteDataStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.DATA;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IMPORT_INDEX_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class UpgradeFrom26To27 extends UpgradeProcedure {

  private static final String FROM_VERSION = "2.6.0";
  private static final String TO_VERSION = "2.7.0";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  public UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DecisionInstanceIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new CollectionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new TimestampBasedImportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new AlertIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DecisionDefinitionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new ProcessInstanceIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new MetadataIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new ImportIndexIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new TerminatedUserSessionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new ProcessDefinitionIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new SingleDecisionReportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new SingleProcessReportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new CombinedReportIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new ReportShareIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new LicenseIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new TenantIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DashboardIndex()))
      .addUpgradeStep(updateTypeForDocumentsInIndex(new DashboardShareIndex()))
      .addUpgradeStep(moveParameterFieldsForDecisionReport())
      .addUpgradeStep(moveParameterFieldsForProcessReport())
      .addUpgradeStep(removeDocumentsForImportIndexWithId("decisionDefinitionXmlImportIndex"))
      .addUpgradeStep(removeDocumentsForImportIndexWithId("processDefinitionXmlImportIndex"))
      .build();
  }

  private UpgradeStep moveParameterFieldsForDecisionReport() {
    ImmutableMap<String, String> fieldsToSubstitute = ImmutableMap.<String, String>builder()
      .put("sortingField", "sorting")
      .build();
    return moveParameterFieldsToReportConfiguration(
      SINGLE_DECISION_REPORT_INDEX_NAME,
      fieldsToSubstitute
    );
  }

  private UpgradeStep moveParameterFieldsForProcessReport() {
    ImmutableMap<String, String> fieldsToSubstitute = ImmutableMap.<String, String>builder()
      .put("sortingField", "sorting")
      .put("processPartField", "processPart")
      .build();
    return moveParameterFieldsToReportConfiguration(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      fieldsToSubstitute
    );
  }

  private UpgradeStep moveParameterFieldsToReportConfiguration(String reportType,
                                                               ImmutableMap<String, String> fieldSubstitutions) {

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("reportDataField", DATA)
        .put("configurationField", "configuration")
        .put("parameterField", "parameters")
        .putAll(fieldSubstitutions)
        .build()
    );
    String substitutionScriptPart = fieldSubstitutions.keySet().stream()
      .map(key -> String.format(
        // @formatter:off
        "ctx._source.${reportDataField}.${configurationField}.${%s} = parameters.${%s};\n",
        // @formatter:on
        key,
        key
      ))
      .collect(Collectors.joining());
    String script = substitutor.replace(
      // @formatter:off
      "def parameters = ctx._source.${reportDataField}.${parameterField};\n" +
        "if (parameters != null) {\n" +
          "if (ctx._source.${reportDataField}.${configurationField} != null) {\n" +
          substitutionScriptPart +
          "}\n" +
        "ctx._source.${reportDataField}.remove(\"${parameterField}\");\n" +
        "}\n"
      // @formatter:on
    );
    return new UpdateDataStep(
      reportType,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

  private UpgradeStep removeDocumentsForImportIndexWithId(String idPrefix) {
    final String[] documentIdsToRemove = configurationService.getConfiguredEngines()
      .keySet()
      .stream()
      .map(engineAlias -> EsHelper.constructKey(idPrefix, engineAlias))
      .toArray(String[]::new);
    return new DeleteDataStep(
      IMPORT_INDEX_INDEX_NAME,
      QueryBuilders.idsQuery().addIds(documentIdsToRemove)
    );
  }

  private UpgradeStep updateTypeForDocumentsInIndex(StrictIndexMappingCreator index) {
    return new UpdateIndexStep(index, null);
  }

}
