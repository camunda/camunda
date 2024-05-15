/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.reader;

import static org.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;

import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SettingsDto;
import org.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import org.camunda.optimize.service.db.reader.SettingsReader;
import org.camunda.optimize.service.db.schema.index.SettingsIndex;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class SettingsReaderOS implements SettingsReader {

  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;

  @Override
  public Optional<SettingsDto> getSettings() {
    log.debug("Fetching Optimize Settings");

    final GetRequest.Builder getReqBuilder =
        new GetRequest.Builder().index(SETTINGS_INDEX_NAME).id(SettingsIndex.ID);

    final String errorMessage = "There was an error while reading settings for OpenSearch";
    final GetResponse<SettingsDto> getResponse =
        osClient.get(getReqBuilder, SettingsDto.class, errorMessage);
    if (getResponse.found()) {
      SettingsDto result = getResponse.source();
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
