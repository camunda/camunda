package org.camunda.optimize.upgrade.main.impl;

import com.jayway.jsonpath.JsonPath;
import org.apache.http.util.EntityUtils;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchRestClientBuilder;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.CreateIndexAliasForExistingIndexStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COMBINED_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_TYPE;


public class UpgradeFrom22To23 implements Upgrade {

  private static final String FROM_VERSION = "2.2.0";
  private static final String TO_VERSION = "2.3.0";
  public static final String SINGLE_REPORT = "single-report";

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
          new CreateIndexAliasForExistingIndexStep(
            COMBINED_REPORT_TYPE, COMBINED_REPORT_TYPE, TO_VERSION, getNewCombinedReportMapping()
          )
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(
            SINGLE_REPORT, SINGLE_PROCESS_REPORT_TYPE, TO_VERSION, getNewSingleProcessReportMapping()
          )
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(DASHBOARD_TYPE, TO_VERSION)
        )
        .addUpgradeStep(
          new CreateIndexAliasForExistingIndexStep(TIMESTAMP_BASED_IMPORT_INDEX_TYPE, TO_VERSION)
        )
        .addUpgradeStep(relocateProcessPart())
        .addUpgradeStep(migrateSingleReportType())
        .addUpgradeStep(migrateCombinedReportType())
        .addUpgradeStep(buildFillVisualizationFieldOfCombinedReportWithDataStep())
        .build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private String getNewCombinedReportMapping() {
    String pathToMapping = "upgrade/main/UpgradeFrom22To23/add_visualization_to_combined_report_index_mapping.json";
    return SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping);
  }

  private String getNewSingleProcessReportMapping() {
    String pathToMapping = "upgrade/main/UpgradeFrom22To23/single-process-report-mapping.json";
    return SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping);
  }

  private UpgradeStep buildFillVisualizationFieldOfCombinedReportWithDataStep() {
    // @formatter:off
    String updateScript =
      "if(ctx._source.data != null) {" +
        "if(ctx._source.data.reportIds != null && !ctx._source.data.reportIds.isEmpty()) {" +
          "String firstReportId = ctx._source.data.reportIds.get(0);" +
          "String visualizationOfCombinedReport = params[firstReportId];" +
          "ctx._source.data.visualization = visualizationOfCombinedReport;" +
        "} else {" +
          "ctx._source.data.visualization = null;" +
        "}" +
      "}";
    // @formatter:on
    return new UpdateDataStep(
      COMBINED_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      updateScript,
      buildSingleReportIdToVisualizationMap()
    );
  }

  private Map<String, String> buildSingleReportIdToVisualizationMap() {
    RestClient build = ElasticsearchRestClientBuilder.build(configurationService);
    try {
      Response response =
        build.performRequest("GET", getOptimizeIndexAliasForType(SINGLE_REPORT) + "/_search");
      String json = EntityUtils.toString(response.getEntity());
      List<Map> hits = JsonPath.read(json, "$.hits.hits.*");
      Map<String, String> reportIdToVisualization = new HashMap<>();
      for (Map hit : hits) {
        Map source = (Map) hit.get("_source");
        String id = (String) source.get("id");
        Map data = (Map) source.get("data");
        String visualization = (String) data.get("visualization");
        reportIdToVisualization.put(id, visualization);
      }
      return reportIdToVisualization;
    } catch (IOException e) {
      String errorMessage =
        "Could not retrieve all single reports to update combined report visualization field!";
      throw new UpgradeRuntimeException(errorMessage, e);
    }
  }

  private UpdateDataStep relocateProcessPart() {
    return new UpdateDataStep(
      SINGLE_PROCESS_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      "ctx._source.data.parameters = [\"processPart\": ctx._source.data.processPart];" +
        "ctx._source.data.remove(\"processPart\");"
    );
  }

  private UpdateDataStep migrateSingleReportType() {
    return new UpdateDataStep(
      SINGLE_PROCESS_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      "ctx._source.reportType = \"process\";" +
        "ctx._source.combined = false;"
    );
  }

  private UpdateDataStep migrateCombinedReportType() {
    return new UpdateDataStep(
      COMBINED_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      "ctx._source.reportType = \"process\";" +
        "ctx._source.combined = true;"
    );
  }
}
