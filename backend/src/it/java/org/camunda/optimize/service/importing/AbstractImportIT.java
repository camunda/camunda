/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractImportIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(engineIntegrationExtensionRule.getEngineName());

  protected void allEntriesInElasticsearchHaveAllData(String elasticsearchIndex, final Set<String> excludedFields) throws
                                                                                                                  IOException {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, 1L, excludedFields);
  }


  protected void allEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                               final long count) throws IOException {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchIndex, count, Collections.emptySet());
  }

  protected void allEntriesInElasticsearchHaveAllDataWithCount(final String elasticsearchIndex,
                                                               final long count,
                                                               final Set<String> nullValueFields)
    throws IOException {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(elasticsearchIndex);

    assertThat(idsResp.getHits().getTotalHits(), is(count));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      assertAllFieldsSet(nullValueFields, searchHit);
    }
  }

  protected void assertAllFieldsSet(final Set<String> nullValueFields, final SearchHit searchHit) {
    for (Map.Entry<String, Object> searchHitField : searchHit.getSourceAsMap().entrySet()) {
      if (nullValueFields.contains(searchHitField.getKey())) {
        assertThat(searchHitField.getValue(), is(nullValue()));
      } else {
        String errorMessage = "Something went wrong during fetching of field: " + searchHitField.getKey() +
          ". Should actually have a value!";
        assertThat(errorMessage, searchHitField.getValue(), is(notNullValue()));
        if (searchHitField.getValue() instanceof String) {
          String value = (String) searchHitField.getValue();
          assertThat(errorMessage, value.isEmpty(), is(false));
        }
      }
    }
  }

  protected SearchResponse getSearchResponseForAllDocumentsOfIndex(String elasticsearchIndex) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(elasticsearchIndex)
      .types(elasticsearchIndex)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtensionRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    return deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = createSimpleProcessDefinition();
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  protected BpmnModelInstance createSimpleProcessDefinition() {
    // @formatter:off
    return Bpmn.createExecutableProcess("aProcess")
      .camundaVersionTag("aVersionTag")
      .name("aProcessName")
      .startEvent()
      .serviceTask()
        .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter:on
  }

  protected ProcessInstanceEngineDto deployAndStartUserTaskProcess() {
    // @formatter:off
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .userTask()
      .endEvent()
      .done();
    // @formatter:on
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariable");
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(processModel, variables);
  }
}
