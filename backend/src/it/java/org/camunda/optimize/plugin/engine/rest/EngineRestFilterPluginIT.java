/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.plugin.engine.rest;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.plugin.EngineRestFilterProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EngineRestFilterPluginIT extends AbstractIT {

  private ConfigurationService configurationService;
  private EngineRestFilterProvider pluginProvider;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    pluginProvider = embeddedOptimizeExtension.getApplicationContext().getBean(EngineRestFilterProvider.class);
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void allEventFieldDataOfImportIsAvailableWithCustomAuthentication() throws Exception {
    // given
    configurationService.setEngineRestFilterPluginBasePackages(
      Collections.singletonList("org.camunda.optimize.testplugin.engine.rest")
    );
    embeddedOptimizeExtension.reloadConfiguration();
    EngineConfiguration engineConfiguration = embeddedOptimizeExtension.getConfigurationService()
      .getConfiguredEngines()
      .get("1");
    engineConfiguration.getAuthentication().setEnabled(true);
    engineConfiguration.getAuthentication().setPassword("kermit");
    engineConfiguration.getAuthentication().setUser("kermit");
    engineConfiguration.setRest(
      engineConfiguration.getRest()
        .replace("engine-rest", "engine-it-plugin/custom-auth")
    );
    engineIntegrationExtension.addUser("kermit", "kermit");
    engineIntegrationExtension.grantAllAuthorizations("kermit");
    embeddedOptimizeExtension.reloadConfiguration();

    deployAndStartSimpleServiceTask();

    // when
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    engineConfiguration.getAuthentication().setEnabled(false);
    engineConfiguration.setRest(
      engineConfiguration.getRest()
        .replace("engine-it-plugin/custom-auth", "engine-rest")
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
    engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType) throws IOException {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchType, 1L);
  }

  private void allEntriesInElasticsearchHaveAllDataWithCount(String elasticsearchType, long count) throws IOException {
    SearchResponse idsResp = getSearchResponseForAllDocumentsOfIndex(elasticsearchType);
    assertThat(idsResp.getHits().getTotalHits(), is(count));
  }

  private SearchResponse getSearchResponseForAllDocumentsOfIndex(String indexName) throws IOException {
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(matchAllQuery())
      .size(100);

    SearchRequest searchRequest = new SearchRequest()
      .indices(indexName)
      .source(searchSourceBuilder);

    return elasticSearchIntegrationTestExtension.getOptimizeElasticClient().search(searchRequest, RequestOptions.DEFAULT);
  }

}
