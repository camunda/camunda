/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.DistributedByType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.custom_buckets.CustomBucketDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.AssigneeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.CandidateGroupDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.FlowNodeDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.NoneDistributedByDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.distributed.UserTaskDistributedByDto;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.service.es.schema.index.AlertIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.events.EventProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import org.camunda.optimize.upgrade.util.MappingMetadataUtil;
import org.elasticsearch.index.query.QueryBuilders;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.ReportConstants.DATE_UNIT_AUTOMATIC;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

public class UpgradeFrom31To32 extends UpgradeProcedure {
  public static final String FROM_VERSION = "3.1.0";
  public static final String TO_VERSION = "3.2.0";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  @Override
  public UpgradePlan buildUpgradePlan() {
    final List<EventProcessInstanceIndex> eventProcessInstanceIndices =
      MappingMetadataUtil.retrieveAllEventProcessInstanceIndices(esClient);
    final UpgradePlanBuilder.AddUpgradeStepBuilder upgradeBuilder = UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(migrateAlertEmailRecipientsField())
      .addUpgradeStep(addTableColumnSettingsToReportConfiguration(SINGLE_PROCESS_REPORT_INDEX_NAME))
      .addUpgradeStep(addTableColumnSettingsToReportConfiguration(SINGLE_DECISION_REPORT_INDEX_NAME))
      .addUpgradeStep(migrateDistributedByField(new SingleProcessReportIndex()))
      .addUpgradeStep(migrateDistributedByField(new SingleDecisionReportIndex()))
      // the order matters here
      // update process instance index mappings so new fields are available
      .addUpgradeStep(new UpdateMappingIndexStep(new ProcessInstanceIndex()))
      .addUpgradeSteps(eventProcessInstanceIndices.stream().map(UpdateMappingIndexStep::new).collect(toList()))
      // for being initialized
      .addUpgradeStep(createInitializeProcessInstanceIncidentsFieldStep(PROCESS_INSTANCE_INDEX_NAME))
      .addUpgradeSteps(
        eventProcessInstanceIndices
          .stream()
          .map(ProcessInstanceIndex::getIndexName)
          .map(this::createInitializeProcessInstanceIncidentsFieldStep)
          .collect(toList())
      )
      .addUpgradeStep(setDefaultDistributeByCustomBucketValues());
    return upgradeBuilder.build();
  }

  private UpgradeStep createInitializeProcessInstanceIncidentsFieldStep(final String instanceIndexName) {
    return new UpdateDataStep(
      instanceIndexName,
      matchAllQuery(),
      "ctx._source.incidents = new ArrayList();"
    );
  }

  private UpgradeStep migrateAlertEmailRecipientsField() {
    //@formatter:off
    final String script =
      "def emails = new ArrayList();\n" +
      "if (ctx._source.email != null) {\n" +
        "emails.add(ctx._source.email);\n" +
      "}\n" +
      "ctx._source.emails = emails;\n" +
      "ctx._source.remove(\"email\");"
      ;
    //@formatter:on
    return new UpdateIndexStep(
      new AlertIndex(),
      script
    );
  }

  private UpgradeStep addTableColumnSettingsToReportConfiguration(final String reportIndexName) {
    final String defaultTableColumnDto = "newTableColumnDto";

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("oldExcludeColumnsField", "excludedColumns")
        .put("oldIncludeColumnsField", "includedColumns")
        .put("tableColumnField", SingleReportConfigurationDto.Fields.tableColumns.name())
        .put("tableColumnExcludeField", TableColumnDto.Fields.excludedColumns.name())
        .put("tableColumnIncludeField", TableColumnDto.Fields.includedColumns.name())
        .put("defaultTableColumnDto", defaultTableColumnDto)
        .build()
    );
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put(defaultTableColumnDto, new TableColumnDto())
      .build();

    //@formatter:off
    final String script = substitutor.replace(
      "ctx._source.data.configuration.${tableColumnField} = params.${defaultTableColumnDto};\n" +
        "if(ctx._source.data.configuration.${oldExcludeColumnsField} != null) {\n" +
          "ctx._source.data.configuration.${tableColumnField}.${tableColumnExcludeField} = ctx._source.data.configuration.${oldExcludeColumnsField};\n" +
        "} else {\n" +
          "ctx._source.data.configuration.${tableColumnField}.${tableColumnExcludeField} = new ArrayList();\n" +
        "}\n" +
        "if(ctx._source.data.configuration.${oldIncludeColumnsField} != null) {\n" +
          "ctx._source.data.configuration.${tableColumnField}.${tableColumnIncludeField} = ctx._source.data.configuration.${oldIncludeColumnsField};\n" +
        "} else {\n" +
        "ctx._source.data.configuration.${tableColumnField}.${tableColumnIncludeField} = new ArrayList();\n" +
        "}\n" +
        "ctx._source.data.configuration.remove(\"${oldExcludeColumnsField}\");\n" +
        "ctx._source.data.configuration.remove(\"${oldIncludeColumnsField}\");\n"
    );
    //@formatter:on

    return new UpdateDataStep(
      reportIndexName,
      matchAllQuery(),
      script,
      params
    );
  }

  private UpgradeStep setDefaultDistributeByCustomBucketValues() {
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put("defaultDistributeByCustomBucket", CustomBucketDto.builder().build())
      .put("defaultDistributeByDateVariableUnit", DATE_UNIT_AUTOMATIC)
      .build();


    //@formatter:off
    final String script =
      "def configuration = ctx._source.data.configuration;" +
        "if (configuration.distributeByCustomBucket == null) {" +
          "configuration.distributeByCustomBucket = params.defaultDistributeByCustomBucket;" +
        "}" +
        "if (configuration.distributeByDateVariableUnit == null) {" +
          "configuration.distributeByDateVariableUnit = params.defaultDistributeByDateVariableUnit;" +
        "}" +
        "\n";
    //@formatter:on

    return new UpdateDataStep(
      SINGLE_PROCESS_REPORT_INDEX_NAME,
      QueryBuilders.matchAllQuery(),
      script,
      params
    );
  }

  private UpgradeStep migrateDistributedByField(final IndexMappingCreator indexMappingCreator) {
    final String distributeByNoneType = DistributedByType.NONE.toString();
    final String distributeByUserTaskType = DistributedByType.USER_TASK.toString();
    final String distributeByFlowNodeType = DistributedByType.FLOW_NODE.toString();
    final String distributeByAssigneeType = DistributedByType.ASSIGNEE.toString();
    final String distributeByCandidateGroupType = DistributedByType.CANDIDATE_GROUP.toString();

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("oldDistributeByField", "distributedBy")
        .put("newDistributeByField", SingleReportConfigurationDto.Fields.distributedBy.name())
        .put("distributeByNone", distributeByNoneType)
        .put("distributedByUserTask", distributeByUserTaskType)
        .put("distributedByFlowNode", distributeByFlowNodeType)
        .put("distributedByAssignee", distributeByAssigneeType)
        .put("distributeByCandidateGroup", distributeByCandidateGroupType)
        .build()
    );
    final Map<String, Object> params = ImmutableMap.<String, Object>builder()
      .put(distributeByNoneType, new NoneDistributedByDto())
      .put(distributeByUserTaskType, new UserTaskDistributedByDto())
      .put(distributeByFlowNodeType, new FlowNodeDistributedByDto())
      .put(distributeByAssigneeType, new AssigneeDistributedByDto())
      .put(distributeByCandidateGroupType, new CandidateGroupDistributedByDto())
      .build();

    //@formatter:off
    final String script = substitutor.replace(
        "def configuration = ctx._source.data.configuration;" +
        "if(configuration.${oldDistributeByField} == null" +
          "|| \"${distributeByNone}\".equals(configuration.${oldDistributeByField})) {\n" +
          "configuration.${newDistributeByField} = params.${distributeByNone};\n" +
        "} else if(\"${distributedByUserTask}\".equals(configuration.${oldDistributeByField})){\n" +
          "configuration.${newDistributeByField} = params.${distributedByUserTask};\n" +
        "} else if(\"${distributedByFlowNode}\".equals(configuration.${oldDistributeByField})){\n" +
          "configuration.${newDistributeByField} = params.${distributedByFlowNode};\n" +
        "} else if(\"${distributedByAssignee}\".equals(configuration.${oldDistributeByField})){\n" +
          "configuration.${newDistributeByField} = params.${distributedByAssignee};\n" +
        "} else if(\"${distributeByCandidateGroup}\".equals(configuration.${oldDistributeByField})){\n" +
          "configuration.${newDistributeByField} = params.${distributeByCandidateGroup};\n" +
        "}\n"
    );
    //@formatter:on

    return new UpdateIndexStep(
      indexMappingCreator,
      script,
      params
    );
  }

}
