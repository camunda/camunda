package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.es.ElasticsearchRestClientBuilder;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.schema.DeleteIndexStep;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class UpgradeFrom23To24 implements Upgrade {

  private static final String FROM_VERSION = "2.3.0";
  private static final String TO_VERSION = "2.4.0";

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
      UpgradePlanBuilder.AddUpgradeStepBuilder upgradePlanBuilder = UpgradePlanBuilder.createUpgradePlan()
        .fromVersion(FROM_VERSION)
        .toVersion(TO_VERSION);

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
}
