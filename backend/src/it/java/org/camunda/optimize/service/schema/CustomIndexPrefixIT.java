/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.schema;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.IndexMappingCreator;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.util.BpmnModels;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexAliasForIndexNameAndPrefix;
import static org.camunda.optimize.service.es.schema.OptimizeIndexNameService.getOptimizeIndexOrTemplateNameForAliasAndVersion;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

public class CustomIndexPrefixIT extends AbstractIT {
  private static final String CUSTOM_PREFIX = UUID.randomUUID().toString().substring(0, 5);

  @RegisterExtension
  @Order(2)
  public ElasticSearchIntegrationTestExtension customPrefixElasticSearchIntegrationTestExtension
    = new ElasticSearchIntegrationTestExtension(CUSTOM_PREFIX);

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
    assertThat(prefixAwareRestHighLevelClient.getIndexNameService().getIndexPrefix()).isEqualTo(CUSTOM_PREFIX);
    assertThat(embeddedOptimizeExtension.getElasticSearchSchemaManager().schemaExists(prefixAwareRestHighLevelClient))
      .isTrue();
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
    assertThat(mappings).hasSize(29);
    for (IndexMappingCreator mapping : mappings) {
      final String expectedAliasName = getOptimizeIndexAliasForIndexNameAndPrefix(
        mapping.getIndexName(),
        CUSTOM_PREFIX
      );
      final String expectedIndexName = getOptimizeIndexOrTemplateNameForAliasAndVersion(
        expectedAliasName, String.valueOf(mapping.getVersion()))
        + mapping.getIndexNameInitialSuffix();

      final OptimizeElasticsearchClient esClient =
        customPrefixElasticSearchIntegrationTestExtension.getOptimizeElasticClient();
      final RestHighLevelClient highLevelClient = esClient.getHighLevelClient();

      assertThat(highLevelClient.indices().exists(new GetIndexRequest(expectedAliasName), esClient.requestOptions()))
        .isTrue();
      assertThat(highLevelClient.indices().exists(new GetIndexRequest(expectedIndexName), esClient.requestOptions()))
        .isTrue();
    }
  }

  @Test
  public void optimizeIndexDataIsIsolated() {
    // given
    deploySimpleProcess();
    importAllEngineEntitiesFromScratch();

    // when
    embeddedOptimizeExtension.getConfigurationService().setEsIndexPrefix(
      customPrefixElasticSearchIntegrationTestExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix()
    );
    embeddedOptimizeExtension.reloadConfiguration();
    initializeSchema();

    deploySimpleProcess();

    importAllEngineEntitiesFromScratch();
    customPrefixElasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS)).isEqualTo(1);
    assertThat(
      customPrefixElasticSearchIntegrationTestExtension.getDocumentCountOf(PROCESS_INSTANCE_MULTI_ALIAS)
    ).isEqualTo(2);
  }

  private void deploySimpleProcess() {
    engineIntegrationExtension.deployAndStartProcess(createSimpleProcess());
  }

  private BpmnModelInstance createSimpleProcess() {
    return BpmnModels.getSingleServiceTaskProcess("aProcess");
  }

  private void initializeSchema() {
    embeddedOptimizeExtension.getElasticSearchSchemaManager().initializeSchema(prefixAwareRestHighLevelClient);
  }
}
