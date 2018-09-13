package org.camunda.optimize.upgrade.main.impl;

import org.camunda.optimize.service.es.schema.type.CombinedReportType;
import org.camunda.optimize.service.es.schema.type.SingleReportType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.AddFieldStep;
import org.camunda.optimize.upgrade.steps.CreateIndexStep;
import org.camunda.optimize.upgrade.steps.RenameIndexStep;
import org.camunda.optimize.upgrade.steps.UpdateDataStep;
import org.camunda.optimize.upgrade.util.SchemaUpgradeUtil;
import org.elasticsearch.index.query.QueryBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.SingleReportType.REPORT_TYPE;


public class UpgradeFrom21To22 implements Upgrade {

  private static final String FROM_VERSION = "2.1.0";
  private static final String TO_VERSION = "2.2.0";

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
        .addUpgradeStep(addStateFieldToProcessInstanceType())
        .addUpgradeStep(addReportTypeFieldToReportType())
        .addUpgradeStep(renameReportIndexToSimpleReport())
        .addUpgradeStep(addActivityStartDateFieldToProcessInstanceType())
        .addUpgradeStep(addActivityEndDateFieldToProcessInstanceType())
        .addUpgradeStep(AdjustGroupByStep())
        .addUpgradeStep(AdjustViewStep())
        .build();
      upgradePlan.execute();
    } catch (Exception e) {
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  private AddFieldStep addActivityStartDateFieldToProcessInstanceType() {
    Map<String, String> dateType = new HashMap<>();
    dateType.put("type", "date");
    dateType.put("format", configurationService.getOptimizeDateFormat());
    return new AddFieldStep(
      "process-instance",
      "$.mappings.process-instance.properties.events.properties",
      "startDate",
      dateType,
      "ctx._source.startDate = null"
    );
  }

  private AddFieldStep addActivityEndDateFieldToProcessInstanceType() {
    Map<String, String> value = new HashMap<>();
    value.put("type", "date");
    value.put("format", configurationService.getOptimizeDateFormat());
    return new AddFieldStep(
      "process-instance",
      "$.mappings.process-instance.properties.events.properties",
      "endDate",
      value,
      "ctx._source.endDate = null"
    );
  }

  private RenameIndexStep renameReportIndexToSimpleReport() {
    return new RenameIndexStep("report", SingleReportType.SINGLE_REPORT_TYPE);
  }

  private CreateIndexStep createCombinedReportIndex() {
    String pathToMapping = "upgrade/main/UpgradeFrom21To22/new_combined_report_index_mapping.json";
    return new CreateIndexStep(
      CombinedReportType.COMBINED_REPORT_TYPE,
      SchemaUpgradeUtil.readClasspathFileAsString(pathToMapping)
    );
  }

  private UpdateDataStep AdjustGroupByStep() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "if (ctx._source.data.groupBy.type == 'flowNode') {" +
        "ctx._source.data.groupBy.type = 'flowNodes';" +
      "}"
    );
  }

  private UpdateDataStep AdjustViewStep() {
    return new UpdateDataStep(
      "single-report",
      QueryBuilders.matchAllQuery(),
      "if (ctx._source.data.view.operation == 'rawData') { " +
        "ctx._source.data.view.property = null;" +
        "ctx._source.data.view.entity = null;" +
      "}"
    );
  }

  private AddFieldStep addReportTypeFieldToReportType() {
    return new AddFieldStep(
      "report",
      "$.mappings.report.properties",
      REPORT_TYPE,
      Collections.singletonMap("type", "keyword"),
      "ctx._source.reportType = 'single'"
    );
  }

  private AddFieldStep addStateFieldToProcessInstanceType() {
    return new AddFieldStep(
      "process-instance",
      "$.mappings.process-instance.properties",
      "state",
      Collections.singletonMap("type", "keyword"),
      "ctx._source.state = null"
    );
  }

}
