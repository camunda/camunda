/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import static io.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;

import co.elastic.clients.elasticsearch.core.GetResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.reader.SettingsReader;
import io.camunda.optimize.service.db.schema.index.SettingsIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class SettingsReaderES implements SettingsReader {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SettingsReaderES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  public SettingsReaderES(
      final OptimizeElasticsearchClient esClient,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper,
      final ConfigurationService configurationService) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
  }

  @Override
  public Optional<SettingsDto> getSettings() {
    LOG.debug("Fetching Optimize Settings");

    SettingsDto result = null;
    try {
      final GetResponse<SettingsDto> getResponse =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, SETTINGS_INDEX_NAME).id(SettingsIndex.ID)),
              SettingsDto.class);
      if (getResponse.found()) {
        result = getResponse.source();
        if (result.getSharingEnabled().isEmpty()) {
          result.setSharingEnabled(configurationService.getSharingEnabled());
        }
      }
    } catch (final IOException e) {
      final String errorMessage = "There was an error while reading settings.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    return Optional.ofNullable(result);
  }
}
