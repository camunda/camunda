/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.schema;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.TypeMappingCreator;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
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
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROC_INSTANCE_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CustomIndexPrefixIT {
  private static final String CUSTOM_PREFIX = UUID.randomUUID().toString().substring(0, 5);

  public static ElasticSearchIntegrationTestRule defaultElasticSearchRule = new ElasticSearchIntegrationTestRule();
  public static ElasticSearchIntegrationTestRule customPrefixElasticSearchRule = new ElasticSearchIntegrationTestRule(
    CUSTOM_PREFIX
  );
  public static EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public static EngineIntegrationRule engineRule = new EngineIntegrationRule();

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(defaultElasticSearchRule)
    .around(customPrefixElasticSearchRule)
    .around(engineRule)
    .around(embeddedOptimizeRule);

  @Before
  public void setUp() {
    prefixAwareRestHighLevelClient = embeddedOptimizeRule.getOptimizeElasticClient();
  }

  @Test
  public void optimizeCustomPrefixIndexExistsAfterSchemaInitialization() {
    // given
    embeddedOptimizeRule.getConfigurationService().setEsIndexPrefix(CUSTOM_PREFIX);
    embeddedOptimizeRule.reloadConfiguration();

    // when
    initializeSchema();

    // then
    assertThat(prefixAwareRestHighLevelClient.getIndexNameService().getIndexPrefix(), is(CUSTOM_PREFIX));
    assertThat(
      embeddedOptimizeRule.getElasticSearchSchemaManager().schemaAlreadyExists(prefixAwareRestHighLevelClient),
      is(true)
    );
  }

  @Test
  public void allTypesWithPrefixExistAfterSchemaInitialization() throws IOException {
    // given
    embeddedOptimizeRule.getConfigurationService().setEsIndexPrefix(CUSTOM_PREFIX);
    embeddedOptimizeRule.reloadConfiguration();

    // when
    initializeSchema();

    // then
    final List<TypeMappingCreator> mappings = embeddedOptimizeRule.getElasticSearchSchemaManager().getMappings();
    assertThat(mappings.size(), is(18));
    for (TypeMappingCreator mapping : mappings) {
      final String expectedAliasName = getOptimizeIndexAliasForTypeAndPrefix(mapping.getType(), CUSTOM_PREFIX);
      final String expectedIndexName = getOptimizeIndexNameForAliasAndVersion(
        expectedAliasName, String.valueOf(mapping.getVersion())
      );

      final RestHighLevelClient highLevelClient = customPrefixElasticSearchRule.getOptimizeElasticClient().getHighLevelClient();

      assertThat(
        "Custom prefix alias exists for type " + mapping.getType(),
        highLevelClient.indices().exists(new GetIndexRequest().indices(expectedAliasName), RequestOptions.DEFAULT),
        is(true)
      );

      assertThat(
        "Custom prefix index exists for type " + mapping.getType(),
        highLevelClient.indices().exists(new GetIndexRequest().indices(expectedIndexName), RequestOptions.DEFAULT),
        is(true)
      );
    }
  }

  @Test
  public void optimizeIndexDataIsIsolated() {
    // given
    deploySimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    defaultElasticSearchRule.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeRule.getConfigurationService().setEsIndexPrefix(CUSTOM_PREFIX);
    embeddedOptimizeRule.reloadConfiguration();
    initializeSchema();

    deploySimpleProcess();

    embeddedOptimizeRule.importAllEngineEntitiesFromScratch();
    customPrefixElasticSearchRule.refreshAllOptimizeIndices();

    assertThat(defaultElasticSearchRule.getDocumentCountOf(PROC_INSTANCE_TYPE), is(1));
    assertThat(customPrefixElasticSearchRule.getDocumentCountOf(PROC_INSTANCE_TYPE), is(2));
  }

  private void deploySimpleProcess() {
    engineRule.deployAndStartProcess(createSimpleProcess());
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
    embeddedOptimizeRule.getElasticSearchSchemaManager().initializeSchema(prefixAwareRestHighLevelClient);
  }
}
