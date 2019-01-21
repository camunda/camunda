package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.document.UpgradeCombinedReportSettingsFrom23Step;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleDecisionReportSettingsFrom23Step;
import org.camunda.optimize.upgrade.steps.document.UpgradeSingleProcessReportSettingsFrom23Step;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.util.ReportUtil.buildSingleReportIdToVisualizationAndViewMap;
import static org.camunda.optimize.upgrade.util.SchemaUpgradeUtil.getDefaultReportConfigurationAsMap;


public class UpgradeFrom23To24 implements Upgrade {

  private static final Logger logger = LoggerFactory.getLogger(UpgradeFrom23To24.class);

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

  private ConfigurationService configurationService = new ConfigurationService();

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
      UpgradePlanBuilder.AddUpgradeStepBuilder upgradePlanBuilder = UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(
          DECISION_INSTANCE_TYPE,
          DecisionInstanceType.VERSION,
          getNewDecisionInstanceMapping()
        ))
        .addUpgradeStep(migrateConfigurationInCombinedProcessReport())
        .addUpgradeStep(migrateConfigurationInSimpleProcessReport())
        .addUpgradeStep(migrateConfigurationInSimpleDecisionReport())
        .addUpgradeStep(buildMatchedRules());

      if (isTargetValueIndexPresent()) {
        upgradePlanBuilder
          .addUpgradeStep(removeTargetValueIndexStep());
      }

      UpgradePlan upgradePlan = upgradePlanBuilder
        .build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private UpdateDataStep migrateConfigurationInSimpleProcessReport() {
    return new UpgradeSingleProcessReportSettingsFrom23Step(getDefaultReportConfigurationAsMap());
  }

  private UpdateDataStep migrateConfigurationInSimpleDecisionReport() {
    return new UpgradeSingleDecisionReportSettingsFrom23Step(getDefaultReportConfigurationAsMap());
  }

  private UpdateDataStep migrateConfigurationInCombinedProcessReport() {
    return new UpgradeCombinedReportSettingsFrom23Step(
      getDefaultReportConfigurationAsMap(), buildSingleReportIdToVisualizationAndViewMap(configurationService)
    );
  }

  private boolean isTargetValueIndexPresent() {
    RestHighLevelClient client = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

    GetIndexRequest request = new GetIndexRequest();
    request.indices("optimize-duration-target-value");

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

  private String getNewDecisionInstanceMapping() {
    String pathToMapping = "upgrade/main/UpgradeFrom23To24/decision-instance-mapping.json";
    return SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping);
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
