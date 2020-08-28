/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry;

import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractIT;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.dto.optimize.query.telemetry.DatabaseDto;
import org.camunda.optimize.dto.optimize.query.telemetry.InternalsDto;
import org.camunda.optimize.dto.optimize.query.telemetry.ProductDto;
import org.camunda.optimize.dto.optimize.query.telemetry.TelemetryDataDto;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.es.schema.index.MetadataIndex;
import org.camunda.optimize.service.license.LicenseManager;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.util.Optional;

import static javax.ws.rs.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.telemetry.TelemetryDataService.INFORMATION_UNAVAILABLE_STRING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.METADATA_INDEX_NAME;
import static org.elasticsearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.mockserver.model.HttpRequest.request;

public class TelemetryDataServiceIT extends AbstractIT {
  @Test
  public void retrieveTelemetryData() {
    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getApplicationContext().getBean(TelemetryDataService.class).getTelemetryData();

    // then
    final TelemetryDataDto expectedTelemetry = getExpectedTelemetry(
      elasticSearchIntegrationTestExtension.getEsVersion(),
      getMetadata().map(MetadataDto::getInstallationId).orElse(INFORMATION_UNAVAILABLE_STRING),
      getLicense()
    );

    assertThat(telemetryData).isEqualTo(expectedTelemetry);
  }

  @Test
  public void retrieveTelemetryData_missingMetadata_doesNotFail() {
    // given
    removeMetadata();

    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getApplicationContext().getBean(TelemetryDataService.class).getTelemetryData();

    // then
    final TelemetryDataDto expectedTelemetry = getExpectedTelemetry(
      elasticSearchIntegrationTestExtension.getEsVersion(),
      INFORMATION_UNAVAILABLE_STRING,
      getLicense()
    );
    assertThat(telemetryData).isEqualTo(expectedTelemetry);
  }

  @Test
  public void retrieveTelemetryData_elasticsearchDown_doesNotFail() {
    // given
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest requestMatcher = request()
      .withPath("/")
      .withMethod(GET);
    esMockServer
      .when(requestMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));

    // when
    final TelemetryDataDto telemetryData =
      embeddedOptimizeExtension.getApplicationContext().getBean(TelemetryDataService.class).getTelemetryData();

    // then
    final TelemetryDataDto expectedTelemetry = getExpectedTelemetry(
      INFORMATION_UNAVAILABLE_STRING,
      getMetadata().map(MetadataDto::getInstallationId).orElse(INFORMATION_UNAVAILABLE_STRING),
      getLicense()
    );

    assertThat(telemetryData).isEqualTo(expectedTelemetry);
  }

  @Test
  public void retrieveTelemetryData_missingLicenseKey() {
    try {
      // given
      removeLicense();

      // when
      final TelemetryDataDto telemetryData =
        embeddedOptimizeExtension.getApplicationContext().getBean(TelemetryDataService.class).getTelemetryData();

      // then
      final TelemetryDataDto expectedTelemetry = getExpectedTelemetry(
        elasticSearchIntegrationTestExtension.getEsVersion(),
        getMetadata().map(MetadataDto::getInstallationId).orElse(INFORMATION_UNAVAILABLE_STRING),
        INFORMATION_UNAVAILABLE_STRING
      );

      assertThat(telemetryData).isEqualTo(expectedTelemetry);
    } finally {
      initOptimizeLicense();
    }
  }

  private TelemetryDataDto getExpectedTelemetry(final String expectedDatabaseVersion,
                                                final String expectedInstallationId,
                                                final String expectedLicenseKey) {
    final DatabaseDto databaseDto = DatabaseDto.builder()
      .version(expectedDatabaseVersion)
      .vendor("elasticsearch")
      .build();

    final InternalsDto internalsDto = InternalsDto.builder()
      .engineInstallationIds(Lists.newArrayList()) // adjust once engine installation ID retrieval is implemented
      .database(databaseDto)
      .licenseKey(expectedLicenseKey)
      .build();

    final ProductDto productDto = ProductDto.builder()
      .internals(internalsDto)
      .build();

    return TelemetryDataDto.builder()
      .installation(expectedInstallationId)
      .product(productDto)
      .build();
  }

  @SneakyThrows
  private void removeMetadata() {
    final DeleteRequest request = new DeleteRequest(METADATA_INDEX_NAME)
      .id(MetadataIndex.ID)
      .setRefreshPolicy(IMMEDIATE);

    elasticSearchIntegrationTestExtension.getOptimizeElasticClient().delete(request, RequestOptions.DEFAULT);
  }

  private Optional<MetadataDto> getMetadata() {
    return embeddedOptimizeExtension.getApplicationContext().getBean(ElasticsearchMetadataService.class)
      .readMetadata(embeddedOptimizeExtension.getOptimizeElasticClient());
  }

  @SneakyThrows
  private void removeLicense() {
    embeddedOptimizeExtension.getApplicationContext().getBean(LicenseManager.class).setOptimizeLicense(null);
  }

  @SneakyThrows
  private void initOptimizeLicense() {
    embeddedOptimizeExtension.getApplicationContext().getBean(LicenseManager.class).init();
  }

  private String getLicense() {
    return embeddedOptimizeExtension.getApplicationContext().getBean(LicenseManager.class).getOptimizeLicense();
  }
}
