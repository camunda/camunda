package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexAliasForExistingIndexStep;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;


public class UpgradeFrom22To23 implements Upgrade {

  private static final String FROM_VERSION = "2.2.0";
  private static final String TO_VERSION = "2.3.0";

  private Logger logger = LoggerFactory.getLogger(getClass());
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
      UpgradePlan upgradePlan = UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION)
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getAlertType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getDashboardShareType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getDurationHeatmapTargetValueType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getImportIndexType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getLicenseType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getMetaDataType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getProcessDefinitionType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getProcessInstanceType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(configurationService.getReportShareType(), TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(COMBINED_REPORT_TYPE, TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(SINGLE_REPORT_TYPE, TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(DASHBOARD_TYPE, TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(TIMESTAMP_BASED_IMPORT_INDEX_TYPE, TO_VERSION)
        )
        .addUpgradeStep(relocateProcessPart())
        .build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private UpdateDataStep relocateProcessPart() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "ctx._source.data.parameters = [\"processPart\": ctx._source.data.processPart];" +
        "ctx._source.data.remove(\"processPart\");"
    );
  }
}
