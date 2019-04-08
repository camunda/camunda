/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.engine.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.plugin.EngineRestFilterProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.EngineConfiguration;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class EngineRestFilterPluginIT {

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  
  private ConfigurationService configurationService;
  private EngineRestFilterProvider pluginProvider;

  @Rule
  public RuleChain chain = RuleChain.outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  
  @Before
  public void setup() {
    configurationService = embeddedOptimizeRule.getConfigurationService();
    pluginProvider = embeddedOptimizeRule.getApplicationContext().getBean(EngineRestFilterProvider.class);
    pluginProvider.resetPlugins();
  }

  @After
  public void resetBasePackage() {
    configurationService.setEngineRestFilterPluginBasePackages(new ArrayList<>());
    pluginProvider.resetPlugins();
  }
  
  @Test
  public void allEventFieldDataOfImportIsAvailableWithCustomAuthentication() throws Exception {
    // given
    configurationService.setEngineRestFilterPluginBasePackages(Collections.singletonList("org.camunda.optimize.plugin.engine.rest"));
    EngineConfiguration engineConfiguration = embeddedOptimizeRule.getConfigurationService().getConfiguredEngines().get("1");
    engineConfiguration.getAuthentication().setEnabled(true);
    engineConfiguration.getAuthentication().setPassword("kermit");
    engineConfiguration.getAuthentication().setUser("kermit");
    engineConfiguration.setRest(
        engineConfiguration.getRest()
        .replace("engine-rest", "engine-rest-custom")
    );
    engineRule.addUser("kermit", "kermit");
    engineRule.grantAllAuthorizations("kermit");
    embeddedOptimizeRule.reloadConfiguration();
    
    deployAndStartSimpleServiceTask();

    // when
    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    elasticSearchRule.refreshAllOptimizeIndices();

    engineConfiguration.getAuthentication().setEnabled(false);
    engineConfiguration.setRest(
        engineConfiguration.getRest()
        .replace("engine-rest-custom", "engine-rest")
    );

    // then
    allEntriesInElasticsearchHaveAllData(PROC_INSTANCE_TYPE);
  }

  private void deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private void deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess").name("aProcessName").startEvent().serviceTask().camundaExpression("${true}")
        .endEvent().done();
    engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType) throws IOException {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchType, 1L);
  }

  private void allEntriesInElasticsearchHaveAllDataWithCount(String elasticsearchType, long count) throws IOException {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticsearchType);

    assertThat(idsResp.getHits().getTotalHits(), is(count));
    for (SearchHit searchHit : idsResp.getHits().getHits()) {
      for (Entry searchHitField : searchHit.getSourceAsMap().entrySet()) {
        String errorMessage = "Something went wrong during fetching of field: " + searchHitField.getKey() + ". Should actually have a value!";
        assertThat(errorMessage, searchHitField.getValue(), is(notNullValue()));
        if (searchHitField.getValue() instanceof String) {
          String value = (String) searchHitField.getValue();
          assertThat(errorMessage, value.isEmpty(), is(false));
        }
      }
    }
  }

  private SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(getOptimizeIndexAliasForType(elasticsearchType))
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return elasticSearchRule.getEsClient().search(searchRequest, RequestOptions.DEFAULT);
  }

}
