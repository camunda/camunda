/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsResponseDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.reader.SettingsReader;
import org.camunda.optimize.service.db.schema.index.SettingsIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class SettingsReaderOS implements SettingsReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<SettingsResponseDto> getSettings() {
    log.debug("Fetching Optimize Settings");

    final GetRequest.Builder getReqBuilder = new GetRequest.Builder()
      .index(SETTINGS_INDEX_NAME).id(SettingsIndex.ID);

    final String errorMessage = "There was an error while reading settings for OpenSearch";
    final GetResponse<SettingsResponseDto> getResponse = osClient.get(getReqBuilder, SettingsResponseDto.class, errorMessage);
    if (getResponse.found()) {
      SettingsResponseDto result = getResponse.source();
      if (Objects.nonNull(result)) {
        if (result.getSharingEnabled().isEmpty()) {
          result.setSharingEnabled(configurationService.getSharingEnabled());
        }
        if (result.getMetadataTelemetryEnabled().isEmpty()) {
          result.setMetadataTelemetryEnabled(configurationService.getTelemetryConfiguration().isInitializeTelemetry());
        }
        return Optional.of(result);
      }
    }
    return Optional.empty();
  }

}
