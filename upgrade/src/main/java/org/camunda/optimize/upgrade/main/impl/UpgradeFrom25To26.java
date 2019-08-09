/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.main.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRole;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.service.engine.importing.DmnModelUtility;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.upgrade.es.ElasticsearchHighLevelRestClientBuilder;
import org.camunda.optimize.upgrade.exception.UpgradeRuntimeException;
import org.camunda.optimize.upgrade.main.Upgrade;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanBuilder;
import org.camunda.optimize.upgrade.steps.UpgradeStep;
import org.camunda.optimize.upgrade.steps.document.UpdateDataStep;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.camunda.optimize.service.engine.importing.DmnModelUtility.parseDmnModel;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_ID;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.INPUT_VARIABLE_NAMES;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.OUTPUT_VARIABLE_NAMES;
import static org.camunda.optimize.service.es.schema.type.report.AbstractReportType.DATA;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.COLLECTION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_DECISION_REPORT_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.SINGLE_PROCESS_REPORT_TYPE;

public class UpgradeFrom25To26 implements Upgrade {

  private static final String FROM_VERSION = "2.5.0";
  private static final String TO_VERSION = "2.6.0";

  private Logger logger = LoggerFactory.getLogger(getClass());


  private ConfigurationService configurationService = new ConfigurationService();
  private OptimizeIndexNameService indexNameService = new OptimizeIndexNameService(configurationService);
  private OptimizeElasticsearchClient client = new OptimizeElasticsearchClient(
      ElasticsearchHighLevelRestClientBuilder.build(configurationService),
      indexNameService
    );
  private ObjectMapper objectMapper = new ObjectMapper();

  private static final String DEFINITION_ID_TO_VAR_NAMES_PARAMETER_NAME = "definitionIdToVarNames";


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
      logger.error("Error while executing upgrade", e);
      System.exit(2);
    }
  }

  public UpgradePlan buildUpgradePlan() {
    return UpgradePlanBuilder.createUpgradePlan()
      .fromVersion(FROM_VERSION)
      .toVersion(TO_VERSION)
      .addUpgradeStep(createMultipleDefinitionVersionsForProcessReports())
      .addUpgradeStep(createMultipleDefinitionVersionsForDecisionReports())
      .addUpgradeStep(createDecisionDefinitionInputVariableNames())
      .addUpgradeStep(createDecisionDefinitionOutputVariableNames())
      .addUpgradeStep(createDefaultManagerRoleForCollections())
      .build();
  }

  private UpgradeStep createMultipleDefinitionVersionsForProcessReports() {
    return createMultipleDefinitionVersionsForReports(SINGLE_PROCESS_REPORT_TYPE, "processDefinitionVersion");
  }

  private UpgradeStep createMultipleDefinitionVersionsForDecisionReports() {
    return createMultipleDefinitionVersionsForReports(SINGLE_DECISION_REPORT_TYPE, "decisionDefinitionVersion");
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
      "String definition = ctx._source.${reportDataField}.${definitionVersionField};\n" +
      "if (definition != null && !definition.isEmpty()) {\n" +
        "def list = new ArrayList();\n" +
        "list.add(definition);\n" +
        "ctx._source.${reportDataField}.${definitionVersionsField} = list; \n" +
        "ctx._source.${reportDataField}.remove(\"${definitionVersionField}\");\n" +
      "}\n"
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

    Map<String, Object> params =
      objectMapper.convertValue(definitionIdToVariableNames, new TypeReference<Map<String, Object>>() {});
    // @formatter:off
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
      DECISION_DEFINITION_TYPE,
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

  private Map<String, List<DecisionVariableNameDto>> getDecisionDefinitionVariableNames(BiFunction<DmnModelInstance, String, List<DecisionVariableNameDto>> extractVariables) {
    final Map<String, List<DecisionVariableNameDto>> result = new HashMap<>();
    try {
      final TimeValue scrollTimeOut = new TimeValue(configurationService.getElasticsearchScrollTimeout());
      final SearchRequest scrollSearchRequest = new SearchRequest(DECISION_DEFINITION_TYPE)
        .source(new SearchSourceBuilder().size(10))
        .scroll(scrollTimeOut);

      SearchResponse currentScrollResponse = client.search(scrollSearchRequest, RequestOptions.DEFAULT);
      while (currentScrollResponse != null && currentScrollResponse.getHits().getHits().length != 0) {
        Arrays.stream(currentScrollResponse.getHits().getHits())
          .map(SearchHit::getSourceAsMap)
          .forEach(sourceAsMap -> {
            final String id = (String) sourceAsMap.get(DecisionDefinitionType.DECISION_DEFINITION_ID);
            final String key = (String) sourceAsMap.get(DecisionDefinitionType.DECISION_DEFINITION_KEY);
            final String value = (String) sourceAsMap.get(DecisionDefinitionType.DECISION_DEFINITION_XML);
            if (value != null) {
              result.put(id, extractVariables.apply(parseDmnModel(value), key));
            }
          });

        if (currentScrollResponse.getHits().getTotalHits() > result.size()) {
          SearchScrollRequest scrollRequest = new SearchScrollRequest(currentScrollResponse.getScrollId());
          scrollRequest.scroll(scrollTimeOut);
          currentScrollResponse = client.scroll(scrollRequest, RequestOptions.DEFAULT);
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

  private UpgradeStep createDefaultManagerRoleForCollections() {

    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder()
        .put("collectionOwnerField", BaseCollectionDefinitionDto.Fields.owner.name())
        .put("collectionDataField", BaseCollectionDefinitionDto.Fields.data.name())
        .put("collectionDataRolesField", CollectionDataDto.Fields.roles.name())
        .put("managerRole", CollectionRole.MANAGER.name())
        .build()
    );
    String script = substitutor.replace(
      // @formatter:off
      "String owner = ctx._source.${collectionOwnerField};\n" +
        "def identity = [ \"id\": owner, \"type\": \"USER\" ];\n" +
        "def roleEntry = [ \"id\": \"USER:\" + owner, \"identity\": identity, \"role\": \"${managerRole}\"];\n" +
        "ctx._source.${collectionDataField}.${collectionDataRolesField} = new ArrayList();\n" +
        "ctx._source.${collectionDataField}.${collectionDataRolesField}.add(roleEntry);\n"
      // @formatter:on
    );
    return new UpdateDataStep(
      COLLECTION_TYPE,
      QueryBuilders.matchAllQuery(),
      script
    );

  }
}
