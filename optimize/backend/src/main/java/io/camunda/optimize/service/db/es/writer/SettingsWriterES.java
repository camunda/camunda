/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.writer;

import static io.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static io.camunda.optimize.service.db.DatabaseConstants.SETTINGS_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.SettingsIndex.LAST_MODIFIED;
import static io.camunda.optimize.service.db.schema.index.SettingsIndex.SHARING_ENABLED;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.SettingsDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeUpdateRequestBuilderES;
import io.camunda.optimize.service.db.schema.index.SettingsIndex;
import io.camunda.optimize.service.db.writer.SettingsWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class SettingsWriterES implements SettingsWriter {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(SettingsWriterES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public SettingsWriterES(
      final OptimizeElasticsearchClient esClient,
      final @Qualifier("optimizeObjectMapper") ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public void upsertSettings(final SettingsDto settingsDto) {
    LOG.debug("Writing settings to ES");

    try {
      final UpdateRequest<SettingsDto, SettingsDto> request = createSettingsUpsert(settingsDto);
      esClient.update(request, SettingsDto.class);
    } catch (final IOException e) {
      final String errorMessage = "There were errors while writing settings.";
      LOG.error(errorMessage, e);
      throw new OptimizeRuntimeException(errorMessage, e);
    }
  }

  private UpdateRequest<SettingsDto, SettingsDto> createSettingsUpsert(
      final SettingsDto settingsDto) throws JsonProcessingException {
    final Set<String> fieldsToUpdate = new HashSet<>();
    if (settingsDto.getSharingEnabled().isPresent()) {
      fieldsToUpdate.add(SHARING_ENABLED);
    }
    if (!fieldsToUpdate.isEmpty()) {
      // This always gets updated
      fieldsToUpdate.add(LAST_MODIFIED);
    } else {
      throw new BadRequestException("No settings can be updated, as no values are present!");
    }

    final Script updateScript =
        ElasticsearchWriterUtil.createFieldUpdateScript(fieldsToUpdate, settingsDto, objectMapper);

    return new OptimizeUpdateRequestBuilderES<SettingsDto, SettingsDto>()
        .optimizeIndex(esClient, SETTINGS_INDEX_NAME)
        .id(SettingsIndex.ID)
        .upsert(settingsDto)
        .script(updateScript)
        .refresh(Refresh.True)
        .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
        .build();
  }
}
