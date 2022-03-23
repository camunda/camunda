/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.plugin.engine.rest;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.engine.EngineConfiguration;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.util.BpmnModels.getSingleServiceTaskProcess;

public class EngineRestFilterPluginIT extends AbstractIT {

  private ConfigurationService configurationService;

  @BeforeEach
  public void setup() {
    configurationService = embeddedOptimizeExtension.getConfigurationService();
    configurationService.setPluginDirectory("target/testPluginsValid");
  }

  @Test
  public void allEventFieldDataOfImportIsAvailableWithCustomAuthentication() {
    // given
    configurationService.setEngineRestFilterPluginBasePackages(
      Collections.singletonList("org.camunda.optimize.testplugin.engine.rest")
    );
    embeddedOptimizeExtension.reloadConfiguration();
    EngineConfiguration engineConfiguration = embeddedOptimizeExtension.getDefaultEngineConfiguration();
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
    importAllEngineEntitiesFromScratch();

    engineConfiguration.getAuthentication().setEnabled(false);
    engineConfiguration.setRest(
      engineConfiguration.getRest()
        .replace("engine-it-plugin/custom-auth", "engine-rest")
    );

    // then
    allEntriesInElasticsearchHaveAllData(PROCESS_INSTANCE_MULTI_ALIAS);
  }

  private void deployAndStartSimpleServiceTask() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("aVariable", "aStringVariables");
    deployAndStartSimpleServiceTaskWithVariables(variables);
  }

  private void deployAndStartSimpleServiceTaskWithVariables(Map<String, Object> variables) {
    engineIntegrationExtension.deployAndStartProcessWithVariables(getSingleServiceTaskProcess(), variables);
  }

  private void allEntriesInElasticsearchHaveAllData(String elasticsearchType) {
    allEntriesInElasticsearchHaveAllDataWithCount(elasticsearchType, 1L);
  }

  private void allEntriesInElasticsearchHaveAllDataWithCount(String elasticsearchType, long count) {
    SearchResponse idsResp = elasticSearchIntegrationTestExtension.getSearchResponseForAllDocumentsOfIndex(
      elasticsearchType);
    assertThat(idsResp.getHits().getTotalHits().value).isEqualTo(count);
  }

}
