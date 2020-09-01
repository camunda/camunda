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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ElasticMetadataVersionIT extends AbstractIT {

  private static final String SCHEMA_VERSION = "testVersion";
  private static final String INSTALLATION_ID = "testId";

  @Test
  public void verifyVersionAndInstallationIdIsInitialized() throws Exception {
    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();

    // then schemaversion matches expected version and installationID is present
    final Optional<MetadataDto> metadataDto =
      embeddedOptimizeExtension.getApplicationContext().getBean(ElasticsearchMetadataService.class)
        .readMetadata(embeddedOptimizeExtension.getOptimizeElasticClient());
    final String expectedVersion = embeddedOptimizeExtension.getApplicationContext()
      .getBean(OptimizeVersionService.class)
      .getVersion();

    assertThat(metadataDto)
      .isPresent().get()
      .satisfies(metadata -> {
        assertThat(metadata.getSchemaVersion()).isEqualTo(expectedVersion);
        assertThat(metadata.getInstallationId()).isNotNull();
      });
  }

  @Test
  public void verifyNotStartingIfMetadataIsCorrupted() throws Exception {
    // given
    String metaDataType = ElasticsearchConstants.METADATA_INDEX_NAME;
    embeddedOptimizeExtension.stopOptimize();
    MetadataDto meta = new MetadataDto(SCHEMA_VERSION, INSTALLATION_ID);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(metaDataType, "2", meta);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(metaDataType, "3", meta);

    // when
    try {
      embeddedOptimizeExtension.startOptimize();
    } catch (Exception e) {
      // expected
      elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
      embeddedOptimizeExtension.stopOptimize();
      embeddedOptimizeExtension.startOptimize();
      return;
    }

    fail("Exception expected");
  }

  @Test
  public void verifyNotStartingIfVersionDoesNotMatch() throws Exception {
    // given
    String metaDataType = ElasticsearchConstants.METADATA_INDEX_NAME;
    embeddedOptimizeExtension.stopOptimize();
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
    MetadataDto meta = new MetadataDto(SCHEMA_VERSION, INSTALLATION_ID);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(metaDataType, "2", meta);

    // when
    try {
      embeddedOptimizeExtension.startOptimize();
    } catch (Exception e) {
      // expected
      assertThat(e.getCause().getMessage())
        .contains("The Elasticsearch Optimize schema version [" + SCHEMA_VERSION + "]");
      elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
      embeddedOptimizeExtension.stopOptimize();
      embeddedOptimizeExtension.startOptimize();
      return;
    }

    fail("Exception expected");
  }
}
