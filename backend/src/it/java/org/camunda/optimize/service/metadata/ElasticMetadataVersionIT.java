/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class ElasticMetadataVersionIT extends AbstractIT {

  private static final String SCHEMA_VERSION = "testVersion";
  private static final String INSTALLATION_ID = "testId";

  @Test
  public void verifyVersionAndInstallationIdIsInitialized() throws Exception {
    // when
    embeddedOptimizeExtension.stopOptimize();
    embeddedOptimizeExtension.startOptimize();

    // then schemaversion matches expected version and installationID is present
    final Optional<MetadataDto> metadataDto = getMetadataDto();
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
  public void verifyStillStartingEvenIfMetadataIndexMissing() throws Exception {
    // given
    embeddedOptimizeExtension.stopOptimize();
    elasticSearchIntegrationTestExtension.deleteIndexOfMapping(new MetadataIndex());

    // when
    embeddedOptimizeExtension.startOptimize();

    // then the metadata index & doc is recreated
    final Optional<MetadataDto> metadataDto = getMetadataDto();
    final String expectedVersion = embeddedOptimizeExtension.getApplicationContext()
      .getBean(OptimizeVersionService.class)
      .getVersion();

    assertThat(metadataDto)
      .isPresent().get()
      .satisfies(metadata -> {
        assertThat(metadata.getSchemaVersion()).isEqualTo(expectedVersion);
      });
  }

  @Test
  public void verifyStillStartingEvenIfExpectedMetadataDocIsMissing() throws Exception {
    // given
    embeddedOptimizeExtension.stopOptimize();
    elasticSearchIntegrationTestExtension.deleteAllDocsInIndex(new MetadataIndex());

    // when
    embeddedOptimizeExtension.startOptimize();

    // then the metadata doc is created
    final Optional<MetadataDto> metadataDto = getMetadataDto();
    final String expectedVersion = embeddedOptimizeExtension.getApplicationContext()
      .getBean(OptimizeVersionService.class)
      .getVersion();

    assertThat(metadataDto)
      .isPresent().get()
      .satisfies(metadata -> {
        assertThat(metadata.getSchemaVersion()).isEqualTo(expectedVersion);
      });
  }

  @Test
  public void verifyNotStartingIfVersionDoesNotMatch() throws Exception {
    // given
    embeddedOptimizeExtension.stopOptimize();
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
    MetadataDto meta = new MetadataDto(SCHEMA_VERSION, INSTALLATION_ID);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(METADATA_INDEX_NAME, MetadataIndex.ID, meta);

    // when
    try {
      assertThatThrownBy(() -> embeddedOptimizeExtension.startOptimize())
        .getCause()
        .hasMessageContaining("The Elasticsearch Optimize schema version [" + SCHEMA_VERSION + "]");
    } finally {
      embeddedOptimizeExtension.stopOptimize();
      elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
      embeddedOptimizeExtension.startOptimize();
    }
  }

  @Test
  public void verifyGetMetadataFailsOnClientException() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    esMockServer
      .when(request().withPath("/.*-" + METADATA_INDEX_NAME + ".*/_doc/" + MetadataIndex.ID))
      .respond(HttpResponse.response().withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code()));

    assertThatThrownBy(this::getMetadataDto)
      .hasMessage("Failed retrieving the Optimize metadata document from elasticsearch!");
  }

  private Optional<MetadataDto> getMetadataDto() {
    return embeddedOptimizeExtension.getApplicationContext()
      .getBean(ElasticsearchMetadataService.class)
      .readMetadata(embeddedOptimizeExtension.getOptimizeElasticClient());
  }
}
