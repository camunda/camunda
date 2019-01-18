package org.camunda.optimize.upgrade.main.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.service.es.schema.type.DecisionInstanceType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
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
import java.util.Collections;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;


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
        .toVersion(TO_VERSION)
        .addUpgradeStep(new UpdateIndexStep(
          DECISION_INSTANCE_TYPE,
          DecisionInstanceType.VERSION,
          getNewDecisionInstanceMapping()
        ))
        .addUpgradeStep(resetConfigurationInSimpleProcessReport())
        .addUpgradeStep(resetConfigurationInSimpleDecisionReport())
        .addUpgradeStep(resetConfigurationInCombinedProcessReport())
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

  private UpdateDataStep resetConfigurationInSimpleProcessReport() {
    Map reportConfiguration = getReportConfigurationObject();
    return new UpdateDataStep(
      SINGLE_PROCESS_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      "ctx._source.data.configuration = params.defaultConfiguration;",
      Collections.singletonMap("defaultConfiguration", reportConfiguration)

    );
  }

  private UpdateDataStep resetConfigurationInSimpleDecisionReport() {
    Map reportConfiguration = getReportConfigurationObject();
    return new UpdateDataStep(
      SINGLE_DECISION_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      "ctx._source.data.configuration = params.defaultConfiguration;",
      Collections.singletonMap("defaultConfiguration", reportConfiguration)

    );
  }

  private UpdateDataStep resetConfigurationInCombinedProcessReport() {
    Map reportConfiguration = getReportConfigurationObject();
    return new UpdateDataStep(
      COMBINED_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      "List reportColors = ctx._source.data.configuration.color;" +
        "ctx._source.data.configuration = params.defaultConfiguration;"+
        "ctx._source.data.configuration.reportColors = reportColors;",
      Collections.singletonMap("defaultConfiguration", reportConfiguration)

    );
  }

  private Map getReportConfigurationObject() {
    String pathToMapping = "upgrade/main/UpgradeFrom23To24/default-report-configuration.json";
    String reportConfigurationStructureAsJson = SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping);
    ObjectMapper objectMapper = new ObjectMapper();
    Map reportConfigurationAsMap;
    try {
      reportConfigurationAsMap = objectMapper.readValue(reportConfigurationStructureAsJson, Map.class);
    } catch (IOException e) {
      throw new UpgradeRuntimeException("Could not deserialize default report configuration structure as json!");
    }
    return reportConfigurationAsMap;
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
