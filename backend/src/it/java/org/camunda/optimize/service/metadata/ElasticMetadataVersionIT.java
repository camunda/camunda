/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.metadata;

import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class ElasticMetadataVersionIT {

  @RegisterExtension
  @Order(1)
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule = new ElasticSearchIntegrationTestExtensionRule();
  @RegisterExtension
  @Order(2)
  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  @RegisterExtension
  @Order(3)
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();

  @Test
  public void verifyVersionIsInitialized() throws Exception {
    embeddedOptimizeExtensionRule.stopOptimize();
    embeddedOptimizeExtensionRule.startOptimize();
    String version = embeddedOptimizeExtensionRule.getApplicationContext().getBean(ElasticsearchMetadataService.class)
      .readMetadata(embeddedOptimizeExtensionRule.getOptimizeElasticClient()).get().getSchemaVersion();
    String expected = embeddedOptimizeExtensionRule.getApplicationContext().getBean(OptimizeVersionService.class).getVersion();
    assertThat(version, is(expected));
  }

  @Test
  public void verifyNotStartingIfMetadataIsCorrupted() throws Exception {
    String metaDataType = ElasticsearchConstants.METADATA_INDEX_NAME;
    embeddedOptimizeExtensionRule.stopOptimize();
    MetadataDto meta = new MetadataDto("TEST");
    elasticSearchIntegrationTestExtensionRule.addEntryToElasticsearch(metaDataType, "2", meta);
    elasticSearchIntegrationTestExtensionRule.addEntryToElasticsearch(metaDataType, "3", meta);
    try {
      embeddedOptimizeExtensionRule.startOptimize();
    } catch (Exception e) {
      //expected
      elasticSearchIntegrationTestExtensionRule.deleteAllOptimizeData();
      embeddedOptimizeExtensionRule.stopOptimize();
      embeddedOptimizeExtensionRule.startOptimize();
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void verifyNotStartingIfVersionDoesNotMatch () throws Exception {
    String metaDataType = ElasticsearchConstants.METADATA_INDEX_NAME;
    embeddedOptimizeExtensionRule.stopOptimize();
    elasticSearchIntegrationTestExtensionRule.deleteAllOptimizeData();
    MetadataDto meta = new MetadataDto("TEST");
    elasticSearchIntegrationTestExtensionRule.addEntryToElasticsearch(metaDataType, "2", meta);
    try {
      embeddedOptimizeExtensionRule.startOptimize();
    } catch (Exception e) {
      //expected
      assertThat(e.getCause().getMessage(), containsString("The Elasticsearch Optimize schema version [TEST]"));
      elasticSearchIntegrationTestExtensionRule.deleteAllOptimizeData();
      embeddedOptimizeExtensionRule.stopOptimize();
      embeddedOptimizeExtensionRule.startOptimize();
      return;
    }

    fail("Exception expected");
  }
}
