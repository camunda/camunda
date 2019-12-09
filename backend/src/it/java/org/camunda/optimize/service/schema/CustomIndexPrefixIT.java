/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.schema;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtension;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexAliasForIndexNameAndPrefix;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexNameForAliasAndVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CustomIndexPrefixIT extends AbstractIT {
  private static final String CUSTOM_PREFIX = UUID.randomUUID().toString().substring(0, 5);

  @RegisterExtension
  @Order(2)
  public ElasticSearchIntegrationTestExtension customPrefixElasticSearchIntegrationTestExtension
    = new ElasticSearchIntegrationTestExtension(CUSTOM_PREFIX);
  @RegisterExtension
  @Order(3)
  public EngineIntegrationExtension engineIntegrationExtension = new EngineIntegrationExtension();
  @RegisterExtension
  @Order(4)
  public EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  private OptimizeElasticsearchClient prefixAwareRestHighLevelClient;

  @BeforeEach
  public void setUp() {
    prefixAwareRestHighLevelClient = embeddedOptimizeExtension.getOptimizeElasticClient();
  }

  @Test
  public void optimizeCustomPrefixIndexExistsAfterSchemaInitialization() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEsIndexPrefix(CUSTOM_PREFIX);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    initializeSchema();

    // then
    assertThat(prefixAwareRestHighLevelClient.getIndexNameService().getIndexPrefix(), is(CUSTOM_PREFIX));
    assertThat(
      embeddedOptimizeExtension.getElasticSearchSchemaManager().schemaAlreadyExists(prefixAwareRestHighLevelClient),
      is(true)
    );
  }

  @Test
  public void allTypesWithPrefixExistAfterSchemaInitialization() throws IOException {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEsIndexPrefix(CUSTOM_PREFIX);
    embeddedOptimizeExtension.reloadConfiguration();

    // when
    initializeSchema();

    // then
    final List<IndexMappingCreator> mappings = embeddedOptimizeExtension.getElasticSearchSchemaManager().getMappings();
    assertThat(mappings.size(), is(25));
    for (IndexMappingCreator mapping : mappings) {
      final String expectedAliasName = getOptimizeIndexAliasForIndexNameAndPrefix(mapping.getIndexName(), CUSTOM_PREFIX);
      final String expectedIndexName = getOptimizeIndexNameForAliasAndVersion(
        expectedAliasName, String.valueOf(mapping.getVersion())
      );

      final RestHighLevelClient highLevelClient = customPrefixElasticSearchIntegrationTestExtension.getOptimizeElasticClient().getHighLevelClient();

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

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    //when
    embeddedOptimizeExtension.getConfigurationService().setEsIndexPrefix(
      customPrefixElasticSearchIntegrationTestExtension.getOptimizeElasticClient().getIndexNameService().getIndexPrefix()
    );
    embeddedOptimizeExtension.reloadConfiguration();
    initializeSchema();

    deploySimpleProcess();

    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    customPrefixElasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME), is(1));
    assertThat(customPrefixElasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_INDEX_NAME), is(2));
  }

  private void deploySimpleProcess() {
    engineIntegrationExtension.deployAndStartProcess(createSimpleProcess());
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
    embeddedOptimizeExtension.getElasticSearchSchemaManager().initializeSchema(prefixAwareRestHighLevelClient);
  }
}
