/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.reader.SettingsReader;
import io.camunda.optimize.service.db.schema.index.SettingsIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class SettingsReaderES implements SettingsReader {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public Optional<SettingsDto> getSettings() {
    log.debug("Fetching Optimize Settings");

    final GetRequest getRequest = new GetRequest(SETTINGS_INDEX_NAME).id(SettingsIndex.ID);

    SettingsDto result = null;
    try {
      final GetResponse getResponse = esClient.get(getRequest);
      if (getResponse.isExists()) {
        result = objectMapper.readValue(getResponse.getSourceAsString(), SettingsDto.class);
        if (result.getSharingEnabled().isEmpty()) {
          result.setSharingEnabled(configurationService.getSharingEnabled());
        }
      }
    } catch (IOException e) {
      final String errorMessage = "There was an error while reading settings.";
      log.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return Optional.ofNullable(result);
  }
}
