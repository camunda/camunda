/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.metadata;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class ElasticMetadataVersionIT extends AbstractIT {

  @Test
  public void verifyVersionIsInitialized() throws Exception {
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();
    String version = embeddedOptimizeExtension.getApplicationContext().getBean(ElasticsearchMetadataService.class)
      .readMetadata(embeddedOptimizeExtension.getOptimizeElasticClient()).get().getSchemaVersion();
    String expected = embeddedOptimizeExtension.getApplicationContext().getBean(OptimizeVersionService.class).getVersion();
    assertThat(version, is(expected));
  }

  @Test
  public void verifyNotStartingIfMetadataIsCorrupted() throws Exception {
    String metaDataType = ElasticsearchConstants.METADATA_INDEX_NAME;
    embeddedOptimizeExtension.stopOptimize();
    MetadataDto meta = new MetadataDto("TEST");
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(metaDataType, "2", meta);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(metaDataType, "3", meta);
    try {
      embeddedOptimizeExtension.startOptimize();
    } catch (Exception e) {
      //expected
      elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
      embeddedOptimizeExtension.stopOptimize();
      embeddedOptimizeExtension.startOptimize();
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void verifyNotStartingIfVersionDoesNotMatch () throws Exception {
    String metaDataType = ElasticsearchConstants.METADATA_INDEX_NAME;
    embeddedOptimizeExtension.stopOptimize();
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
    MetadataDto meta = new MetadataDto("TEST");
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(metaDataType, "2", meta);
    try {
      embeddedOptimizeExtension.startOptimize();
    } catch (Exception e) {
      //expected
      assertThat(e.getCause().getMessage(), containsString("The Elasticsearch Optimize schema version [TEST]"));
      elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
      embeddedOptimizeExtension.stopOptimize();
      embeddedOptimizeExtension.startOptimize();
      return;
    }

    fail("Exception expected");
  }
}
