/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.Main;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;
import static org.mockserver.model.HttpRequest.request;

public class ElasticMetadataVersionIT extends AbstractIT {

  private static final String SCHEMA_VERSION = "testVersion";
  private static final String INSTALLATION_ID = "testId";

  @Test
  public void verifyVersionAndInstallationIdIsInitialized() {
    // when
    startAndUseNewOptimizeInstance();

    // then schemaversion matches expected version and installationID is present
    final Optional<MetadataDto> metadataDto = getMetadataDto();
    final String expectedVersion = embeddedOptimizeExtension.getBean(OptimizeVersionService.class).getVersion();

    assertThat(metadataDto)
      .isPresent().get()
      .satisfies(metadata -> {
        assertThat(metadata.getSchemaVersion()).isEqualTo(expectedVersion);
        assertThat(metadata.getInstallationId()).isNotNull();
      });
  }

  @Test
  public void verifyNotStartingIfVersionDoesNotMatch() {
    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();

    MetadataDto meta = new MetadataDto(SCHEMA_VERSION, INSTALLATION_ID);
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(METADATA_INDEX_NAME, MetadataIndex.ID, meta);
    assertThatThrownBy(() -> {
      ConfigurableApplicationContext context = SpringApplication.run(Main.class);
      context.close();
    })
      .getCause().getCause()
      .hasMessageContaining("The Elasticsearch Optimize schema version [" + SCHEMA_VERSION + "]");

    elasticSearchIntegrationTestExtension.deleteAllOptimizeData();
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
    return embeddedOptimizeExtension.getBean(ElasticsearchMetadataService.class)
      .readMetadata(embeddedOptimizeExtension.getOptimizeElasticClient());
  }
}
