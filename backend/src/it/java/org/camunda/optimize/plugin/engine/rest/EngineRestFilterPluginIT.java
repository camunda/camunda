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
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
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
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void allEventFieldDataOfImportIsAvailableWithCustomAuthentication() throws Exception {
    // given
    configurationService.setEngineRestFilterPluginBasePackages(
      Collections.singletonList("org.camunda.optimize.testplugin.engine.rest")
    );
    embeddedOptimizeRule.reloadConfiguration();
    EngineConfiguration engineConfiguration = embeddedOptimizeRule.getConfigurationService()
      .getConfiguredEngines()
      .get("1");
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
    allEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_INDEX_NAME);
  }

  private void deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private void deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    // @formatter: off
    final BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess").name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    // @formatter: on
    engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType) throws IOException {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchType, 1L);
  }

  private void allEntriesInElasticsearchHaveAllDataWithCount(String elasticsearchType, long count) throws IOException {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfType(elasticsearchType);
    assertThat(idsResp.getHits().getTotalHits(), is(count));
  }

  private SearchResponse getSearchResponseForAllDocumentsOfType(String elasticsearchType) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(elasticsearchType)
      .types(elasticsearchType)
      .source(searchSourceBuilder);

    return elasticSearchRule.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
  }

}
