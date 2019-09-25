/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.service.engine.importing.DmnModelUtility;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.es.schema.index.report.CombinedReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleDecisionReportIndex;
import org.camunda.optimize.service.es.schema.index.report.SingleProcessReportIndex;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.UpgradeProcedure;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpdateMappingIndexStep;
import org.camunda.optimize.upgrade.steps.schema.UpgradeCollectionIndexStep;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static org.camunda.optimize.service.engine.importing.DmnModelUtility.parseDmnModel;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.INPUT_VARIABLE_NAMES;
import static org.camunda.optimize.service.es.schema.index.DecisionDefinitionIndex.OUTPUT_VARIABLE_NAMES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.report.AbstractReportIndex.DATA;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_INDEX_NAME;

public class UpgradeFrom25To26 extends UpgradeProcedure {

  private static final String FROM_VERSION = "2.5.0";
  private static final String TO_VERSION = "2.6.0";

  private static final String DEFINITION_ID_TO_VAR_NAMES_PARAMETER_NAME = "definitionIdToVarNames";

  @Override
  public String getInitialVersion() {
    return FROM_VERSION;
  }

  @Override
  public String getTargetVersion() {
    return TO_VERSION;
  }

  public UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .addUpgradeDependencies(upgradeDependencies)
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(new UpdateMappingIndexStep(new SingleProcessReportIndex()))
      .addUpgradeStep(new UpdateMappingIndexStep(new SingleDecisionReportIndex()))
      .addUpgradeStep(createMultipleDefinitionVersionsForProcessReports())
      .addUpgradeStep(createMultipleDefinitionVersionsForDecisionReports())
      .addUpgradeStep(addActivePropertyToHiddenNodesConfigurationField(SINGLE_PROCESS_REPORT_INDEX_NAME))
      .addUpgradeStep(addActivePropertyToHiddenNodesConfigurationField(SINGLE_DECISION_REPORT_INDEX_NAME))
      .addUpgradeStep(new UpdateMappingIndexStep(new DecisionDefinitionIndex()))
      .addUpgradeStep(createDecisionDefinitionInputVariableNames())
      .addUpgradeStep(createDecisionDefinitionOutputVariableNames())
      .addUpgradeStep(new UpdateMappingIndexStep(new DashboardIndex()))
      .addUpgradeStep(new UpdateMappingIndexStep(new CombinedReportIndex()))
      .addUpgradeStep(new UpgradeCollectionIndexStep(prefixAwareClient, configurationService, objectMapper))
      .addUpgradeStep(createProcessInstanceIndexUpgrade())
      .build();
  }

  private UpgradeStep createMultipleDefinitionVersionsForProcessReports() {
    return createMultipleDefinitionVersionsForReports(SINGLE_PROCESS_REPORT_INDEX_NAME, "processDefinitionVersion");
  }

  private UpgradeStep createMultipleDefinitionVersionsForDecisionReports() {
    return createMultipleDefinitionVersionsForReports(SINGLE_DECISION_REPORT_INDEX_NAME, "decisionDefinitionVersion");
  }

  private UpgradeStep createMultipleDefinitionVersionsForReports(String esType, String definitionVersionField) {

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("reportDataField", DATA)
        .put("definitionVersionField", definitionVersionField)
        .put("definitionVersionsField", definitionVersionField + "s")
        .build()
    );
    String script = substitutor.replace(
      // @formatter:off
      "String definition = ctx._source.${reportDataField}.${definitionVersionField};\n" +
        "if (definition != null && !definition.isEmpty()) {\n" +
        "def list = new ArrayList();\n" +
        "list.add(definition);\n" +
        "ctx._source.${reportDataField}.${definitionVersionsField} = list; \n" +
        "ctx._source.${reportDataField}.remove(\"${definitionVersionField}\");\n" +
        "}\n"
      // @formatter:on
    );
    return new UpdateDataStep(
      esType,
      QueryBuilders.matchAllQuery(),
      script
    );

  }

  private UpgradeStep createDecisionDefinitionInputVariableNames() {
    return createDecisionDefinitionVariableNames(this::getDecisionDefinitionInputVariableNames, INPUT_VARIABLE_NAMES);
  }

  private UpgradeStep createDecisionDefinitionOutputVariableNames() {
    return createDecisionDefinitionVariableNames(this::getDecisionDefinitionOutputVariableNames, OUTPUT_VARIABLE_NAMES);
  }

  private UpgradeStep createDecisionDefinitionVariableNames(Supplier<Map<String, List<DecisionVariableNameDto>>> defIdToVarNames,
                                                            String varNameField) {
    final Map<String, List<DecisionVariableNameDto>> definitionIdToVariableNames = defIdToVarNames.get();
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("definitionIdField", DECISION_DEFINITION_ID)
        .put("variableNamesFiled", varNameField)
        .put("definitionToVarNamesParam", DEFINITION_ID_TO_VAR_NAMES_PARAMETER_NAME)
        .build()
    );

    // @formatter:off
    Map<String, Object> params =
      objectMapper.convertValue(definitionIdToVariableNames, new TypeReference<Map<String, Object>>() {});
    String script = substitutor.replace(
      "if (params.${definitionToVarNamesParam}.containsKey(ctx._source.${definitionIdField})) {\n" +
        "ctx._source.${variableNamesFiled} = " +
          "params.${definitionToVarNamesParam}.get(ctx._source.${definitionIdField});" +
      "} else {\n" +
        "ctx._source.${variableNamesFiled} = new ArrayList();" +
      "}\n"
    );
    // @formatter:on

    return new UpdateDataStep(
      DECISION_DEFINITION_INDEX_NAME,
      QueryBuilders.matchAllQuery(),
      script,
      ImmutableMap.of(DEFINITION_ID_TO_VAR_NAMES_PARAMETER_NAME, params)
    );
  }

  private Map<String, List<DecisionVariableNameDto>> getDecisionDefinitionInputVariableNames() {
    return getDecisionDefinitionVariableNames(DmnModelUtility::extractInputVariables);
  }

  private Map<String, List<DecisionVariableNameDto>> getDecisionDefinitionOutputVariableNames() {
    return getDecisionDefinitionVariableNames(DmnModelUtility::extractOutputVariables);
  }

  private Map<String, List<DecisionVariableNameDto>> getDecisionDefinitionVariableNames(BiFunction<DmnModelInstance,
    String, List<DecisionVariableNameDto>> extractVariables) {
    final Map<String, List<DecisionVariableNameDto>> result = new HashMap<>();
    try {
      final TimeValue scrollTimeOut = new TimeValue(configurationService.getElasticsearchScrollTimeout());
      final SearchRequest scrollSearchRequest = new SearchRequest(DECISION_DEFINITION_INDEX_NAME)
        .source(new SearchSourceBuilder().size(10))
        .scroll(scrollTimeOut);

      SearchResponse currentScrollResponse = prefixAwareClient.search(scrollSearchRequest, RequestOptions.DEFAULT);
      while (currentScrollResponse != null && currentScrollResponse.getHits().getHits().length != 0) {
        Arrays.stream(currentScrollResponse.getHits().getHits())
          .map(SearchHit::getSourceAsMap)
          .forEach(sourceAsMap -> {
            final String id = (String) sourceAsMap.get(DecisionDefinitionIndex.DECISION_DEFINITION_ID);
            final String key = (String) sourceAsMap.get(DecisionDefinitionIndex.DECISION_DEFINITION_KEY);
            final String value = (String) sourceAsMap.get(DecisionDefinitionIndex.DECISION_DEFINITION_XML);
            if (value != null) {
              result.put(id, extractVariables.apply(parseDmnModel(value), key));
            }
          });

        if (currentScrollResponse.getHits().getTotalHits() > result.size()) {
          SearchScrollRequest scrollRequest = new SearchScrollRequest(currentScrollResponse.getScrollId());
          scrollRequest.scroll(scrollTimeOut);
          currentScrollResponse = prefixAwareClient.scroll(scrollRequest, RequestOptions.DEFAULT);
        } else {
          currentScrollResponse = null;
        }
      }
    } catch (IOException e) {
      String errorMessage = "Could not retrieve all decision definition XMLs!";
      throw new UpgradeRuntimeException(errorMessage, e);
    }
    return result;
  }

  private UpgradeStep createProcessInstanceIndexUpgrade() {
    StringBuilder scriptBuilder = moveProcessVariablesToSingleField();
    scriptBuilder.append(getRemoveUserOperationsLogsScript());
    return new UpdateIndexStep(new ProcessInstanceIndex(), scriptBuilder.toString());
  }

  private StringBuilder moveProcessVariablesToSingleField() {
    List<String> oldVariableFields = asList(
      "stringVariables",
      "integerVariables",
      "longVariables",
      "shortVariables",
      "doubleVariables",
      "dateVariables",
      "booleanVariables"
    );
    StringBuilder scriptBuilder = new StringBuilder("def variables = new ArrayList();\n");
    for (String oldVariableField : oldVariableFields) {
      final StringSubstitutor substitutor = new StringSubstitutor(
        ImmutableMap.<String, String>builder()
          .put("oldVarField", oldVariableField)
          .build()
      );
      // @formatter:off
      String replaceVarFieldScript =
        "if (ctx._source.${oldVarField} != null) {\n" +
        "  def newEntries = " +
        "    ctx._source.${oldVarField}.stream()" +
        "      .map(var -> {" +
        "        def valueAsString = var.value == null? null : String.valueOf(var.value); " +
        "        var.value = valueAsString; " +
        "        return var;}" +
        "      ).collect(Collectors.toList());\n" +
        "  variables.addAll(newEntries); \n" +
        "}\n" +
        "ctx._source.remove(\"${oldVarField}\"); \n";
      // @formatter:on
      scriptBuilder.append(substitutor.replace(replaceVarFieldScript));
    }
    scriptBuilder.append("ctx._source." + VARIABLES + " = variables;");
    return scriptBuilder;
  }

  private String getRemoveUserOperationsLogsScript() {
    // @formatter:off
    return "if (ctx._source.userTasks != null && !ctx._source.userTasks.isEmpty()) {" +
      "ctx._source.userTasks.forEach( currUserTask -> { " +
      "  currUserTask.remove(\"userOperations\");" +
      "});" +
    "}";
    // @formatter:on
  }

  private UpdateDataStep addActivePropertyToHiddenNodesConfigurationField(String esIndex) {
    String script =
      // @formatter:off
      "def reportData = ctx._source.data;\n" +
      "if (reportData.configuration != null) {\n" +
      "  def keys = reportData.configuration.hiddenNodes;\n" +
      "  keys = keys == null? new ArrayList() : keys;\n" +
      "  reportData.configuration.hiddenNodes = new HashMap();\n" +
      "  reportData.configuration.hiddenNodes.active = keys != null && !keys.isEmpty(); \n" +
      "  reportData.configuration.hiddenNodes.keys = keys; \n" +
      "}\n";
      // @formatter:on
    return new UpdateDataStep(
      esIndex,
      QueryBuilders.matchAllQuery(),
      script
    );
  }
}
