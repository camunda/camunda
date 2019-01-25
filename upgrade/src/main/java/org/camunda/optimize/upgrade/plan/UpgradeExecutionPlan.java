package org.camunda.optimize.upgrade.plan;

import org.camunda.optimize.jetty.util.LoggingConfigurationReader;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.service.ValidationService;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_TYPE_SCHEMA_VERSION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class UpgradeExecutionPlan implements UpgradePlan {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private RestHighLevelClient client;
  private ConfigurationService configurationService;
  private List<UpgradeStep> upgradeSteps = new ArrayList<>();
  private String fromVersion;
  private String toVersion;
  private ValidationService validationService;

  public UpgradeExecutionPlan() {
    new LoggingConfigurationReader().defineLogbackLoggingConfiguration();

    configurationService = new ConfigurationService();
    validationService = new ValidationService(configurationService);
    validationService.validateConfiguration();

    client = ElasticsearchHighLevelRestClientBuilder.build(configurationService);
  }

  @Override
  public void execute() {
    validationService.validateVersions(client, fromVersion, toVersion);
    ESIndexAdjuster esIndexAdjuster = new ESIndexAdjuster(client, configurationService);
    int currentStepCount = 1;
    for (UpgradeStep step : upgradeSteps) {
      logger.info(
        "Starting step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );
      step.execute(esIndexAdjuster);
      logger.info(
        "Successfully finished step {}/{}: {}",
        currentStepCount,
        upgradeSteps.size(),
        step.getClass().getSimpleName()
      );
      currentStepCount++;
    }
    updateOptimizeVersion(esIndexAdjuster);
  }

  private void updateOptimizeVersion(ESIndexAdjuster ESIndexAdjuster) {
    logger.info("Updating Elasticsearch data structure version tag from {} to {}.", fromVersion, toVersion);
    ESIndexAdjuster.updateDataByTypeName(
      ElasticsearchConstants.METADATA_TYPE,
      termQuery(METADATA_TYPE_SCHEMA_VERSION, fromVersion),
      String.format("ctx._source.schemaVersion = \"%s\"", toVersion),
      null
    );
  }

  public void addUpgradeStep(UpgradeStep upgradeStep) {
    this.upgradeSteps.add(upgradeStep);
  }

  public void setFromVersion(String fromVersion) {
    this.fromVersion = fromVersion;
  }

  public void setToVersion(String toVersion) {
    this.toVersion = toVersion;
  }

}
