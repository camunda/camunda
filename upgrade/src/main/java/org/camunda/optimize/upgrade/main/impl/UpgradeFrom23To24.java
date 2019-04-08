/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.camunda.optimize.service.es.schema.type.report.CombinedReportType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.document.UpgradeCombinedReportSettingsStep;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleDecisionReportSettingsStep;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleProcessReportSettingsStep;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.util.ReportUtil.buildDecisionDefinitionXmlByKeyAndVersionMap;
import static org.camunda.optimize.upgrade.util.ReportUtil.buildSingleReportIdToVisualizationAndViewMap;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.createMappingStringFromMapping;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultCombinedReportConfigurationAsMap;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultSingleReportConfigurationAsMap;


public class UpgradeFrom23To24 implements Upgrade {

  private static final Logger logger = LoggerFactory.getLogger(UpgradeFrom23To24.class);

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private ConfigurationService configurationService = new ConfigurationService();
  private RestHighLevelClient client = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  @Override
  public void performUpgrade() {
    try {
      final UpgradePlanBuilder.AddUpgradeStepBuilder upgradePlanBuilder = UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION);

      if (isTargetValueIndexPresent()) {
        upgradePlanBuilder
          .addUpgradeStep(removeTargetValueIndexStep());
      }

      if (isDecisionInstanceIndexPresent()) {
        upgradePlanBuilder
          .addUpgradeStep(new UpdateIndexStep(
            DECISION_INSTANCE_TYPE,
            DecisionInstanceType.VERSION,
            createMappingStringFromMapping(new DecisionInstanceType())
          ))
          .addUpgradeStep(buildMatchedRules())
          .addUpgradeStep(migrateConfigurationInSimpleDecisionReport());
      }

      if (isCombinedReportIndexPresent()) {
        final CombinedReportType combinedReportType = new CombinedReportType();
        // we set dynamic mappings to false to keep deprecated properties
        // that are required by the migrateConfigurationInCombinedProcessReport step
        // it will be reset to the default ('strict') the next call of schemaManager.initializeSchema(client);
        // which happens in the UpgradePlan#execute
        combinedReportType.setDynamicMappingsValue("false");
        upgradePlanBuilder
          .addUpgradeStep(new UpdateIndexStep(
            COMBINED_REPORT_TYPE,
            CombinedReportType.VERSION,
            createMappingStringFromMapping(combinedReportType)
          ));
      }

      if (isProcessInstanceIndexPresent()) {
        upgradePlanBuilder
          .addUpgradeStep(new UpdateIndexStep(
            PROC_INSTANCE_TYPE,
            ProcessInstanceType.VERSION,
            createMappingStringFromMapping(new ProcessInstanceType())
          ));
      }

      upgradePlanBuilder
        .addUpgradeStep(migrateConfigurationInCombinedProcessReport())
        .addUpgradeStep(migrateConfigurationInSimpleProcessReport());

      final UpgradePlan upgradePlan = upgradePlanBuilder.build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private UpdateDataStep migrateConfigurationInSimpleProcessReport() {
    return new UpgradeSingleProcessReportSettingsStep(getDefaultSingleReportConfigurationAsMap());
  }

  private UpdateDataStep migrateConfigurationInSimpleDecisionReport() {
    return new UpgradeSingleDecisionReportSettingsStep(
      getDefaultSingleReportConfigurationAsMap(),
      buildDecisionDefinitionXmlByKeyAndVersionMap(configurationService)
    );
  }

  private UpdateDataStep migrateConfigurationInCombinedProcessReport() {
    return new UpgradeCombinedReportSettingsStep(
      getDefaultCombinedReportConfigurationAsMap(), buildSingleReportIdToVisualizationAndViewMap(configurationService)
    );
  }

  private boolean isCombinedReportIndexPresent() {
    return checkIfIndexExists("optimize-combined-report_v1");
  }

  private boolean isDecisionInstanceIndexPresent() {
    return checkIfIndexExists("optimize-decision-instance_v1");
  }

  private boolean isProcessInstanceIndexPresent() {
    return checkIfIndexExists("optimize-process-instance_v1");
  }

  private boolean isTargetValueIndexPresent() {
    return checkIfIndexExists("optimize-duration-target-value");
  }

  private boolean checkIfIndexExists(final String indexName) {
    GetIndexRequest request = new GetIndexRequest();
    request.indices(indexName);

    boolean exists;

    try {
      exists = client.indices().exists(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Could not execute a request against Elasticsearch");
      exists = false;
    }
    return exists;
  }

  private DeleteIndexStep removeTargetValueIndexStep() {
    return new DeleteIndexStep(null, "duration-target-value");
  }

  private UpgradeStep buildMatchedRules() {
    // @formatter:off
    String updateScript =
      "ctx._source.matchedRules = new HashSet();" +
      "for (output in ctx._source.outputs) {" +
      "  ctx._source.matchedRules.add(output.ruleId);" +
      "}";
    // @formatter:on
    return new UpdateDataStep(DECISION_INSTANCE_TYPE, QueryBuilders.matchAllQuery(), updateScript);
  }
}
