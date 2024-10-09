/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;

import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.reader.SettingsReader;
import io.camunda.optimize.service.db.schema.index.SettingsIndex;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.Objects;
import java.util.Optional;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class SettingsReaderOS implements SettingsReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(SettingsReaderOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  public SettingsReaderOS(
      final OptimizeOpenSearchClient osClient, final ConfigurationService configurationService) {
    this.osClient = osClient;
    this.configurationService = configurationService;
  }

  @Override
  public Optional<SettingsDto> getSettings() {
    log.debug("Fetching Optimize Settings");

    final GetRequest.Builder getReqBuilder =
        new GetRequest.Builder().index(SETTINGS_INDEX_NAME).id(SettingsIndex.ID);

    final String errorMessage = "There was an error while reading settings for OpenSearch";
    final GetResponse<SettingsDto> getResponse =
        osClient.get(getReqBuilder, SettingsDto.class, errorMessage);
    if (getResponse.found()) {
      final SettingsDto result = getResponse.source();
      if (Objects.nonNull(result)) {
        if (result.getSharingEnabled().isEmpty()) {
          result.setSharingEnabled(configurationService.getSharingEnabled());
        }
        log.debug("Finished Fetching Optimize Settings");
        return Optional.of(result);
      }
    } else {
      log.debug("No Settings found");
    }
    return Optional.empty();
  }
}
