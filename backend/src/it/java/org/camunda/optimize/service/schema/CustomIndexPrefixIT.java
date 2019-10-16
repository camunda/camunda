/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.schema;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexAliasForTypeAndPrefix;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CustomIndexPrefixIT {
  private static final String CUSTOM_PREFIX = UUID.randomUUID().toString().substring(0, 5);

  public static ElasticSearchIntegrationTestExtensionRule defaultElasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  public static ElasticSearchIntegrationTestExtensionRule customPrefixElasticSearchIntegrationTestExtensionRule
    = new ElasticSearchIntegrationTestExtensionRule(CUSTOM_PREFIX);
  public static EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  public static EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(defaultElasticSearchIntegrationTestExtensionRule)
    .around(customPrefixElasticSearchIntegrationTestExtensionRule)
    .around(engineIntegrationExtensionRule)
    .around(embeddedOptimizeExtensionRule);

  @Before
  public void setUp() {
    prefixAwareRestHighLevelClient = embeddedOptimizeExtensionRule.getOptimizeElasticClient();
  }

  @Test
  public void optimizeCustomPrefixIndexExistsAfterSchemaInitialization() {
    // given
    embeddedOptimizeExtensionRule.getConfigurationService().setEsIndexPrefix(CUSTOM_PREFIX);
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // when
    initializeSchema();

    // then
    assertThat(prefixAwareRestHighLevelClient.getIndexNameService().getIndexPrefix(), is(CUSTOM_PREFIX));
    assertThat(
      embeddedOptimizeExtensionRule.getElasticSearchSchemaManager().schemaAlreadyExists(prefixAwareRestHighLevelClient),
      is(true)
    );
  }

  @Test
  public void allTypesWithPrefixExistAfterSchemaInitialization() throws IOException {
    // given
    embeddedOptimizeExtensionRule.getConfigurationService().setEsIndexPrefix(CUSTOM_PREFIX);
    embeddedOptimizeExtensionRule.reloadConfiguration();

    // when
    initializeSchema();

    // then
    final List<IndexMappingCreator> mappings = embeddedOptimizeExtensionRule.getElasticSearchSchemaManager().getMappings();
    assertThat(mappings.size(), is(18));
    for (IndexMappingCreator mapping : mappings) {
      final String expectedAliasName = getOptimizeIndexAliasForTypeAndPrefix(mapping.getIndexName(), CUSTOM_PREFIX);
      final String expectedIndexName = getOptimizeIndexNameForAliasAndVersion(
        expectedAliasName, String.valueOf(mapping.getVersion())
      );

      final RestHighLevelClient highLevelClient = customPrefixElasticSearchIntegrationTestExtensionRule.getOptimizeElasticClient().getHighLevelClient();

      assertThat(
        "Custom prefix alias exists for type " + mapping.getIndexName(),
        highLevelClient.indices().exists(new GetIndexRequest().indices(expectedAliasName), RequestOptions.DEFAULT),
        is(true)
      );

      assertThat(
        "Custom prefix index exists for type " + mapping.getIndexName(),
        highLevelClient.indices().exists(new GetIndexRequest().indices(expectedIndexName), RequestOptions.DEFAULT),
        is(true)
      );
    }
  }

  @Test
  public void optimizeIndexDataIsIsolated() {
    // given
    deploySimpleProcess();

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    defaultElasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeExtensionRule.getConfigurationService().setEsIndexPrefix(
      customPrefixElasticSearchIntegrationTestExtensionRule.getOptimizeElasticClient().getIndexNameService().getIndexPrefix()
    );
    embeddedOptimizeExtensionRule.reloadConfiguration();
    initializeSchema();

    deploySimpleProcess();

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    customPrefixElasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    assertThat(defaultElasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME), is(1));
    assertThat(customPrefixElasticSearchIntegrationTestExtensionRule.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME), is(2));
  }

  private void deploySimpleProcess() {
    engineIntegrationExtensionRule.deployAndStartProcess(createSimpleProcess());
  }

  private BpmnModelInstance createSimpleProcess() {
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

  private void initializeSchema() {
    embeddedOptimizeExtensionRule.getElasticSearchSchemaManager().initializeSchema(prefixAwareRestHighLevelClient);
  }
}
