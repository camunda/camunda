/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.telemetry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.MetadataDto;
import org.camunda.optimize.dto.optimize.query.telemetry.DatabaseDto;
import org.camunda.optimize.dto.optimize.query.telemetry.InternalsDto;
import org.camunda.optimize.dto.optimize.query.telemetry.ProductDto;
import org.camunda.optimize.dto.optimize.query.telemetry.TelemetryDataDto;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.ElasticsearchMetadataService;
import org.camunda.optimize.service.license.LicenseManager;
import org.elasticsearch.client.RequestOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.metadata.Version.VERSION;

@RequiredArgsConstructor
@Component
@Slf4j
public class TelemetryDataService {
  public static final String INFORMATION_UNAVAILABLE_STRING = "Unknown";

  private final ElasticsearchMetadataService elasticsearchMetadataService;
  private final LicenseManager licenseManager;
  private final OptimizeElasticsearchClient esClient;

  public TelemetryDataDto getTelemetryData() {
    final Optional<MetadataDto> metadata = elasticsearchMetadataService.readMetadata(esClient);

    return TelemetryDataDto.builder()
      .installation(metadata.map(MetadataDto::getInstallationId).orElse(INFORMATION_UNAVAILABLE_STRING))
      .product(getProductData())
      .build();
  }

  private ProductDto getProductData() {
    return ProductDto.builder()
      .version(VERSION)
      .internals(getInternalsData())
      .build();
  }

  private InternalsDto getInternalsData() {
    final List<String> engineInstallationIDs = new ArrayList<>(); // TODO once CAM-12294 is implemented
    final String licenseKey =
      Optional.ofNullable(licenseManager.getOptimizeLicense()).orElse(INFORMATION_UNAVAILABLE_STRING);

    return InternalsDto.builder()
      .engineInstallationIds(engineInstallationIDs)
      .database(getDatabaseData())
      .licenseKey(licenseKey)
      .build();
  }

  private DatabaseDto getDatabaseData() {
    String esVersion = INFORMATION_UNAVAILABLE_STRING;

    try {
      esVersion = esClient.getHighLevelClient()
        .info(RequestOptions.DEFAULT)
        .getVersion()
        .toString();
    } catch (IOException e) {
      log.info("Failed to retrieve Elasticsearch version for telemetry data.");
    }

    return DatabaseDto.builder()
      .version(esVersion)
      .build();
  }

}
