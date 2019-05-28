/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.FlowNodeExecutionState;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_OPERATIONS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASKS;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.USER_TASK_CLAIM_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;

@Slf4j
public class UpgradeFrom24To25 implements Upgrade {

  private static final String FROM_VERSION = "2.4.0";
  private static final String TO_VERSION = "2.5.0";

  private static ConfigurationService configurationService = new ConfigurationService();
  private static RestHighLevelClient client = ElasticsearchHighLevelRestClientBuilder.build(configurationService);

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
      UpgradePlan upgradePlan = buildUpgradePlan();
      upgradePlan.execute();
    } catch (Exception e) {
      log.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  public static UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(createChangeSingleDecisionReportViewStructureStep())
      .addUpgradeStep(createNewConfigFieldsToReportConfigStep(SINGLE_DECISION_REPORT_TYPE))
      .addUpgradeStep(createNewConfigFieldsToReportConfigStep(SINGLE_PROCESS_REPORT_TYPE))
      .addUpgradeStep(createNewClaimDateFieldForUserTasksSetup())
      .build();
  }

  private static UpdateDataStep createChangeSingleDecisionReportViewStructureStep() {
    String script =
      // @formatter:off
      "def reportData = ctx._source.data;\n" +
      "if (reportData.view != null) {\n" +
      "  if (reportData.view.operation != null) {\n" +
      "    if (reportData.view.operation == \"rawData\") {\n" +
      "      reportData.view.property = \"rawData\";\n" +
      "    }\n" +
      "  }\n" +
      "  reportData.view.remove('operation');\n"+
      "}\n";
    // @formatter:on
    return new UpdateDataStep(
      SINGLE_DECISION_REPORT_TYPE,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

  private static UpdateDataStep createNewConfigFieldsToReportConfigStep(String type) {
    String script =
      // @formatter:off
      "def reportData = ctx._source.data;\n" +
      "if (reportData.configuration != null) {\n" +
      "  reportData.configuration.hiddenNodes = new ArrayList();" +
      "  reportData.configuration.flowNodeExecutionState = '" + FlowNodeExecutionState.ALL + "';" +
      "}\n";
      // @formatter:on
    return new UpdateDataStep(
      type,
      QueryBuilders.matchAllQuery(),
      script
    );
  }

  private static UpdateDataStep createNewClaimDateFieldForUserTasksSetup() {

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("userTasksField", USER_TASKS)
        .put("userOperationsField", USER_OPERATIONS)
        .put("claimDateField", USER_TASK_CLAIM_DATE)
        .put("claimTypeValue", "Claim")
        .put("dateFormatPattern", OPTIMIZE_DATE_FORMAT)
        .build()
    );

    // @formatter:off
    String script = substitutor.replace(
      "if (ctx._source.${userTasksField} != null) {\n" +
          "for (def currentTask : ctx._source.${userTasksField}) {\n" +
            "if (currentTask.${userOperationsField} != null) {\n" +
              "def dateFormatter = new SimpleDateFormat(\"${dateFormatPattern}\");\n" +
              "currentTask.${claimDateField} = null;\n" +
              "def optionalFirstClaimDate = currentTask.${userOperationsField}.stream()\n" +
                ".filter(userOperation -> \"${claimTypeValue}\".equals(userOperation.type))\n" +
                ".map(userOperation -> userOperation.timestamp)\n" +
                ".min(Comparator.comparing(dateStr -> dateFormatter.parse(dateStr)));\n" +
              "optionalFirstClaimDate.ifPresent(claimDateStr -> {\n" +
                "currentTask.${claimDateField} = claimDateStr;\n" +
              "});\n" +
            "}\n" +
          "}\n" +
      "}\n"
    );
    // @formatter:on

    final BoolQueryBuilder filterQuery = QueryBuilders.boolQuery()
      .filter(QueryBuilders.scriptQuery(new Script("doc['userTasks.id'] != null")));

    return new UpdateDataStep(
      PROC_INSTANCE_TYPE,
      QueryBuilders.nestedQuery(USER_TASKS, filterQuery, ScoreMode.None),
      script
    );
  }

}
